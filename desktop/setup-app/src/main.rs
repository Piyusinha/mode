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
}

type Res<T> = Result<T, String>;

fn err(e: impl std::fmt::Display) -> String {
    e.to_string()
}

// ---- iPhone -----------------------------------------------------------------------------------

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
    let password = match password.filter(|p| !p.is_empty()) {
        Some(p) => p,
        None => account::remembered_password(&email).ok_or("Enter your password.")?,
    };
    let slot = state.code.clone();
    let handle = app.clone();
    let ask = move |req: CodeRequest| {
        let (tx, rx) = oneshot::channel();
        *slot.lock().unwrap() = Some(tx);
        let _ = handle.emit("code-needed", req);
        async move { rx.await.unwrap_or(CodeAnswer::Cancel) }
    };
    let mut session = account::sign_in(&email, &password, &helper, ask).await.map_err(err)?;
    if remember {
        account::remember_password(&email, &password).map_err(err)?;
    } else {
        account::forget_password(&email);
    }
    let teams = account::teams(&mut session).await.map_err(err)?;
    *state.teams.lock().unwrap() = teams.clone();
    *state.signer.lock().await = Some(Signer::new(session));
    save_prefs(&app, |p| p.email = Some(email.clone()));
    Ok(teams)
}

#[tauri::command]
fn submit_code(state: State<'_, App>, answer: CodeAnswer) {
    if let Some(tx) = state.code.lock().unwrap().take() {
        let _ = tx.send(answer);
    }
}

#[tauri::command]
fn has_remembered_password(email: String) -> bool {
    account::remembered_password(&email).is_some()
}

#[tauri::command]
fn sign_out(app: AppHandle, email: String) {
    account::forget_password(&email);
    save_prefs(&app, |p| p.email = None);
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

/// The Mode app file to install: bundled with this app, else the one built in this repo, else the
/// latest release (checked against its published SHA-256).
async fn mode_ipa(app: &AppHandle, paid: bool) -> Res<PathBuf> {
    let name = if paid { "Mode-paid.ipa" } else { "Mode.ipa" };
    if let Ok(dir) = app.path().resource_dir() {
        let p = dir.join(name);
        if p.exists() {
            return Ok(p);
        }
    }
    let dev = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../build/release").join(name);
    if dev.exists() {
        return Ok(dev);
    }
    let rel = release::latest_mode().await.map_err(err)?;
    let dest = std::env::temp_dir().join("ModeSetup").join(name);
    let handle = app.clone();
    release::download(&rel.ipa_url, &dest, rel.sha256.as_deref(), move |f| {
        let _ = handle.emit("progress", ProgressEvent { what: "mode".into(), stage: "download".into(), fraction: f });
    })
    .await
    .map_err(err)
}

#[tauri::command]
async fn install_mode(app: AppHandle, state: State<'_, App>, udid: String, phone_name: String, team_id: String, own_vpn: bool) -> Res<Installed> {
    let t = team(&state, &team_id)?;
    let ipa = mode_ipa(&app, own_vpn).await?;
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
async fn phone_control(udid: String) -> Res<Vec<String>> {
    let control = pairing::make(&udid).await.map_err(err)?;
    let given = pairing::give_to_mode(&udid, &control).await.map_err(err)?;
    let all = apps::installed(&udid).await.map_err(err)?;
    if apps::sidestore(&all).is_some() {
        pairing::give_to_sidestore(&udid, &control).await.map_err(err)?;
    }
    Ok(given)
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
            phones, trust, show_developer_mode, apps_on_phone, helpers, sign_in, submit_code, has_remembered_password, sign_out,
            mode_vpn_turn_on, mode_vpn_status, install_mode, install_sidestore, phone_control, prefs, set_helper, open_url, diagnostics,
            local_dev_vpn_qr, debug_start
        ])
        .run(tauri::generate_context!())
        .expect("Mode Setup could not start");
}
