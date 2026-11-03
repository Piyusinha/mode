//! One live connection to this iPhone's CoreDevice services.
//!
//! A [`Session`] owns the tunnel, keeps one screen media stream open for its
//! whole life (iOS 27 drops touch and keyboard input from a client that has no
//! stream), and holds the HID clients. Ops are serialised through one lock.
//!
//! The stream setup follows idevice's own `tools/src/coredevice_stream.rs`
//! (Jackson Coxson, MIT).

use std::net::{IpAddr, SocketAddr};
use std::path::{Path, PathBuf};
use std::time::Duration;

use futures::TryStreamExt;
use idevice::cryptexd::{Cryptex1Assets, install_ddi};
use idevice::core_device::{
    AppListEntry, AppServiceClient, CallInfoBlob, DisplayServiceClient, GENERAL_PASTEBOARD, ImageFormat,
    PasteboardServiceClient, ScreenCaptureServiceClient, build_screen_audio_offer,
    build_screen_video_offer, build_start_audio_parameters, build_start_video_parameters,
    hid::{
        ButtonState, IndigoHidClient, TOUCHSCREEN_STATE_CONTACT, TOUCHSCREEN_STATE_RELEASE,
        UniversalHidServiceClient,
    },
};
use idevice::remote_pairing::{
    RemotePairingClient, RpPairingFile, RpPairingSocket, connect_tls_psk_tunnel_native,
};
use idevice::rsd::RsdHandshake;
use idevice::tcp::handle::{AdapterHandle, UdpSocketHandle};
use idevice::{IdeviceError, ReadWrite, RsdService};
use tokio::sync::Mutex;
use tokio::task::JoinHandle;

use crate::keys;

/// Device Hub advertises 140 as the controller's supported features; the
/// device's own, larger mask makes the negotiator produce an invalid config.
const CLIENT_SUPPORTED_FEATURES: u64 = 140;
/// backboardd needs a moment to re-match HID surfaces to a new stream.
const STREAM_SETTLE: Duration = Duration::from_millis(300);
/// Buttons shorter than ~50 ms are dropped.
const MIN_BUTTON_HOLD: Duration = Duration::from_millis(50);
/// Gap between touch samples while a finger moves (about 60 Hz).
const TOUCH_FRAME: Duration = Duration::from_millis(16);
/// Gap after each keystroke so the input stack sees discrete keys.
const KEY_GAP: Duration = Duration::from_millis(12);

#[derive(Debug)]
pub struct Error(pub String);

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for Error {}

impl From<IdeviceError> for Error {
    fn from(e: IdeviceError) -> Self {
        Error(format!("{e}"))
    }
}

fn err(context: &str, e: impl std::fmt::Display) -> Error {
    Error(format!("{context}: {e}"))
}

pub type Result<T> = std::result::Result<T, Error>;

/// A hardware button.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Button {
    Home,
    Lock,
    VolumeUp,
    VolumeDown,
}

impl Button {
    /// (usage page, usage, hold) as Device Hub sends them.
    fn usage(self) -> (u64, u64, Duration) {
        match self {
            Button::Home => (0x0C, 0x40, Duration::from_millis(80)),
            Button::Lock => (0x0C, 0x30, Duration::from_millis(200)),
            Button::VolumeUp => (0x0C, 0xE9, Duration::from_millis(80)),
            Button::VolumeDown => (0x0C, 0xEA, Duration::from_millis(80)),
        }
    }
}

/// How touch reports are timestamped. iOS uses the timestamps for gesture
/// velocity, so they must be monotonic; which clock lands taps most reliably
/// on-device is still being measured.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Clock {
    /// `mach_absolute_time()`: the phone's own clock when running on the phone.
    Mach,
    /// idevice's default, wall-clock nanoseconds.
    Wall,
}

struct Live {
    adapter: AdapterHandle,
    handshake: RsdHandshake,
    display: DisplayServiceClient<Box<dyn ReadWrite>>,
    drains: Vec<JoinHandle<()>>,
    /// The video receiver socket and our SSRC, to say goodbye (RTCP BYE).
    video: (std::sync::Arc<UdpSocketHandle>, u32),
    touch: UniversalHidServiceClient<Box<dyn ReadWrite>>,
    indigo: IndigoHidClient<Box<dyn ReadWrite>>,
    clock: Clock,
}

