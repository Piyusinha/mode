//! Getting the app files to install: Mode from its GitHub releases (checked against the published
//! SHA-256), SideStore from SideStore's.

use std::{path::{Path, PathBuf}, time::Duration};

use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use crate::{Result, SetupError};

pub const MODE_REPO: &str = "heymode/mode";
/// The free build: Mode and its widget, no special entitlements (iosApp/project.yml).
pub const MODE_ASSET: &str = "Mode.ipa";
pub const SIDESTORE_IPA: &str = "https://github.com/SideStore/SideStore/releases/latest/download/SideStore.ipa";

/// A Mode release on GitHub.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Release {
    pub version: String,
    pub ipa_url: String,
    /// From the release's `Mode.ipa.sha256` asset.
    pub sha256: Option<String>,
}

/// The two kinds of release, each with its own version and tag: Mode, the phone app
/// ("Mode-Mobile-Build-0.2.0", iPhone and Android), and Mode Setup, this desktop app
/// ("Mode-Desktop-App-0.1.3"). The first releases
/// ("v0.1.0" to "v0.1.2") held both, so they count as either.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    App,
    Setup,
}

impl Kind {
    /// The version in a release tag of this kind ("Mode-Mobile-Build-0.2.0" → "0.2.0"), or None for the other kind.
    pub fn version_of(self, tag: &str) -> Option<&str> {
        let prefix = match self { Kind::App => "Mode-Mobile-Build-", Kind::Setup => "Mode-Desktop-App-" };
        let v = tag.strip_prefix(prefix).or_else(|| tag.strip_prefix('v'))?;
        v.starts_with(|c: char| c.is_ascii_digit()).then_some(v)
    }
}

#[derive(Deserialize)]
struct GhRelease {
    tag_name: String,
    #[serde(default)]
    draft: bool,
    #[serde(default)]
    prerelease: bool,
    assets: Vec<GhAsset>,
}

#[derive(Deserialize)]
struct GhAsset {
    name: String,
    browser_download_url: String,
}

fn client() -> Result<reqwest::Client> {
    reqwest::Client::builder()
        .user_agent(concat!("ModeSetup/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(60))
        .build()
        .map_err(|e| SetupError::Download(e.to_string()))
}

/// The newest published release of this kind that has [asset] (any asset when None).
fn newest(list: Vec<GhRelease>, kind: Kind, asset: Option<&str>) -> Option<(String, GhRelease)> {
    let mut best: Option<(String, GhRelease)> = None;
    for r in list {
        if r.draft || r.prerelease { continue; }
        let Some(v) = kind.version_of(&r.tag_name).map(str::to_string) else { continue };
        if asset.is_some_and(|a| !r.assets.iter().any(|x| x.name == a)) { continue; }
        if best.as_ref().is_none_or(|(b, _)| is_newer(&v, b)) { best = Some((v, r)); }
    }
    best
}

async fn releases() -> Result<Vec<GhRelease>> {
    let url = format!("https://api.github.com/repos/{MODE_REPO}/releases?per_page=50");
    client()?
        .get(&url)
        .send()
        .await
        .and_then(|r| r.error_for_status())
        .map_err(|e| SetupError::Download(format!("Mode’s release list ({e})")))?
        .json()
        .await
        .map_err(|e| SetupError::Download(format!("Mode’s release list ({e})")))
}

/// The newest Mode Setup's version ("0.1.3"), to tell whether this one is out of date.
pub async fn latest_setup_version() -> Result<String> {
    newest(releases().await?, Kind::Setup, None)
        .map(|(v, _)| v)
        .ok_or_else(|| SetupError::Download("Mode Setup in the release list".into()))
}

/// The newest Mode release.
pub async fn latest_mode() -> Result<Release> {
    latest_mode_variant(false).await
}

/// The newest Mode, free or paid build; a paid update must never download the free IPA.
pub async fn latest_mode_variant(paid: bool) -> Result<Release> {
    let asset = if paid { "Mode-paid.ipa" } else { MODE_ASSET };
    let (version, rel) = newest(releases().await?, Kind::App, Some(asset))
        .ok_or_else(|| SetupError::Download(format!("{asset} in Mode’s releases")))?;
    let ipa = rel.assets.iter().find(|a| a.name == asset).expect("checked in newest");
    let sha = match rel.assets.iter().find(|a| a.name == format!("{asset}.sha256")) {
        Some(a) => Some(fetch_text(&a.browser_download_url).await?.split_whitespace().next().unwrap_or_default().to_lowercase()),
        None => None,
    };
    Ok(Release { version, ipa_url: ipa.browser_download_url.clone(), sha256: sha })
}

async fn fetch_text(url: &str) -> Result<String> {
    client()?
        .get(url)
        .send()
        .await
        .and_then(|r| r.error_for_status())
        .map_err(|e| SetupError::Download(e.to_string()))?
        .text()
        .await
        .map_err(|e| SetupError::Download(e.to_string()))
}

/// Downloads [url] to [dest], reporting 0.0–1.0, and checks [sha256] when given.
pub async fn download(url: &str, dest: &Path, sha256: Option<&str>, progress: impl Fn(f32)) -> Result<PathBuf> {
    let res = client()?.get(url).send().await.and_then(|r| r.error_for_status()).map_err(|e| SetupError::Download(e.to_string()))?;
    let total = res.content_length().unwrap_or(0) as f32;
    let mut stream = res.bytes_stream();
    let mut bytes = Vec::new();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| SetupError::Download(e.to_string()))?;
        bytes.extend_from_slice(&chunk);
        if total > 0.0 {
            progress(bytes.len() as f32 / total);
        }
    }
    if let Some(expected) = sha256 {
        if !matches_sha256(&bytes, expected) {
            return Err(SetupError::Checksum);
        }
    }
    if let Some(dir) = dest.parent() {
        tokio::fs::create_dir_all(dir).await.map_err(|e| SetupError::Download(e.to_string()))?;
    }
    tokio::fs::write(dest, &bytes).await.map_err(|e| SetupError::Download(e.to_string()))?;
    Ok(dest.to_path_buf())
}

