//! Mode Setup, the window app: the screens on the design canvas ("Mode Setup" section) over
//! mode-setup-core. Each command is one engine step; the window (ui/) decides the order, as the
//! flow board shows. Apple's verification code is asked through an event and answered with
//! `submit_code`. Nothing secret is logged or sent anywhere but Apple.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{Arc, Mutex as StdMutex},
};

use mode_setup_core::{
    account::{self, CodeAnswer, CodeRequest, Team, TeamKind, HELPERS},
    apps,
    device::{self, Phone, Readiness},
    install::{Installed, Signer, Stage, VpnStatus},
    pairing, release,
};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::{oneshot, Mutex};

type CodeSlot = Arc<StdMutex<Option<oneshot::Sender<CodeAnswer>>>>;

#[derive(Default)]
struct App {
    signer: Mutex<Option<Signer>>,
    teams: StdMutex<Vec<Team>>,
    code: CodeSlot,
    email: StdMutex<Option<String>>,
    auth_epoch: Arc<std::sync::atomic::AtomicU64>,
    signing_in: Mutex<()>,
    pairing: Mutex<()>,
}

type Res<T> = Result<T, String>;

fn err(e: impl std::fmt::Display) -> String {
    e.to_string()
}

// ---- iPhone -----------------------------------------------------------------------------------

/// Is this computer ready (iPhone driver, internet)? Shown before setup starts.
#[tauri::command]
async fn check_computer() -> mode_setup_core::system::ComputerCheck {
    mode_setup_core::system::check_computer().await
}

#[tauri::command]
async fn phones() -> Res<Vec<Phone>> {
    device::phones().await.map_err(err)
}

#[tauri::command]
async fn trust(udid: String) -> Res<Readiness> {
    device::trust(&udid).await.map_err(err)
}

