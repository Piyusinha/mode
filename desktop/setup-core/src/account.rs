//! Signing in to Apple, and the teams that can sign Mode.
//!
//! The password goes only to Apple. A "sign-in helper" (anisette server) tells Apple which computer
//! this is; it never sees the password or the code. The helpers are run by open-source projects.

use std::{sync::Arc, time::Duration};

use isideload::{
    anisette::remote_v3::RemoteV3AnisetteProvider,
    auth::apple_account::{AppleAccount, TwoFactorCallbackParams, TwoFactorCallbackResponse},
    dev::{developer_session::DeveloperSession, device_type::{DeveloperDeviceType, dev_url}},
};
use serde::{Deserialize, Serialize};

use crate::{APP_NAME, Result, SetupError, error::from_report};

/// A sign-in helper the person can pick in Settings.
#[derive(Debug, Clone, Serialize)]
pub struct Helper {
    pub name: &'static str,
    pub url: &'static str,
}

/// iLoader's first: it is what isideload uses by default.
pub const HELPERS: [Helper; 2] = [
    Helper { name: "iLoader", url: "https://ani.iloader.app" },
    Helper { name: "SideStore", url: "https://ani.sidestore.io" },
];

/// How long the helper took to answer, or why it didn't.
pub async fn helper_answers(url: &str) -> Result<Duration> {
    let client = reqwest::Client::builder().timeout(Duration::from_secs(8)).build().map_err(|e| SetupError::HelperDown(e.to_string()))?;
    let started = std::time::Instant::now();
    let res = client.get(url).send().await.map_err(|e| SetupError::HelperDown(e.to_string()))?;
    if res.status().is_server_error() {
        return Err(SetupError::HelperDown(format!("HTTP {}", res.status())));
    }
    Ok(started.elapsed())
}

/// What Apple asks after the password.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CodeRequest {
    /// The code went by text to this number; otherwise to the person's devices.
    pub texted_to: Option<String>,
    /// Other numbers it can be texted to: (id, number).
    pub numbers: Vec<(u32, String)>,
    /// The last way failed; offer every way.
    pub last_failed: bool,
}

/// The person's answer to a [CodeRequest].
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "kind", content = "value", rename_all = "camelCase")]
pub enum CodeAnswer {
    Code(String),
    Resend,
    SendToDevices,
    Text(u32),
    Cancel,
}

/// The person's choice of who signs Mode.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum TeamKind {
    /// Personal Team: Mode lasts 7 days.
    Free,
    /// Apple Developer Program: Mode lasts a year.
    Paid,
    Unknown,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Team {
    pub id: String,
    pub name: String,
    pub kind: TeamKind,
}

/// A signed-in Apple account, ready to sign apps.
pub struct Session {
    pub email: String,
    pub(crate) dev: DeveloperSession,
}

/// Signs in. [ask_code] is called when Apple wants a verification code (it may be called again
/// after a wrong code or a resend). The password is used once and not kept here; the caller
/// decides whether to remember it ([remember_password]).
pub async fn sign_in<F, Fut>(email: &str, password: &str, helper_url: &str, ask_code: F) -> Result<Session>
where
    F: Fn(CodeRequest) -> Fut + Send + Sync + 'static,
    Fut: Future<Output = CodeAnswer> + Send + 'static,
{
    let ask = Arc::new(ask_code);
    let callback = move |p: TwoFactorCallbackParams| {
        let ask = ask.clone();
        async move {
            let numbers: Vec<(u32, String)> = p.numbers.iter().map(|n| (n.id, n.number_with_dial_code.clone())).collect();
            let texted_to = if p.sms {
                p.selected_number_id.and_then(|id| numbers.iter().find(|(i, _)| *i == id).map(|(_, n)| n.clone()))
            } else {
                None
            };
            let answer = ask(CodeRequest { texted_to, numbers, last_failed: p.unknown }).await;
            Ok(match answer {
                CodeAnswer::Code(c) => TwoFactorCallbackResponse::SubmitCode(c.trim().to_string()),
                CodeAnswer::Resend => TwoFactorCallbackResponse::ResendCode,
                CodeAnswer::SendToDevices => TwoFactorCallbackResponse::SendToDevices,
                CodeAnswer::Text(id) => TwoFactorCallbackResponse::SendSms(id),
                CodeAnswer::Cancel => TwoFactorCallbackResponse::Abort,
            })
        }
    };
    let helper = RemoteV3AnisetteProvider::default()
        .map_err(|e| SetupError::HelperDown(e.to_string()))?
        .set_url(helper_url)
        .set_storage(crate::secure_storage::storage());
    let mut account: AppleAccount = AppleAccount::builder(email)
        .anisette_provider(helper)
        .login(password, callback)
        .await
        .map_err(sign_in_error)?;
    let dev = DeveloperSession::from_account(&mut account).await.map_err(sign_in_error)?;
    Ok(Session { email: email.to_string(), dev })
}