/// A connection to the phone. Every method blocks until the op is done.
pub struct Session {
    rt: tokio::runtime::Runtime,
    live: Mutex<Option<Live>>,
    mounted_disk_image: bool,
}

/// The service whose absence means the developer disk image is not mounted.
const DDI_SERVICE: &str = "com.apple.coredevice.displayservice";

impl Session {
    /// Connects to the phone's `remotepairingd` at `addr` with a remote-pairing
    /// file, builds the tunnel and opens the screen stream.
    ///
    /// On the phone itself `addr` is `127.0.0.1:<port>`, or the loopback VPN's
    /// peer `10.7.0.1:<port>`. From a Mac on the same network it is the phone's
    /// Wi-Fi address.
    ///
    /// When the developer disk image is not mounted (the phone rebooted) and
    /// `disk_image` names an unpacked iOS DDI `Restore` folder, it is mounted
    /// first; that asks Apple's signing server for a ticket, so it needs internet.
    pub fn open_rppairing(
        addr: SocketAddr,
        hostname: &str,
        pairing_file: &[u8],
        disk_image: Option<&Path>,
    ) -> Result<Session> {
        let rt = runtime()?;
        let disk_image = disk_image.map(Path::to_path_buf);
        let (adapter, handshake, mounted) = rt.block_on(connect_ready(
            async || tunnel_rppairing(addr, hostname, pairing_file).await,
            disk_image,
        ))?;
        Self::start(rt, adapter, handshake, mounted)
    }

    /// Connects over USB through usbmuxd (Mac-side testing only).
    #[cfg(feature = "usb")]
    pub fn open_usb(disk_image: Option<&Path>) -> Result<Session> {
        let rt = runtime()?;
        let disk_image = disk_image.map(Path::to_path_buf);
        let (adapter, handshake, mounted) = rt.block_on(connect_ready(async || tunnel_usb().await, disk_image))?;
        Self::start(rt, adapter, handshake, mounted)
    }

    fn start(
        rt: tokio::runtime::Runtime,
        mut adapter: AdapterHandle,
        mut handshake: RsdHandshake,
        mounted_disk_image: bool,
    ) -> Result<Session> {
        let live = rt.block_on(async {
            let (display, drains, video) = open_screen_stream(&mut adapter, &mut handshake).await?;
            tokio::time::sleep(STREAM_SETTLE).await;
            let touch = UniversalHidServiceClient::connect_rsd(&mut adapter, &mut handshake)
                .await
                .map_err(|e| err("touch service", e))?;
            let indigo = IndigoHidClient::connect_rsd(&mut adapter, &mut handshake)
                .await
                .map_err(|e| err("button/keyboard service", e))?;
            Ok::<_, Error>(Live { adapter, handshake, display, drains, video, touch, indigo, clock: Clock::Mach })
        })?;
        Ok(Session { rt, live: Mutex::new(Some(live)), mounted_disk_image })
    }

    /// True when this session had to mount the developer disk image itself.
    pub fn mounted_disk_image(&self) -> bool {
        self.mounted_disk_image
    }

    fn run<T>(&self, op: impl AsyncFnOnce(&mut Live) -> Result<T>) -> Result<T> {
        self.rt.block_on(async {
            let mut guard = self.live.lock().await;
            let live = guard.as_mut().ok_or_else(|| Error("session is closed".into()))?;
            op(live).await
        })
    }

    pub fn set_clock(&self, clock: Clock) -> Result<()> {
        self.run(async |live| {
            live.clock = clock;
            Ok(())
        })
    }