#[tauri::command]
async fn show_developer_mode(udid: String) -> Res<()> {
    device::show_developer_mode_switch(&udid).await.map_err(err)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OnPhone {
    modes: Vec<apps::AppOnPhone>,
    sidestore: Option<apps::AppOnPhone>,
    local_dev_vpn: Option<apps::AppOnPhone>,
    /// Apps using a free account's slots (signed with a developer certificate).
    sideloaded: Vec<apps::AppOnPhone>,
}

#[tauri::command]
async fn apps_on_phone(udid: String) -> Res<OnPhone> {
    let all = apps::installed(&udid).await.map_err(err)?;
    Ok(OnPhone {
        modes: apps::modes(&all).cloned().collect(),
        sidestore: apps::sidestore(&all).cloned(),
        local_dev_vpn: apps::local_dev_vpn(&all).cloned(),
        sideloaded: all.iter().filter(|a| a.sideloaded).cloned().collect(),
    })
}

// ---- Apple account ----------------------------------------------------------------------------

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct HelperStatus {
    name: String,
    url: String,
    ms: Option<u128>,
    error: Option<String>,
}

#[tauri::command]
async fn helpers() -> Vec<HelperStatus> {
    let mut out = Vec::new();
    for h in HELPERS {
        let r = account::helper_answers(h.url).await;
        out.push(HelperStatus { name: h.name.into(), url: h.url.into(), ms: r.as_ref().ok().map(|d| d.as_millis()), error: r.err().map(err) });
    }
    out
}

/// Signs in; Apple's code is asked with the "code-needed" event. Answers the teams, paid first.
#[tauri::command]
async fn sign_in(app: AppHandle, state: State<'_, App>, email: String, password: Option<String>, remember: bool, helper: String) -> Res<Vec<Team>> {
    use std::sync::atomic::Ordering;
    let _login = state.signing_in.try_lock().map_err(|_| "Sign-in is already in progress.")?;
    let epoch = state.auth_epoch.fetch_add(1, Ordering::SeqCst) + 1;
    let password = match password.filter(|p| !p.is_empty()) {
        Some(p) => p,
        None => account::remembered_password(&email).ok_or("Enter your password.")?,
    };
    let slot = state.code.clone();
    let handle = app.clone();
    let epochs = state.auth_epoch.clone();
    let ask = move |req: CodeRequest| {
        let cancelled = epochs.load(Ordering::SeqCst) != epoch;
        let (tx, rx) = oneshot::channel();
        *slot.lock().unwrap() = Some(tx);
        if !cancelled { let _ = handle.emit("code-needed", req); }
        async move { if cancelled { CodeAnswer::Cancel } else { rx.await.unwrap_or(CodeAnswer::Cancel) } }
    };
    let mut session = account::sign_in(&email, &password, &helper, ask).await.map_err(err)?;
    if state.auth_epoch.load(Ordering::SeqCst) != epoch { return Err("Sign-in cancelled.".into()); }
    let teams = account::teams(&mut session).await.map_err(err)?;
    if state.auth_epoch.load(Ordering::SeqCst) != epoch { return Err("Sign-in cancelled.".into()); }
    if remember {
        account::remember_password(&email, &password).map_err(err)?;
    } else {
        account::forget_password(&email);
    }
    *state.teams.lock().unwrap() = teams.clone();
    *state.signer.lock().await = Some(Signer::new(session));
    *state.email.lock().unwrap() = Some(email.clone());
    save_prefs(&app, |p| { p.email = Some(email.clone()); p.remembered_password = remember; });
    Ok(teams)
}

#[tauri::command]
fn submit_code(state: State<'_, App>, answer: CodeAnswer) {
    if let Some(tx) = state.code.lock().unwrap().take() {
        let _ = tx.send(answer);
    }
}

/// No Keychain access: the UI can reuse this process's authenticated session.
#[tauri::command]
fn active_account(state: State<'_, App>) -> Option<serde_json::Value> {
    let email = state.email.lock().unwrap().clone()?;
    Some(serde_json::json!({ "email": email, "teams": state.teams.lock().unwrap().clone() }))
}

#[tauri::command]
fn cancel_sign_in(state: State<'_, App>) {
    state.auth_epoch.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
    if let Some(tx) = state.code.lock().unwrap().take() { let _ = tx.send(CodeAnswer::Cancel); }
}

#[tauri::command]
async fn sign_out(app: AppHandle, state: State<'_, App>, email: String) -> Res<()> {
    cancel_sign_in(state.clone());
    *state.signer.lock().await = None;
    *state.email.lock().unwrap() = None;
    state.teams.lock().unwrap().clear();
    account::forget_password(&email);
    mode_setup_core::secure_storage::clear_cache();
    save_prefs(&app, |p| { p.email = None; p.remembered_password = false; });
    Ok(())
}

fn team(state: &State<'_, App>, id: &str) -> Res<Team> {
    state.teams.lock().unwrap().iter().find(|t| t.id == id).cloned().ok_or_else(|| "Sign in again.".to_string())
}

// ---- Mode's own VPN (paid teams) --------------------------------------------------------------

#[tauri::command]
async fn mode_vpn_turn_on(state: State<'_, App>, team_id: String) -> Res<VpnStatus> {
    let t = team(&state, &team_id)?;
    let mut s = state.signer.lock().await;
    s.as_mut().ok_or("Sign in again.")?.turn_on_mode_vpn(&t).await.map_err(err)
}

#[tauri::command]
async fn mode_vpn_status(state: State<'_, App>, team_id: String) -> Res<VpnStatus> {
    let t = team(&state, &team_id)?;
    let mut s = state.signer.lock().await;
    s.as_mut().ok_or("Sign in again.")?.mode_vpn_status(&t).await.map_err(err)
}

// ---- Installing -------------------------------------------------------------------------------

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProgressEvent {
    /// "mode" or "sidestore"
    what: String,
    /// "download", "signing", "installing"
    stage: String,
    fraction: f32,
}

fn progress(app: &AppHandle, what: &str) -> mode_setup_core::install::Progress {
    let handle = app.clone();
    let what = what.to_string();
    Arc::new(move |stage, f| {
        let stage = match stage { Stage::Signing => "signing", Stage::Installing => "installing" };
        let _ = handle.emit("progress", ProgressEvent { what: what.clone(), stage: stage.into(), fraction: f });
    })
}

/// The Mode app file to install. Always the newest Mode: the latest release when it is newer than the
/// copy inside this app (checked against its published SHA-256), else that copy. The copy inside is
/// also the fallback when GitHub can't be reached, so a first install works offline. An update
/// (`update`) only ever uses the latest release.
async fn mode_ipa(app: &AppHandle, paid: bool, update: bool) -> Res<PathBuf> {
    let name = if paid { "Mode-paid.ipa" } else { "Mode.ipa" };
    let inside = if update { None } else { bundled_ipa(app, name) };
    // The copy inside is the Mode this app shipped with: release.sh keeps their versions equal.
    let inside_version = app.package_info().version.to_string();
    let rel = match release::latest_mode_variant(paid).await {
        Ok(rel) if inside.is_none() || release::is_newer(&rel.version, &inside_version) => rel,
        Ok(_) => return Ok(inside.expect("checked above")),
        Err(e) => return inside.ok_or_else(|| err(e)),
    };
    let dest = std::env::temp_dir().join("ModeSetup").join(name);
    let handle = app.clone();
    let got = release::download(&rel.ipa_url, &dest, rel.sha256.as_deref(), move |f| {
        let _ = handle.emit("progress", ProgressEvent { what: "mode".into(), stage: "download".into(), fraction: f });
    })
    .await;
    match (got, inside) {
        (Ok(path), _) => Ok(path),
        // A failed or mismatched download never installs; the copy inside this app does instead.
        (Err(e), Some(inside)) => {
            eprintln!("Mode {}: download failed ({e}); installing the copy inside Mode Setup", rel.version);
            Ok(inside)
        }
        (Err(e), None) => Err(err(e)),
    }
}

/// The .ipa inside this app (release builds), or the one built in this repo (development).
fn bundled_ipa(app: &AppHandle, name: &str) -> Option<PathBuf> {
    if let Some(p) = app.path().resource_dir().ok().map(|d| d.join(name)).filter(|p| p.exists()) {
        return Some(p);
    }
    Some(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../build/release").join(name)).filter(|p| p.exists())
}

/// Is a newer Mode Setup out? Compares the latest release with this app's version. Read-only, no
/// sign-in; the new installer is downloaded from the website by the person.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SetupUpdate {
    current: String,
    latest: String,
    available: bool,
    /// Where to download it for this computer.
    url: String,
}

#[tauri::command]
async fn check_setup_update(app: AppHandle) -> Res<SetupUpdate> {
    let current = app.package_info().version.to_string();
    let latest = release::latest_version().await.map_err(err)?;
    let os = match std::env::consts::OS { "macos" => "mac", "windows" => "windows", _ => "linux" };
    Ok(SetupUpdate { available: release::is_newer(&latest, &current), current, latest, url: format!("https://heymode.app/download/{os}") })
}

#[tauri::command]
fn setup_version(app: AppHandle) -> String {
    app.package_info().version.to_string()
}

/// A read-only update check; it does not authenticate or access the Keychain.
#[tauri::command]
async fn check_update(own_vpn: bool) -> Res<release::Release> {
    release::latest_mode_variant(own_vpn).await.map_err(err)
}

#[tauri::command]
async fn install_mode(app: AppHandle, state: State<'_, App>, udid: String, phone_name: String, team_id: String, own_vpn: bool, update: Option<bool>) -> Res<Installed> {
    let t = team(&state, &team_id)?;
    let ipa = mode_ipa(&app, own_vpn, update.unwrap_or(false)).await?;
    let mut s = state.signer.lock().await;
    let installed = s.as_mut().ok_or("Sign in again.")?.install(&udid, &phone_name, &t, &ipa, progress(&app, "mode")).await.map_err(err)?;
    let record = Record { phone: phone_name, team_id: t.id.clone(), team_name: t.name.clone(), paid: t.kind == TeamKind::Paid, own_vpn,
                          mode: installed.clone(), sidestore: None };
    save_prefs(&app, |p| {
        let prev = p.phones.remove(&udid);
        p.phones.insert(udid.clone(), Record { sidestore: prev.and_then(|r| r.sidestore), ..record });
    });
    Ok(installed)
}

#[tauri::command]
async fn install_sidestore(app: AppHandle, state: State<'_, App>, udid: String, phone_name: String, team_id: String) -> Res<Installed> {
    let t = team(&state, &team_id)?;
    let dest = std::env::temp_dir().join("ModeSetup").join("SideStore.ipa");
    let handle = app.clone();
    release::download(release::SIDESTORE_IPA, &dest, None, move |f| {
        let _ = handle.emit("progress", ProgressEvent { what: "sidestore".into(), stage: "download".into(), fraction: f });
    })
    .await
    .map_err(err)?;
    let mut s = state.signer.lock().await;
    let installed = s.as_mut().ok_or("Sign in again.")?.install(&udid, &phone_name, &t, &dest, progress(&app, "sidestore")).await.map_err(err)?;
    let ss = installed.clone();
    save_prefs(&app, |p| {
        if let Some(r) = p.phones.get_mut(&udid) {
            r.sidestore = Some(ss);
        }
    });
    Ok(installed)
}

/// Phone control: a fresh pairing for Mode (every copy) and SideStore when it is there.
#[tauri::command]
async fn phone_control(state: State<'_, App>, udid: String) -> Res<Vec<String>> {
    let _pairing = state.pairing.try_lock().map_err(|_| "Phone control is already being reconnected. Please wait.")?;
    tokio::time::timeout(std::time::Duration::from_secs(60), async {
        match device::trust(&udid).await.map_err(err)? {
            Readiness::Ready => {},
            Readiness::Locked => return Err("Unlock your iPhone and try again.".into()),
            Readiness::NeedsTrust => return Err("Tap Trust on your iPhone, then try again.".into()),
            Readiness::Denied => return Err("Reconnect the cable and tap Trust on your iPhone.".into()),
            Readiness::NeedsDeveloperMode => return Err("Enable Developer Mode on your iPhone, then try again.".into()),
        }
        let control = pairing::make(&udid).await.map_err(err)?;
        let given = pairing::give_to_mode(&udid, &control).await.map_err(err)?;
        let all = apps::installed(&udid).await.map_err(err)?;
        if apps::sidestore(&all).is_some() {
            pairing::give_to_sidestore(&udid, &control).await.map_err(err)?;
        }
        Ok(given)
    }).await.map_err(|_| "Reconnecting timed out after 60 seconds. Unlock your iPhone, stop any running task in Mode, reconnect the cable and try again.".to_string())?
}

// ---- What this computer remembers (no secrets: those are in the keychain) -------------------------

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Record {
    phone: String,
    team_id: String,
    team_name: String,
    paid: bool,
    own_vpn: bool,
    mode: Installed,
    sidestore: Option<Installed>,
}

#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Prefs {
    email: Option<String>,
    #[serde(default)]
    remembered_password: bool,
    helper: Option<String>,
    #[serde(default)]
    phones: HashMap<String, Record>,
}