fn sign_in_error(report: impl std::fmt::Display) -> SetupError {
    let text = report.to_string();
    let lower = text.to_lowercase();
    if lower.contains("-20101") || lower.contains("incorrect") || lower.contains("invalid password") {
        SetupError::WrongPassword
    } else if lower.contains("anisette") {
        SetupError::HelperDown(text)
    } else {
        SetupError::SignIn(text)
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RawTeam {
    name: Option<String>,
    team_id: String,
    r#type: Option<String>,
    #[serde(default)]
    memberships: Vec<RawMembership>,
}

#[derive(Deserialize)]
struct RawMembership {
    name: Option<String>,
}

/// The teams this account can sign with, paid ones first.
pub async fn teams(session: &mut Session) -> Result<Vec<Team>> {
    let raw: Vec<RawTeam> = session
        .dev
        .send_dev_request(&dev_url("listTeams", DeveloperDeviceType::Any), None, "teams")
        .await
        .map_err(|e| from_report("listing teams", e))?;
    let mut teams: Vec<Team> = raw
        .into_iter()
        .map(|t| {
            let kind = kind_of(t.r#type.as_deref(), t.memberships.iter().filter_map(|m| m.name.as_deref()));
            Team { name: t.name.unwrap_or_else(|| t.team_id.clone()), id: t.team_id, kind }
        })
        .collect();
    teams.sort_by_key(|t| match t.kind { TeamKind::Paid => 0, TeamKind::Unknown => 1, TeamKind::Free => 2 });
    Ok(teams)
}

/// Free or paid, as AltSign tells them apart: an organisation is paid; an individual is free when
/// its only membership is the free program. Confirmed later from the signed profile's expiry.
pub fn kind_of<'a>(team_type: Option<&str>, memberships: impl Iterator<Item = &'a str>) -> TeamKind {
    let names: Vec<String> = memberships.map(str::to_lowercase).collect();
    match team_type {
        Some("Company/Organization") => TeamKind::Paid,
        Some("Individual") if names.len() == 1 && names[0].contains("free") => TeamKind::Free,
        Some("Individual") if names.is_empty() => TeamKind::Free,
        Some("Individual") => TeamKind::Paid,
        _ if names.iter().any(|n| n.contains("free")) => TeamKind::Free,
        _ => TeamKind::Unknown,
    }
}

const PASSWORD_KEY: &str = "apple-password";

/// Keeps the password in this computer's keychain ("Remember on this computer").
pub fn remember_password(email: &str, password: &str) -> Result<()> {
    keyring::Entry::new(APP_NAME, &format!("{PASSWORD_KEY}:{email}"))
        .and_then(|e| e.set_password(password))
        .map_err(|e| SetupError::Keychain(e.to_string()))
}

/// The remembered password, if any.
pub fn remembered_password(email: &str) -> Option<String> {
    keyring::Entry::new(APP_NAME, &format!("{PASSWORD_KEY}:{email}")).ok()?.get_password().ok()
}

/// Forgets the password ("Sign out").
pub fn forget_password(email: &str) {
    if let Ok(e) = keyring::Entry::new(APP_NAME, &format!("{PASSWORD_KEY}:{email}")) {
        let _ = e.delete_credential();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn organisations_are_paid() {
        assert_eq!(kind_of(Some("Company/Organization"), ["Apple Developer Program"].into_iter()), TeamKind::Paid);
    }

    #[test]
    fn an_individual_on_the_free_program_is_free() {
        assert_eq!(kind_of(Some("Individual"), ["Free Developer Program"].into_iter()), TeamKind::Free);
        assert_eq!(kind_of(Some("Individual"), std::iter::empty()), TeamKind::Free);
    }

    #[test]
    fn an_individual_in_the_paid_program_is_paid() {
        assert_eq!(kind_of(Some("Individual"), ["Apple Developer Program"].into_iter()), TeamKind::Paid);
    }
}
