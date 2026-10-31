//! Making a remote-pairing file over USB, once, from a Mac. The phone then
//! trusts whoever holds the file, which is how Mode reaches its own phone.
//!
//! Follows idevice's `tools/src/rppairing.rs` (Jackson Coxson, MIT).

use idevice::core_device_proxy::CoreDeviceProxy;
use idevice::remote_pairing::{RemotePairingClient, RpPairingFile};
use idevice::rsd::RsdHandshake;
use idevice::usbmuxd::{UsbmuxdAddr, UsbmuxdConnection};
use idevice::{IdeviceService, RemoteXpcClient};

use crate::session::{Error, Result};

/// Pairs with the first iPhone on USB as `hostname` and returns the pairing
/// file's bytes. The phone may ask to Trust this computer.
pub fn pair_over_usb(hostname: &str) -> Result<Vec<u8>> {
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|e| Error(format!("runtime: {e}")))?;
    rt.block_on(async {
        let mut mux = UsbmuxdConnection::default().await?;
        let device = mux
            .get_devices()
            .await?
            .into_iter()
            .next()
            .ok_or_else(|| Error("no iPhone on USB".into()))?;
        let provider = device.to_provider(UsbmuxdAddr::default(), "modecore-pair");
        let proxy = CoreDeviceProxy::connect(&provider).await?;
        let rsd_port = proxy.tunnel_info().server_rsd_port;
        let mut adapter = proxy.create_software_tunnel()?.to_async_handle();
        let rsd = adapter.connect(rsd_port).await.map_err(|e| Error(format!("RSD connect: {e}")))?;
        let handshake = RsdHandshake::new(rsd).await?;
        let service = handshake
            .services
            .get("com.apple.internal.dt.coredevice.untrusted.tunnelservice")
            .ok_or_else(|| Error("the phone has no untrusted tunnel service; is Developer Mode on?".into()))?;
        let stream = adapter
            .connect(service.port)
            .await
            .map_err(|e| Error(format!("tunnel service connect: {e}")))?;
        let mut xpc = RemoteXpcClient::new(stream).await?;
        xpc.do_handshake().await?;
        let _ = xpc.recv_root().await;

        let mut file = RpPairingFile::generate(hostname);
        let mut rpc = RemotePairingClient::new(xpc, hostname);
        rpc.connect(&mut file, async || "000000".to_string())
            .await
            .map_err(|e| Error(format!("pairing (tap Trust on the phone?): {e}")))?;
        Ok(file.to_bytes())
    })
}

/// Removes the developer disk image from the first iPhone on USB, for testing
/// that Mode can mount it again. Answers what was removed, if anything.
pub fn remove_disk_image_over_usb() -> Result<Option<String>> {
    use idevice::RsdService;
    use idevice::cryptexd::{CryptexdClient, installed_ddi};

    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|e| Error(format!("runtime: {e}")))?;
    rt.block_on(async {
        let mut mux = UsbmuxdConnection::default().await?;
        let device = mux
            .get_devices()
            .await?
            .into_iter()
            .next()
            .ok_or_else(|| Error("no iPhone on USB".into()))?;
        let provider = device.to_provider(UsbmuxdAddr::default(), "modecore-ddi");
        let proxy = CoreDeviceProxy::connect(&provider).await?;
        let rsd_port = proxy.tunnel_info().server_rsd_port;
        let mut adapter = proxy.create_software_tunnel()?.to_async_handle();
        let rsd = adapter.connect(rsd_port).await.map_err(|e| Error(format!("RSD connect: {e}")))?;
        let mut handshake = RsdHandshake::new(rsd).await?;
        let Some(installed) = installed_ddi(&mut adapter, &mut handshake).await? else {
            return Ok(None);
        };
        let client: CryptexdClient<Box<dyn idevice::ReadWrite>> =
            CryptexdClient::connect_rsd(&mut adapter, &mut handshake).await?;
        client.uninstall(&installed.identifier, Some(&installed.version)).await?;
        Ok(Some(format!("{} {}", installed.identifier, installed.version)))
    })
}
