//! Drive the phone from a Mac over USB with modecore's Session, the same code
//! Mode runs on the phone. For testing the native layer before it is in the app.
//!
//!     cargo run --example mc --features usb -- <command>...
//!
//! Commands run in order in one session, so the stream opens once:
//!     home | shot <file> | tap <x> <y> | drag <x1> <y1> <x2> <y2> <ms>
//!     type <ascii> | paste <text> | key <name> | launch <bundle id> | apps
//!     clock mach|wall | sleep <ms>
//! Or, alone: `pair <hostname> <file>` makes a remote-pairing file over USB;
//! `ddi-remove` unmounts the developer disk image (to test remounting).
//! Coordinates are 0..65535.

use std::time::{Duration, Instant};

use modecore::{Button, Clock, Session};

fn main() {
    let mut args = std::env::args().skip(1).peekable();
    // `ddi-remove` unmounts the developer disk image, to test remounting.
    if args.peek().map(String::as_str) == Some("ddi-remove") {
        match modecore::remove_disk_image_over_usb() {
            Ok(Some(what)) => println!("removed {what}"),
            Ok(None) => println!("no developer disk image was installed"),
            Err(e) => fail(&format!("ddi-remove: {e}")),
        }
        return;
    }
    // `pair <hostname> <file>` makes a remote-pairing file; it needs no session.
    if args.peek().map(String::as_str) == Some("pair") {
        args.next();
        let hostname = args.next().unwrap_or_else(|| fail("pair: missing hostname"));
        let path = args.next().unwrap_or_else(|| fail("pair: missing output file"));
        let bytes = modecore::pair_over_usb(&hostname).unwrap_or_else(|e| fail(&format!("pair: {e}")));
        std::fs::write(&path, bytes).unwrap_or_else(|e| fail(&format!("write {path}: {e}")));
        println!("paired as '{hostname}', saved {path}");
        return;
    }
    let t0 = Instant::now();
    // MC_DDI=<iOS DDI Restore folder> mounts the disk image if the phone has none.
    let disk_image = std::env::var("MC_DDI").ok().map(std::path::PathBuf::from);
    // MC_CONNECT=host:port MC_PAIRING=file uses the remote-pairing path the app
    // uses on the phone; otherwise the USB tunnel.
    let session = match (std::env::var("MC_CONNECT"), std::env::var("MC_PAIRING")) {
        (Ok(addr), Ok(file)) => {
            let addr = addr.parse().unwrap_or_else(|_| fail("MC_CONNECT must be ip:port"));
            let pairing = std::fs::read(&file).unwrap_or_else(|e| fail(&format!("read {file}: {e}")));
            let hostname = std::env::var("MC_HOSTNAME").unwrap_or_else(|_| "Mode".into());
            Session::open_rppairing(addr, &hostname, &pairing, disk_image.as_deref())
        }
        _ => Session::open_usb(disk_image.as_deref()),
    }
    .unwrap_or_else(|e| fail(&format!("open: {e}")));
    if session.mounted_disk_image() {
        println!("[{:>6.2}s] mounted the developer disk image", t0.elapsed().as_secs_f64());
    }
    println!("[{:>6.2}s] session open", t0.elapsed().as_secs_f64());

    while let Some(cmd) = args.next() {
        let mut next = || args.next().unwrap_or_else(|| fail(&format!("{cmd}: missing argument")));
        let started = Instant::now();
        let result = match cmd.as_str() {
            "home" => session.button(Button::Home),
            "shot" => {
                let path = next();
                session.screenshot().and_then(|bytes| {
                    std::fs::write(&path, &bytes).map_err(|e| modecore::Error(e.to_string()))
                })
            }
            "tap" => {
                let (x, y) = (num(&next()), num(&next()));
                session.tap(x, y)
            }
            "drag" => {
                let (x1, y1, x2, y2) = (num(&next()), num(&next()), num(&next()), num(&next()));
                let ms: u64 = next().parse().unwrap_or(300);
                session.touch((x1, y1), (x2, y2), Duration::from_millis(50), Duration::from_millis(ms))
            }
            "type" => session.type_ascii(&next()),
            "paste" => session.paste_text(&next()),
            "key" => {
                let name = next();
                let usage = modecore::keys::named(&name).unwrap_or_else(|| fail(&format!("no key {name}")));
                session.key(usage, &[])
            }
            "launch" => session.launch(&next(), false),
            "apps" => session.list_apps_json().map(|json| println!("{json}")),
            "clock" => session.set_clock(if next() == "wall" { Clock::Wall } else { Clock::Mach }),
            "sleep" => {
                std::thread::sleep(Duration::from_millis(next().parse().unwrap_or(500)));
                Ok(())
            }
            other => fail(&format!("unknown command {other}")),
        };
        match result {
            Ok(()) => println!("[{:>6.2}s] {cmd} ok ({} ms)", t0.elapsed().as_secs_f64(), started.elapsed().as_millis()),
            Err(e) => println!("[{:>6.2}s] {cmd} FAILED: {e}", t0.elapsed().as_secs_f64()),
        }
    }
    session.close();
}

fn num(s: &str) -> u16 {
    s.parse().unwrap_or_else(|_| fail(&format!("not 0..65535: {s}")))
}

fn fail(msg: &str) -> ! {
    eprintln!("{msg}");
    std::process::exit(1)
}
