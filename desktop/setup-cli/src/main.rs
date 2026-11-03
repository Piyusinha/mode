//! `mode-setup`: drives Mode Setup's engine step by step, to test it against a real iPhone before
//! (and alongside) the window app.
//!
//!   mode-setup phones                       iPhones on this computer
//!   mode-setup trust                        Trust this computer, then Developer Mode
//!   mode-setup apps                         Mode, SideStore and LocalDevVPN on the phone
//!   mode-setup helpers                      whether the sign-in helpers answer
//!   mode-setup latest                       the newest Mode release
//!   mode-setup install <ipa> [--sidestore]  sign in, sign, install, give Mode phone control
//!   mode-setup pair                         a new phone-control file for Mode (and SideStore)
//!
//! Sign-in asks for the Apple Account, password and code in this terminal; nothing is echoed.
//! Set MODE_SETUP_UDID to pick a phone when more than one is connected.

use std::{io::Write, path::PathBuf, sync::Arc, time::Duration};

use mode_setup_core::{
    account::{self, CodeAnswer, CodeRequest, HELPERS, TeamKind},
    apps, device::{self, Readiness}, install::{Signer, Stage}, pairing, release, Result, SetupError,
};

#[tokio::main]
async fn main() {
    let _ = rustls::crypto::ring::default_provider().install_default();
    tracing_subscriber::fmt().with_env_filter(std::env::var("RUST_LOG").unwrap_or_else(|_| "warn".into())).with_writer(std::io::stderr).init();
    mode_setup_core::init();
    let args: Vec<String> = std::env::args().skip(1).collect();
    let result = match args.first().map(String::as_str) {
        Some("phones") => phones().await,
        Some("trust") => trust().await,
        Some("apps") => list_apps().await,
        Some("helpers") => helpers().await,
        Some("latest") => latest().await,
        Some("install") => install(&args[1..]).await,
        Some("pair") => pair().await,
        _ => {
            eprintln!("usage: mode-setup phones|trust|apps|helpers|latest|install <ipa> [--sidestore]|pair");
            std::process::exit(2);
        }
    };
    if let Err(e) = result {
        eprintln!("✗ {e}");
        std::process::exit(1);
    }
}

async fn phone() -> Result<device::Phone> {
    let phones = device::phones().await?;
    if let Ok(udid) = std::env::var("MODE_SETUP_UDID") {
        return phones.into_iter().find(|p| p.udid == udid).ok_or(SetupError::NoPhone);
    }
    match phones.len() {
        0 => Err(SetupError::NoPhone),
        1 => Ok(phones.into_iter().next().unwrap()),
        _ => {
            for p in &phones {
                eprintln!("  {}  {} ({})", p.udid, p.name, p.model);
            }
            Err(SetupError::Phone("More than one iPhone is connected; set MODE_SETUP_UDID to one of them.".into()))
        }
    }
}

async fn phones() -> Result<()> {
    let phones = device::phones().await?;
    if phones.is_empty() {
        println!("No iPhone connected.");
    }
    for p in phones {
        println!("{}  {}  {}  iOS {}  {}", p.udid, p.name, p.model, p.ios, if p.usb { "USB" } else { "Wi-Fi" });
    }
    Ok(())
}

/// Asks for Trust and waits; then shows the Developer Mode switch and waits for it.
async fn trust() -> Result<()> {
    let p = phone().await?;
    let mut last = None;
    let mut shown_switch = false;
    loop {
        let r = device::trust(&p.udid).await?;
        if last != Some(r) {
            println!("{}", match r {
                Readiness::NeedsTrust => "Tap Trust on your iPhone, then enter its passcode…",
                Readiness::Locked => "Unlock your iPhone…",
                Readiness::Denied => "Your iPhone said Don’t Trust. Unplug it and plug it back in.",
                Readiness::NeedsDeveloperMode => "Turn on Developer Mode: Settings → Privacy & Security → Developer Mode, then Restart.",
                Readiness::Ready => "✓ Trusted, Developer Mode on.",
            });
            last = Some(r);
        }
        match r {
            Readiness::Ready => return Ok(()),
            Readiness::Denied => return Err(SetupError::TrustDenied),
            Readiness::NeedsDeveloperMode if !shown_switch => {
                device::show_developer_mode_switch(&p.udid).await?;
                shown_switch = true;
            }
            _ => {}
        }
        tokio::time::sleep(Duration::from_secs(2)).await;
    }
}

async fn list_apps() -> Result<()> {
    let p = phone().await?;
    let a = apps::installed(&p.udid).await?;
    let modes: Vec<String> = apps::modes(&a).map(|m| format!("{} {}", m.bundle_id, m.version)).collect();
    println!("Mode:        {}", if modes.is_empty() { "not installed".into() } else { modes.join(", ") });
    println!("SideStore:   {}", apps::sidestore(&a).map(|m| format!("{} {}", m.bundle_id, m.version)).unwrap_or("not installed".into()));
    println!("LocalDevVPN: {}", apps::local_dev_vpn(&a).map(|m| m.version.clone()).unwrap_or("not installed".into()));
    Ok(())
}

async fn helpers() -> Result<()> {
    for h in HELPERS {
        match account::helper_answers(h.url).await {
            Ok(t) => println!("✓ {} ({}) · {} ms", h.name, h.url, t.as_millis()),
            Err(e) => println!("✗ {} ({}) · {e}", h.name, h.url),
        }
    }
    Ok(())
}

