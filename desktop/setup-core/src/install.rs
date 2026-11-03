//! Signing Mode (or SideStore) with the person's team and installing it over USB.

use std::{
    path::{Path, PathBuf},
    sync::Arc,
};

use isideload::{
    dev::{devices::DevicesApi, teams::DeveloperTeam},
    sideload::{
        SideloaderBuilder,
        builder::MaxCertsBehavior,
        sideloader::Sideloader,
    },
    util::{
        callbacks::{ExtensionsBehaviorCallbackBox, MaxCertsCallbackBox},
        keyring_storage::KeyringStorage,
    },
};
use serde::Serialize;

use crate::{APP_NAME, Result, SetupError, account::{Session, Team}, device, error::from_report};

/// The stages of one install, as the Installing screen lists them.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Stage {
    Signing,
    Installing,
}

/// Progress: the stage and how far through it, 0.0–1.0.
pub type Progress = Arc<dyn Fn(Stage, f32) + Send + Sync>;

/// What was installed, as the Ready and This iPhone screens show it.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Installed {
    /// e.g. "com.piyush.mode.ABCDE12345": the team is part of it, like every app signed this way.
    pub bundle_id: String,
    pub name: String,
    pub version: String,
    pub team_id: String,
    /// When the signing runs out (from the profile Apple issued), as RFC 3339.
    pub expires: Option<String>,
    /// SideStore gets this computer's certificate so it can renew without revoking it.
    pub special: Option<String>,
}

/// Signs and installs apps for one signed-in account.
pub struct Signer {
    inner: Sideloader<MaxCertsCallbackBox, ExtensionsBehaviorCallbackBox>,
}

impl Signer {
    /// Never revokes certificates other tools made: when the account has too many, it stops with
    /// [SetupError::CertificateLimit] and the person decides.
    pub fn new(session: Session) -> Self {
        let inner = SideloaderBuilder::<MaxCertsCallbackBox, ExtensionsBehaviorCallbackBox>::new(session.dev, session.email)
            .max_certs_behavior(MaxCertsBehavior::Error)
            .storage(Box::new(KeyringStorage::new(APP_NAME.to_string())))
            .machine_name(APP_NAME.to_string())
            .build();
        Self { inner }
    }

    /// Signs [ipa] with [team] for the phone [udid], then installs it, keeping the app's data when
    /// it is already there. The phone must trust this computer.
    pub async fn install(&mut self, udid: &str, phone_name: &str, team: &Team, ipa: &Path, progress: Progress) -> Result<Installed> {
        let dev_team = DeveloperTeam { name: Some(team.name.clone()), team_id: team.id.clone(), r#type: None, status: None };
        self.inner
            .get_dev_session()
            .ensure_device_registered(&dev_team, phone_name, udid, None)
            .await
            .map_err(|e| from_report("registering this iPhone", e))?;

        let p = progress.clone();
        let (signed, special) = self
            .inner
            .sign_app(
                ipa.to_path_buf(),
                Some(dev_team),
                false,
                Some(move |f: f32| {
                    let p = p.clone();
                    async move { p(Stage::Signing, f) }
                }),
                None,
                Some(udid),
            )
            .await
            .map_err(|e| from_report("signing", e))?;

        let mut installed = describe(&signed, &team.id)?;
        installed.special = special.map(|s| s.to_string());

        let provider = device::provider(udid).await?;
        let p = progress.clone();
        isideload::sideload::install::install_app(&provider, &signed, move |pct| p(Stage::Installing, pct as f32 / 100.0))
            .await
            .map_err(|e| from_report("installing", e))?;
        let _ = std::fs::remove_dir_all(&signed);
        Ok(installed)
    }
}

/// Reads the signed bundle's identity and the profile's expiry.
fn describe(app: &PathBuf, team_id: &str) -> Result<Installed> {
    let info: plist::Dictionary = plist::from_file(app.join("Info.plist")).map_err(|e| SetupError::Install(format!("reading the signed app: {e}")))?;
    let text = |k: &str| info.get(k).and_then(|v| v.as_string()).unwrap_or_default().to_string();
    let expires = std::fs::read(app.join("embedded.mobileprovision")).ok().and_then(|b| profile_expiry(&b));
    Ok(Installed {
        bundle_id: text("CFBundleIdentifier"),
        name: { let n = text("CFBundleDisplayName"); if n.is_empty() { text("CFBundleName") } else { n } },
        version: text("CFBundleShortVersionString"),
        team_id: team_id.to_string(),
        expires,
        special: None,
    })
}

/// The ExpirationDate inside a provisioning profile (a signed plist), as RFC 3339.
pub fn profile_expiry(profile: &[u8]) -> Option<String> {
    let start = find(profile, b"<?xml")?;
    let end = find(&profile[start..], b"</plist>")? + start + b"</plist>".len();
    let dict: plist::Dictionary = plist::from_bytes(&profile[start..end]).ok()?;
    let date = dict.get("ExpirationDate")?.as_date()?;
    Some(date.to_xml_format())
}

fn find(hay: &[u8], needle: &[u8]) -> Option<usize> {
    hay.windows(needle.len()).position(|w| w == needle)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_expiry_out_of_a_signed_profile() {
        let mut profile = b"\x30\x80junk-before".to_vec();
        profile.extend_from_slice(
            br#"<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict><key>ExpirationDate</key><date>2026-11-10T08:20:00Z</date><key>Name</key><string>x</string></dict></plist>"#,
        );
        profile.extend_from_slice(b"\x00signature-after");
        assert_eq!(profile_expiry(&profile).as_deref(), Some("2026-11-10T08:20:00Z"));
    }

    #[test]
    fn no_profile_no_expiry() {
        assert_eq!(profile_expiry(b"nothing here"), None);
    }
}