    /// One finger down at (x1, y1), held for `hold`, moved to (x2, y2) over
    /// `travel`, then lifted. Same point and zero travel is a tap; a long
    /// `hold` is a long press. Coordinates are 0..=65535 across the screen.
    pub fn touch(&self, from: (u16, u16), to: (u16, u16), hold: Duration, travel: Duration) -> Result<()> {
        self.run(async |live| {
            let clock = live.clock;
            let (x1, y1) = from;
            let (x2, y2) = to;
            live.touch.send_touchscreen(TOUCHSCREEN_STATE_CONTACT, x1, y1, stamp(clock)).await?;
            // A tap needs a short contact or it is not recognised.
            tokio::time::sleep(hold.max(Duration::from_millis(50))).await;
            let frames = (travel.as_millis() / TOUCH_FRAME.as_millis()) as u32;
            for i in 1..=frames {
                let t = i as f64 / frames as f64;
                let x = lerp(x1, x2, t);
                let y = lerp(y1, y2, t);
                live.touch.send_touchscreen(TOUCHSCREEN_STATE_CONTACT, x, y, stamp(clock)).await?;
                tokio::time::sleep(TOUCH_FRAME).await;
            }
            if frames > 0 {
                // Rest at the end so a scroll stops where the finger stops.
                live.touch.send_touchscreen(TOUCHSCREEN_STATE_CONTACT, x2, y2, stamp(clock)).await?;
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
            live.touch.send_touchscreen(TOUCHSCREEN_STATE_RELEASE, x2, y2, stamp(clock)).await?;
            Ok(())
        })
    }

    pub fn tap(&self, x: u16, y: u16) -> Result<()> {
        self.touch((x, y), (x, y), Duration::from_millis(50), Duration::ZERO)
    }

    pub fn button(&self, button: Button) -> Result<()> {
        let (page, usage, hold) = button.usage();
        self.run(async |live| {
            live.indigo.send_button(page, usage, ButtonState::Down).await?;
            tokio::time::sleep(hold.max(MIN_BUTTON_HOLD)).await;
            live.indigo.send_button(page, usage, ButtonState::Up).await?;
            Ok(())
        })
    }

    /// Press one key, with modifiers held around it (HID keyboard usages).
    pub fn key(&self, usage: u64, modifiers: &[u64]) -> Result<()> {
        let modifiers = modifiers.to_vec();
        self.run(async |live| press(&mut live.indigo, usage, &modifiers).await)
    }

    /// Type text on the hardware keyboard. ASCII only: the keyboard sends key
    /// codes, not characters. Use [`Session::paste_text`] for anything else.
    pub fn type_ascii(&self, text: &str) -> Result<()> {
        let strokes = text
            .chars()
            .map(|c| keys::ascii(c).ok_or_else(|| Error(format!("cannot type {c:?} on the keyboard; paste it"))))
            .collect::<Result<Vec<_>>>()?;
        self.run(async |live| {
            for (usage, shift) in strokes {
                let modifiers: &[u64] = if shift { &[keys::LEFT_SHIFT] } else { &[] };
                press(&mut live.indigo, usage, modifiers).await?;
            }
            Ok(())
        })
    }

    /// Replaces the general pasteboard with `text`.
    pub fn set_clipboard(&self, text: &str) -> Result<()> {
        let text = text.to_string();
        self.run(async |live| {
            let mut pb = PasteboardServiceClient::connect_rsd(&mut live.adapter, &mut live.handshake)
                .await
                .map_err(|e| err("pasteboard service", e))?;
            pb.set_text(&text, GENERAL_PASTEBOARD).await?;
            Ok(())
        })
    }

    /// Puts `text` on the clipboard and presses Cmd+V into the focused field.
    pub fn paste_text(&self, text: &str) -> Result<()> {
        self.set_clipboard(text)?;
        self.key(keys::V, &[keys::LEFT_GUI])
    }

    /// The screen as PNG bytes at full resolution. (iOS 27.2 rejects
    /// idevice's "jpeg" format, so shrinking happens on the caller's side.)
    /// The capture service drops its connection after a shot or two, so each
    /// shot reconnects.
    pub fn screenshot(&self) -> Result<Vec<u8>> {
        self.run(async |live| {
            let mut cap = ScreenCaptureServiceClient::connect_rsd(&mut live.adapter, &mut live.handshake)
                .await
                .map_err(|e| err("screen capture service", e))?;
            Ok(cap.take_screenshot(None, ImageFormat::Png).await?)
        })
    }

    /// Brings an app to the front by bundle id. `fresh` closes a running copy first, so the
    /// app starts on its home page instead of wherever it was left.
    pub fn launch(&self, bundle_id: &str, fresh: bool) -> Result<()> {
        let bundle_id = bundle_id.to_string();
        self.run(async |live| {
            let mut apps = AppServiceClient::connect_rsd(&mut live.adapter, &mut live.handshake)
                .await
                .map_err(|e| err("app service", e))?;
            apps.launch_application(bundle_id, &[], fresh, false, None, None, None).await?;
            Ok(())
        })
    }

    /// Installed apps users can open, as `[{"name", "bundle_id"}]` JSON.
    pub fn list_apps_json(&self) -> Result<String> {
        self.run(async |live| {
            let mut apps = AppServiceClient::connect_rsd(&mut live.adapter, &mut live.handshake)
                .await
                .map_err(|e| err("app service", e))?;
            // iOS 27 answers the streamed list; the plain call never returns there.
            let list: Vec<AppListEntry> =
                tokio::time::timeout(Duration::from_secs(20), apps.stream_apps(false, true, false, false, true).try_collect())
                    .await
                    .map_err(|_| Error("app list timed out".into()))??;
            let out: Vec<_> = list
                .into_iter()
                .filter(|a| !a.is_hidden && !a.is_app_clip)
                .map(|a| serde_json::json!({ "name": a.name, "bundle_id": a.bundle_identifier }))
                .collect();
            Ok(serde_json::Value::Array(out).to_string())
        })
    }

    /// Stops the screen stream and keeps the tunnel, pairing, and HID clients.
    /// Touch is not sent until [`Session::resume_stream`].
    pub fn pause_stream(&self) -> Result<()> {
        self.run(async |live| {
            let _ = live.display.stop_media_stream().await;
            live.drains.iter().for_each(|d| d.abort());
            live.drains.clear();
            Ok(())
        })
    }

    /// Starts the screen stream again on this same session.
    pub fn resume_stream(&self) -> Result<()> {
        self.run(async |live| {
            let (display, drains, video) = open_screen_stream(&mut live.adapter, &mut live.handshake).await?;
            live.display = display;
            live.drains = drains;
            live.video = video;
            tokio::time::sleep(STREAM_SETTLE).await;
            Ok(())
        })
    }

    /// Stops the stream and drops the tunnel. Later calls fail.
    pub fn close(&self) {
        self.rt.block_on(async {
            if let Some(mut live) = self.live.lock().await.take() {
                // Like Device Hub: release any held touch, tell the stream we
                // are leaving (RTCP BYE), stop it, then close the tunnel.
                let _ = live.touch.send_touchscreen(TOUCHSCREEN_STATE_RELEASE, 0, 0, stamp(live.clock)).await;
                let (socket, ssrc) = &live.video;
                let _ = socket.send_to(VIDEO_SENDER_PORT, rtcp_bye(*ssrc)).await;
                let _ = live.display.stop_media_stream().await;
                live.drains.iter().for_each(|d| d.abort());
                tokio::time::sleep(Duration::from_millis(200)).await;
                let _ = live.adapter.close().await;
            }
        });
    }
}

impl Drop for Session {
    fn drop(&mut self) {
        self.close();
    }
}

/// A fresh tunnel to the phone's `remotepairingd` with a remote-pairing file.
async fn tunnel_rppairing(addr: SocketAddr, hostname: &str, pairing_file: &[u8]) -> Result<(AdapterHandle, RsdHandshake)> {
    let mut pairing = RpPairingFile::from_bytes(pairing_file).map_err(|e| err("pairing file", e))?;
    let stream = tokio::time::timeout(Duration::from_secs(8), tokio::net::TcpStream::connect(addr))
        .await
        .map_err(|_| Error(format!("connect {addr}: timed out")))?
        .map_err(|e| err(&format!("connect {addr}"), e))?;
    let mut rpc = RemotePairingClient::new(RpPairingSocket::new(stream), hostname);
    // A stored pairing never needs a PIN; an empty one fails cleanly.
    rpc.connect(&mut pairing, async || String::new())
        .await
        .map_err(|e| err("pair-verify", e))?;

    let tunnel_port = rpc.create_tcp_listener().await.map_err(|e| err("tunnel listener", e))?;
    let mut tunnel_addr = addr;
    tunnel_addr.set_port(tunnel_port);
    let tunnel_stream = tokio::net::TcpStream::connect(tunnel_addr)
        .await
        .map_err(|e| err("tunnel connect", e))?;
    let tunnel = connect_tls_psk_tunnel_native(tunnel_stream, rpc.encryption_key())
        .await
        .map_err(|e| err("tunnel TLS", e))?;

    let client_ip: IpAddr = tunnel.info.client_address.parse().map_err(|e| err("client address", e))?;
    let server_ip: IpAddr = tunnel.info.server_address.parse().map_err(|e| err("server address", e))?;
    let mtu = tunnel.info.mtu as usize;
    let rsd_port = tunnel.info.server_rsd_port;
    let mut adapter = idevice::tcp::adapter::Adapter::new(Box::new(tunnel.into_inner()), client_ip, server_ip);
    adapter.set_mss(mtu.min(8192) - 60);
    let mut adapter = adapter.to_async_handle();
    let rsd = adapter.connect(rsd_port).await.map_err(|e| err("RSD connect", e))?;
    let handshake = RsdHandshake::new(rsd).await.map_err(|e| err("RSD handshake", e))?;
    Ok((adapter, handshake))
}

/// A fresh tunnel over USB through usbmuxd (Mac-side testing only).
#[cfg(feature = "usb")]
async fn tunnel_usb() -> Result<(AdapterHandle, RsdHandshake)> {
    use idevice::IdeviceService;
    use idevice::core_device_proxy::CoreDeviceProxy;
    use idevice::usbmuxd::{UsbmuxdAddr, UsbmuxdConnection};

    let mut mux = UsbmuxdConnection::default().await?;
    let device = mux
        .get_devices()
        .await?
        .into_iter()
        .next()
        .ok_or_else(|| Error("no iPhone on USB".into()))?;
    let provider = device.to_provider(UsbmuxdAddr::default(), "modecore");
    let proxy = CoreDeviceProxy::connect(&provider).await?;
    let rsd_port = proxy.tunnel_info().server_rsd_port;
    let mut adapter = proxy.create_software_tunnel()?.to_async_handle();
    let rsd = adapter.connect(rsd_port).await.map_err(|e| err("RSD connect", e))?;
    let handshake = RsdHandshake::new(rsd).await.map_err(|e| err("RSD handshake", e))?;
    Ok((adapter, handshake))
}

/// Connects with `connect`, and if the developer disk image is not mounted,
/// mounts it from `disk_image`. Mounting restarts the phone's service
/// directory and kills the tunnel it was done over (seen 2026-10-31), so it
/// then connects afresh until the services appear. Answers whether it mounted.
async fn connect_ready(
    connect: impl AsyncFn() -> Result<(AdapterHandle, RsdHandshake)>,
    disk_image: Option<PathBuf>,
) -> Result<(AdapterHandle, RsdHandshake, bool)> {
    let (mut adapter, mut handshake) = connect().await?;
    if handshake.services.contains_key(DDI_SERVICE) {
        return Ok((adapter, handshake, false));
    }
    let dir = disk_image.ok_or_else(|| {
        Error("the developer disk image is not mounted (did the phone restart?) and no image was given to mount".into())
    })?;
    let assets = Cryptex1Assets::load(&dir)
        .await
        .map_err(|e| err(&format!("disk image files in {}", dir.display()), e))?;
    install_ddi(&mut adapter, &mut handshake, &assets)
        .await
        .map_err(|e| err("mounting the developer disk image (needs internet for Apple's signature)", e))?;
    let _ = adapter.close().await;

    let mut last = String::from("services did not appear");
    for _ in 0..30 {
        tokio::time::sleep(Duration::from_millis(500)).await;
        match connect().await {
            Ok((adapter, handshake)) if handshake.services.contains_key(DDI_SERVICE) => {
                return Ok((adapter, handshake, true));
            }
            Ok((mut adapter, _)) => {
                let _ = adapter.close().await;
            }
            Err(e) => last = e.0,
        }
    }
    Err(Error(format!("mounted the developer disk image, but could not reconnect afterwards: {last}")))
}

fn runtime() -> Result<tokio::runtime::Runtime> {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .thread_name("modecore")
        .build()
        .map_err(|e| err("runtime", e))
}

async fn press(indigo: &mut IndigoHidClient<Box<dyn ReadWrite>>, usage: u64, modifiers: &[u64]) -> Result<()> {
    for &m in modifiers {
        indigo.send_keyboard(m, ButtonState::Down).await?;
    }
    indigo.send_keyboard(usage, ButtonState::Down).await?;
    indigo.send_keyboard(usage, ButtonState::Up).await?;
    for &m in modifiers.iter().rev() {
        indigo.send_keyboard(m, ButtonState::Up).await?;
    }
    tokio::time::sleep(KEY_GAP).await;
    Ok(())
}

/// Starts the audio+video screen-sharing stream the way Device Hub does, and
/// keeps reading (and discarding) its RTP so nothing piles up in memory.
async fn open_screen_stream(
    adapter: &mut AdapterHandle,
    handshake: &mut RsdHandshake,
) -> Result<(DisplayServiceClient<Box<dyn ReadWrite>>, Vec<JoinHandle<()>>, (std::sync::Arc<UdpSocketHandle>, u32))> {
    let mut display = DisplayServiceClient::connect_rsd(adapter, handshake)
        .await
        .map_err(|e| err("display service", e))?;
    // Clear streams nobody owns any more: a session whose app crashed, was
    // reinstalled or was killed never stopped its stream, and the phone keeps
    // streaming (and showing its "viewing and controlling" icon) until told.
    // stopmediastream stops them all; with none running it is a no-op.
    let _ = display.stop_media_stream().await;
    let audio_udp = adapter.bind_udp(0).await.map_err(|e| err("bind audio", e))?;
    let video_udp = adapter.bind_udp(0).await.map_err(|e| err("bind video", e))?;
    let receiver_ip = adapter.host_ip().to_string();
    let sender_ip = adapter.peer_ip().to_string();

    // Describes this end of the call; the values mirror an offer the device accepted.
    let call_info = CallInfoBlob {
        call_id: 0,
        client_version: 1,
        device_type: "Mac17,7".into(),
        framework_version: "2205.3.1".into(),
        os_version: "25F71".into(),
        device_name: None,
        audio_device_uid: None,
    };
    let session_id = uuid::Uuid::new_v4();

    let audio_call = uuid::Uuid::new_v4().to_string().to_uppercase();
    let audio_offer = build_screen_audio_offer(&audio_call, &call_info).map_err(|e| err("audio offer", e))?;
    display
        .start_media_stream(build_start_audio_parameters(
            &receiver_ip,
            audio_udp.local_port(),
            &sender_ip,
            50000,
            audio_offer,
            CLIENT_SUPPORTED_FEATURES,
            session_id,
        ))
        .await
        .map_err(|e| err("start audio stream", e))?;

    let video_call = uuid::Uuid::new_v4().to_string().to_uppercase();
    let ssrc = uuid::Uuid::new_v4().as_u128() as u32;
    let video_offer =
        build_screen_video_offer(&video_call, &call_info, ssrc).map_err(|e| err("video offer", e))?;
    display
        .start_media_stream(build_start_video_parameters(
            &receiver_ip,
            video_udp.local_port(),
            &sender_ip,
            VIDEO_SENDER_PORT,
            video_offer,
            CLIENT_SUPPORTED_FEATURES,
            1,
            session_id,
        ))
        .await
        .map_err(|e| err("start video stream (the phone refuses while an app uses the camera or mic)", e))?;

    let video_udp = std::sync::Arc::new(video_udp);
    let drains = vec![tokio::spawn(drain(audio_udp)), tokio::spawn(receive_video(video_udp.clone(), ssrc))];
    Ok((display, drains, (video_udp, ssrc)))
}

/// The device's RTP source port for video, as declared in our start parameters.
const VIDEO_SENDER_PORT: u16 = 50001;
/// How often we tell the phone the viewer is alive (Device Hub sends feedback every 50 ms).
const LIVENESS_EVERY: Duration = Duration::from_millis(200);

async fn drain(socket: UdpSocketHandle) {
    while socket.recv().await.is_ok() {}
}

/// Reads (and discards) the video RTP, and like Device Hub keeps sending RTCP
/// receiver reports so the phone knows a viewer is alive. Without them the
/// phone cannot tell when the viewer is gone, and its session (and the
/// "viewing and controlling" icon) can outlive us.
async fn receive_video(socket: std::sync::Arc<UdpSocketHandle>, our_ssrc: u32) {
    use idevice::core_device::{ReportBlock, RtpPacket, build_liveness, is_rtcp};
    let mut media_ssrc: Option<u32> = None;
    let mut highest_seq: u32 = 0;
    let mut cycles: u32 = 0;
    let mut last_seq: Option<u16> = None;
    let mut ticker = tokio::time::interval(LIVENESS_EVERY);
    ticker.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
    loop {
        tokio::select! {
            datagram = socket.recv() => {
                let Ok(datagram) = datagram else { return };
                if is_rtcp(&datagram.data) { continue; }
                if let Some(packet) = RtpPacket::parse(&datagram.data) {
                    media_ssrc = Some(packet.ssrc);
                    if let Some(last) = last_seq {
                        if packet.sequence_number < last && last - packet.sequence_number > 0x8000 {
                            cycles = cycles.wrapping_add(1);
                        }
                    }
                    last_seq = Some(packet.sequence_number);
                    highest_seq = (cycles << 16) | packet.sequence_number as u32;
                }
            }
            _ = ticker.tick() => {
                let blocks: Vec<ReportBlock> = media_ssrc
                    .map(|source| vec![ReportBlock { source_ssrc: source, highest_seq, ..Default::default() }])
                    .unwrap_or_default();
                if socket.send_to(VIDEO_SENDER_PORT, build_liveness(our_ssrc, "mode", &blocks)).await.is_err() {
                    return;
                }
            }
        }
    }
}

/// RTCP BYE (RFC 3550 §6.6): this receiver is leaving.
fn rtcp_bye(our_ssrc: u32) -> Vec<u8> {
    let mut packet = vec![0x81, 203, 0x00, 0x01];
    packet.extend_from_slice(&our_ssrc.to_be_bytes());
    packet
}

fn lerp(a: u16, b: u16, t: f64) -> u16 {
    (a as f64 + (b as f64 - a as f64) * t).round().clamp(0.0, 65535.0) as u16
}

fn stamp(clock: Clock) -> Option<u64> {
    match clock {
        Clock::Wall => None,
        Clock::Mach => Some(mach_now()),
    }
}

#[cfg(target_vendor = "apple")]
fn mach_now() -> u64 {
    unsafe extern "C" {
        fn mach_absolute_time() -> u64;
    }
    // SAFETY: a plain libSystem call with no arguments.
    unsafe { mach_absolute_time() }
}

#[cfg(not(target_vendor = "apple"))]
fn mach_now() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bye_is_a_well_formed_rtcp_packet() {
        let bye = rtcp_bye(0x01020304);
        assert_eq!(bye, vec![0x81, 203, 0, 1, 1, 2, 3, 4]);
        assert_eq!(bye.len() % 4, 0);
    }

    #[test]
    fn lerp_hits_both_ends() {
        assert_eq!(lerp(100, 200, 0.0), 100);
        assert_eq!(lerp(100, 200, 1.0), 200);
        assert_eq!(lerp(200, 100, 0.5), 150);
    }
}
