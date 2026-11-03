//! Signing Mode (or SideStore) with the person's team and installing it over USB.

use std::{
    path::{Path, PathBuf},
    sync::Arc,
};

use isideload::{
    dev::{app_ids::AppIdsApi, devices::DevicesApi, teams::DeveloperTeam},
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
use serde::{Deserialize, Serialize};

use crate::{APP_NAME, Result, SetupError, account::{Session, Team}, apps::MODE_BUNDLE, device, error::from_report};

/// Apple's developer-API key for the Network Extensions capability (fastlane's
/// `Spaceship::Portal::App::Service::NetworkExtension`; App Groups is "APG3427HIY", as isideload uses).
pub const NETWORK_EXTENSIONS: &str = "NWEXT04537";

/// Whether Mode's own VPN may be signed for a team: Network Extensions on both of Mode's app IDs.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnStatus {
    /// "com.piyush.mode.ABCDE12345"
    pub app_id: String,
    /// "com.piyush.mode.ABCDE12345.tunnel"
    pub tunnel_id: String,
    pub app_on: bool,
    pub tunnel_on: bool,
    /// Why Apple refused to switch it on, when it did.
    pub refused: Option<String>,
}

impl VpnStatus {
    pub fn on(&self) -> bool {
        self.app_on && self.tunnel_on
    }
}

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
#[derive(Debug, Clone, Serialize, Deserialize)]
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

    fn dev_team(team: &Team) -> DeveloperTeam {
        DeveloperTeam { name: Some(team.name.clone()), team_id: team.id.clone(), r#type: None, status: None }
    }

    /// Reads from Apple whether Network Extensions is on for Mode's app IDs in [team]. Cheap enough
    /// to call every few seconds while the person switches it on at developer.apple.com.
    pub async fn mode_vpn_status(&mut self, team: &Team) -> Result<VpnStatus> {
        let app_id = format!("{MODE_BUNDLE}.{}", team.id);
        let tunnel_id = format!("{app_id}.tunnel");
        let list = self
            .inner
            .get_dev_session()
            .list_app_ids(&Self::dev_team(team), None)
            .await
            .map_err(|e| from_report("reading your app IDs", e))?;
        let on = |id: &str| {
            list.app_ids
                .iter()
                .find(|a| a.identifier == id)
                .and_then(|a| a.features.get(NETWORK_EXTENSIONS))
                .and_then(|v| v.as_boolean())
                .unwrap_or(false)
        };
        Ok(VpnStatus { app_on: on(&app_id), tunnel_on: on(&tunnel_id), app_id, tunnel_id, refused: None })
    }

    /// Registers Mode's two app IDs if they are new and switches on Network Extensions on both, so
    /// the profiles Apple then issues let Mode run its own VPN. Paid teams only; when Apple refuses,
    /// the answer says why and the person can switch it on at developer.apple.com.
    pub async fn turn_on_mode_vpn(&mut self, team: &Team) -> Result<VpnStatus> {
        let dev_team = Self::dev_team(team);
        let app_id = format!("{MODE_BUNDLE}.{}", team.id);
        let tunnel_id = format!("{app_id}.tunnel");
        let session = self.inner.get_dev_session();
        let list = session.list_app_ids(&dev_team, None).await.map_err(|e| from_report("reading your app IDs", e))?;
        let mut refused = None;
        for (id, name) in [(&app_id, "Mode"), (&tunnel_id, "Mode Tunnel")] {
            let existing = match list.app_ids.iter().find(|a| &a.identifier == id) {
                Some(a) => a.clone(),
                None => match session.add_app_id(&dev_team, name, id, None).await {
                    Ok(a) => a,
                    Err(e) => {
                        refused = Some(e.to_string());
                        continue;
                    }
                },
            };
            let on = existing.features.get(NETWORK_EXTENSIONS).and_then(|v| v.as_boolean()).unwrap_or(false);
            if !on {
                let mut features = plist::Dictionary::new();
                features.insert(NETWORK_EXTENSIONS.into(), true.into());
                if let Err(e) = session.update_app_id(&dev_team, &existing, features, None).await {
                    refused = Some(e.to_string());
                }
            }
        }
        let mut status = self.mode_vpn_status(team).await?;
        if !status.on() {
            status.refused = refused.or_else(|| Some("Apple kept it off".into()));
        }
        Ok(status)
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