async fn latest() -> Result<()> {
    let r = release::latest_mode().await?;
    println!("Mode {} · {} · sha256 {}", r.version, r.ipa_url, r.sha256.as_deref().unwrap_or("not published"));
    Ok(())
}

fn ask(prompt: &str) -> String {
    eprint!("{prompt}");
    let _ = std::io::stderr().flush();
    let mut s = String::new();
    let _ = std::io::stdin().read_line(&mut s);
    s.trim().to_string()
}

async fn code(req: CodeRequest) -> CodeAnswer {
    tokio::task::spawn_blocking(move || {
        match &req.texted_to {
            Some(n) => eprintln!("Apple texted a code to {n}."),
            None => eprintln!("Apple sent a code to your iPhone and other devices."),
        }
        if req.last_failed {
            eprintln!("That didn’t work; choose another way.");
        }
        for (id, n) in &req.numbers {
            eprintln!("  t{id}: text it to {n}");
        }
        let a = ask("Code (or r to resend, d for devices, t<id> to text): ");
        match a.as_str() {
            "r" => CodeAnswer::Resend,
            "d" => CodeAnswer::SendToDevices,
            "" => CodeAnswer::Cancel,
            t if t.starts_with('t') => t[1..].parse().map(CodeAnswer::Text).unwrap_or(CodeAnswer::Cancel),
            c => CodeAnswer::Code(c.to_string()),
        }
    })
    .await
    .unwrap_or(CodeAnswer::Cancel)
}

async fn signer() -> Result<(Signer, account::Team)> {
    let email = std::env::var("MODE_SETUP_EMAIL").unwrap_or_else(|_| ask("Apple Account: "));
    let password = match account::remembered_password(&email) {
        Some(p) => p,
        None => rpassword::prompt_password("Password (goes only to Apple): ").map_err(|e| SetupError::SignIn(e.to_string()))?,
    };
    let helper = std::env::var("MODE_SETUP_HELPER").unwrap_or_else(|_| HELPERS[0].url.to_string());
    let mut session = account::sign_in(&email, &password, &helper, code).await?;
    if ask("Remember the password on this computer? [y/N] ").eq_ignore_ascii_case("y") {
        account::remember_password(&email, &password)?;
    }
    let teams = account::teams(&mut session).await?;
    let team = match teams.len() {
        0 => return Err(SetupError::SignIn("this account has no team that can sign apps".into())),
        1 => teams[0].clone(),
        _ => {
            for (i, t) in teams.iter().enumerate() {
                eprintln!("  {}: {} ({}) · {:?}", i + 1, t.name, t.id, t.kind);
            }
            let n: usize = ask("Team [1]: ").parse().unwrap_or(1);
            teams.get(n.saturating_sub(1)).cloned().unwrap_or_else(|| teams[0].clone())
        }
    };
    println!("Signing with {} ({:?}).", team.name, team.kind);
    Ok((Signer::new(session), team))
}

fn progress(label: &'static str) -> mode_setup_core::install::Progress {
    Arc::new(move |stage, f| {
        let s = match stage { Stage::Signing => "signing", Stage::Installing => "installing" };
        eprint!("\r{label}: {s} {:>3.0}%   ", f * 100.0);
        let _ = std::io::stderr().flush();
    })
}

async fn install(args: &[String]) -> Result<()> {
    let ipa = PathBuf::from(args.first().ok_or_else(|| SetupError::Install("give the path to Mode’s .ipa".into()))?);
    let with_sidestore = args.iter().any(|a| a == "--sidestore");
    let p = phone().await?;
    if device::trust(&p.udid).await? != Readiness::Ready {
        return Err(SetupError::Phone("Run `mode-setup trust` first.".into()));
    }
    let (mut signer, team) = signer().await?;

    let mode = signer.install(&p.udid, &p.name, &team, &ipa, progress("Mode")).await?;
    eprintln!();
    println!("✓ {} {} installed as {} · signed until {}", mode.name, mode.version, mode.bundle_id, mode.expires.as_deref().unwrap_or("?"));

    if with_sidestore && team.kind != TeamKind::Paid {
        let dest = std::env::temp_dir().join("ModeSetup").join("SideStore.ipa");
        release::download(release::SIDESTORE_IPA, &dest, None, |f| eprint!("\rSideStore: downloading {:>3.0}%   ", f * 100.0)).await?;
        let ss = signer.install(&p.udid, &p.name, &team, &dest, progress("SideStore")).await?;
        eprintln!();
        println!("✓ SideStore {} installed · signed until {}", ss.version, ss.expires.as_deref().unwrap_or("?"));
    }
    pair().await
}

async fn pair() -> Result<()> {
    let p = phone().await?;
    let control = pairing::make(&p.udid).await?;
    let bundles = pairing::give_to_mode(&p.udid, &control).await?;
    println!("✓ Phone control given to Mode ({}).", bundles.join(", "));
    let a = apps::installed(&p.udid).await?;
    if apps::sidestore(&a).is_some() {
        pairing::give_to_sidestore(&p.udid, &control).await?;
        println!("✓ And to SideStore.");
    }
    if apps::local_dev_vpn(&a).is_none() {
        println!("Next: get LocalDevVPN from the App Store and connect it, then open Mode.");
    }
    Ok(())
}