fn prefs_path(app: &AppHandle) -> Option<PathBuf> {
    app.path().app_data_dir().ok().map(|d| d.join("mode-setup.json"))
}

fn load_prefs(app: &AppHandle) -> Prefs {
    prefs_path(app).and_then(|p| std::fs::read(p).ok()).and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
}

fn save_prefs(app: &AppHandle, change: impl FnOnce(&mut Prefs)) {
    let mut p = load_prefs(app);
    change(&mut p);
    if let Some(path) = prefs_path(app) {
        if let Some(dir) = path.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        let _ = std::fs::write(path, serde_json::to_vec_pretty(&p).unwrap_or_default());
    }
}

#[tauri::command]
fn prefs(app: AppHandle) -> serde_json::Value {
    serde_json::to_value(load_prefs(&app)).unwrap_or_default()
}

#[tauri::command]
fn set_helper(app: AppHandle, url: String) {
    save_prefs(&app, |p| p.helper = Some(url));
}

/// Debug builds only: MODE_SETUP_START=<screen> opens the window there (to check screens against
/// their boards with a real iPhone). Release builds always answer none.
#[tauri::command]
fn debug_start() -> Option<String> {
    if cfg!(debug_assertions) { std::env::var("MODE_SETUP_START").ok() } else { None }
}