/// Whether release version [candidate] is newer than [current] ("0.1.10" > "0.1.9"; a leading "v" is
/// ignored). Unreadable versions are never newer.
pub fn is_newer(candidate: &str, current: &str) -> bool {
    fn parts(v: &str) -> Option<Vec<u64>> {
        v.trim().trim_start_matches('v').split('.').map(|p| p.parse().ok()).collect()
    }
    match (parts(candidate), parts(current)) {
        (Some(mut a), Some(mut b)) => {
            let n = a.len().max(b.len());
            a.resize(n, 0);
            b.resize(n, 0);
            a > b
        }
        _ => false,
    }
}

pub fn matches_sha256(bytes: &[u8], expected: &str) -> bool {
    hex::encode(Sha256::digest(bytes)).eq_ignore_ascii_case(expected.trim())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rel(tag: &str, draft: bool, assets: &[&str]) -> GhRelease {
        GhRelease { tag_name: tag.into(), draft, prerelease: false,
                    assets: assets.iter().map(|n| GhAsset { name: n.to_string(), browser_download_url: format!("https://x/{tag}/{n}") }).collect() }
    }

    #[test]
    fn each_kind_finds_its_own_newest_release() {
        let list = || vec![
            rel("Mode-Desktop-App-0.1.4", false, &["Mode-Setup-mac.dmg"]),
            rel("Mode-Mobile-Build-0.3.0", true, &["Mode.ipa"]),          // a draft: not out yet
            rel("Mode-Mobile-Build-0.2.0", false, &["Mode.ipa", "Mode-paid.ipa"]),
            rel("Mode-Desktop-App-0.1.3", false, &["Mode-Setup-mac.dmg"]),
            rel("v0.1.2", false, &["Mode.ipa", "Mode-paid.ipa", "Mode-Setup-mac.dmg"]),
        ];
        assert_eq!(newest(list(), Kind::App, Some("Mode.ipa")).unwrap().0, "0.2.0");
        assert_eq!(newest(list(), Kind::Setup, None).unwrap().0, "0.1.4");
        // Before the split: the combined release counts as both.
        let old = || vec![rel("v0.1.2", false, &["Mode.ipa"]), rel("v0.1.1", false, &["Mode.ipa"])];
        assert_eq!(newest(old(), Kind::App, Some("Mode.ipa")).unwrap().0, "0.1.2");
        assert_eq!(newest(old(), Kind::Setup, None).unwrap().0, "0.1.2");
        // A release without the file asked for is skipped.
        assert!(newest(list(), Kind::App, Some("Mode.apk")).is_none());
    }

    #[test]
    fn tags_by_kind() {
        assert_eq!(Kind::App.version_of("Mode-Mobile-Build-0.2.0"), Some("0.2.0"));
        assert_eq!(Kind::App.version_of("Mode-Desktop-App-0.2.0"), None);
        assert_eq!(Kind::Setup.version_of("Mode-Desktop-App-0.1.3"), Some("0.1.3"));
        assert_eq!(Kind::Setup.version_of("v0.1.2"), Some("0.1.2"));
        assert_eq!(Kind::App.version_of("vnext"), None);
    }

    #[test]
    fn compares_release_versions() {
        assert!(is_newer("0.1.2", "0.1.1"));
        assert!(is_newer("v0.2.0", "0.1.9"));
        assert!(is_newer("0.1.10", "0.1.9"));
        assert!(is_newer("0.1.1", "0.1"));
        assert!(!is_newer("0.1.1", "0.1.1"));
        assert!(!is_newer("0.1.0", "0.1"));
        assert!(!is_newer("0.1.0", "0.1.1"));
        assert!(!is_newer("next", "0.1.1"));
    }

    #[test]
    fn checks_the_published_hash() {
        let h = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        assert!(matches_sha256(b"hello", h));
        assert!(matches_sha256(b"hello", &h.to_uppercase()));
        assert!(!matches_sha256(b"hello!", h));
    }
}
