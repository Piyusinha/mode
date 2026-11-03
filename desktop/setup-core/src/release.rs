//! Getting the app files to install: Mode from its GitHub releases (checked against the published
//! SHA-256), SideStore from SideStore's.

use std::{path::{Path, PathBuf}, time::Duration};

use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use crate::{Result, SetupError};

pub const MODE_REPO: &str = "Piyusinha/mode";
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

#[derive(Deserialize)]
struct GhRelease {
    tag_name: String,
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

/// The newest Mode release.
pub async fn latest_mode() -> Result<Release> {
    latest_mode_variant(false).await
}

/// Select the matching artifact; a paid update must never download the free IPA.
pub async fn latest_mode_variant(paid: bool) -> Result<Release> {
    let asset = if paid { "Mode-paid.ipa" } else { MODE_ASSET };
    let url = format!("https://api.github.com/repos/{MODE_REPO}/releases/latest");
    let rel: GhRelease = client()?
        .get(&url)
        .send()
        .await
        .and_then(|r| r.error_for_status())
        .map_err(|e| SetupError::Download(format!("Mode’s release list ({e})")))?
        .json()
        .await
        .map_err(|e| SetupError::Download(format!("Mode’s release list ({e})")))?;
    let ipa = rel.assets.iter().find(|a| a.name == asset).ok_or_else(|| SetupError::Download(format!("{asset} in {}", rel.tag_name)))?;
    let sha = match rel.assets.iter().find(|a| a.name == format!("{asset}.sha256")) {
        Some(a) => Some(fetch_text(&a.browser_download_url).await?.split_whitespace().next().unwrap_or_default().to_lowercase()),
        None => None,
    };
    Ok(Release { version: rel.tag_name.trim_start_matches('v').to_string(), ipa_url: ipa.browser_download_url.clone(), sha256: sha })
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

pub fn matches_sha256(bytes: &[u8], expected: &str) -> bool {
    hex::encode(Sha256::digest(bytes)).eq_ignore_ascii_case(expected.trim())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checks_the_published_hash() {
        let h = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        assert!(matches_sha256(b"hello", h));
        assert!(matches_sha256(b"hello", &h.to_uppercase()));
        assert!(!matches_sha256(b"hello!", h));
    }
}