/// LocalDevVPN on the App Store (Coxson Engineering, the idevice maker), as the QR code on
/// "Finish on your iPhone".
const LOCAL_DEV_VPN: &str = "https://apps.apple.com/app/id6755608044";

#[tauri::command]
fn local_dev_vpn_qr() -> Res<String> {
    let code = qrcode::QrCode::new(LOCAL_DEV_VPN.as_bytes()).map_err(err)?;
    Ok(code.render::<qrcode::render::svg::Color>().min_dimensions(104, 104).quiet_zone(false).build())
}

/// Opens a web page in the browser (https only: developer.apple.com, sidestore.io, the App Store).
#[tauri::command]
fn open_url(url: String) -> Res<()> {
    if !url.starts_with("https://") {
        return Err("Only web links open from here.".into());
    }
    open::that_detached(&url).map_err(err)
}

/// A report to paste when asking for help: versions and what this computer remembers. No passwords,
/// codes, keys or phone-control files.
#[tauri::command]
fn diagnostics(app: AppHandle) -> String {
    let p = load_prefs(&app);
    let phones: Vec<String> = p
        .phones
        .values()
        .map(|r| format!("{} · team {} ({}) · Mode {} {} until {} · own VPN {} · SideStore {}", r.phone, r.team_id,
                         if r.paid { "paid" } else { "free" }, r.mode.version, r.mode.bundle_id, r.mode.expires.as_deref().unwrap_or("?"),
                         r.own_vpn, r.sidestore.as_ref().map(|s| s.version.as_str()).unwrap_or("no")))
        .collect();
    format!("Mode Setup {} on {}\nSign-in helper: {}\n{}", env!("CARGO_PKG_VERSION"), std::env::consts::OS,
            p.helper.as_deref().unwrap_or(HELPERS[0].url), phones.join("\n"))
}

fn main() {
    let _ = rustls::crypto::ring::default_provider().install_default();
    mode_setup_core::init();
    tauri::Builder::default()
        .manage(App::default())
        .invoke_handler(tauri::generate_handler![
            check_computer, check_setup_update, setup_version, phones, trust, show_developer_mode, apps_on_phone, helpers, sign_in, submit_code, active_account, cancel_sign_in, sign_out, check_update,
            mode_vpn_turn_on, mode_vpn_status, install_mode, install_sidestore, phone_control, prefs, set_helper, open_url, diagnostics,
            local_dev_vpn_qr, debug_start
        ])
        .run(tauri::generate_context!())
        .expect("Mode Setup could not start");
}
