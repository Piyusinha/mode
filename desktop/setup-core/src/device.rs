//! Finding iPhones on USB, getting them to trust this computer, and Developer Mode.

use idevice::{
    IdeviceService,
    amfi::AmfiClient,
    lockdown::LockdownClient,
    pairing_file::PairingFile,
    provider::UsbmuxdProvider,
    usbmuxd::{Connection, UsbmuxdAddr, UsbmuxdConnection},
};
use serde::Serialize;

use crate::{APP_NAME, Result, SetupError};

/// An iPhone this computer can see.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Phone {
    pub udid: String,
    pub name: String,
    /// "iPhone18,1"
    pub model: String,
    /// "27.1"
    pub ios: String,
    pub usb: bool,
}

/// Where the iPhone is on the way to being ready for setup.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Readiness {
    /// Waiting for Trust on the phone.
    NeedsTrust,
    /// Locked: unlock it to answer Trust.
    Locked,
    /// Said Don't Trust.
    Denied,
    /// Trusted, but Developer Mode is off (the switch is now shown in Settings).
    NeedsDeveloperMode,
    Ready,
}

pub async fn usbmuxd() -> Result<UsbmuxdConnection> {
    UsbmuxdConnection::default().await.map_err(|e| SetupError::NoPhoneService(e.to_string()))
}

pub(crate) fn addr() -> UsbmuxdAddr {
    UsbmuxdAddr::from_env_var().unwrap_or_default()
}

/// The iPhones connected now, by cable first. Wi-Fi-only phones are listed but can't be set up.
pub async fn phones() -> Result<Vec<Phone>> {
    let mut mux = usbmuxd().await?;
    let mut out = Vec::new();
    for d in mux.get_devices().await? {
        let usb = matches!(d.connection_type, Connection::Usb);
        let provider = d.to_provider(addr(), APP_NAME);
        // These three are readable before the phone trusts us.
        let mut lockdown = match LockdownClient::connect(&provider).await {
            Ok(l) => l,
            Err(_) => continue,
        };
        let text = async |lockdown: &mut LockdownClient, key: &str| {
            lockdown.get_value(Some(key), None).await.ok().and_then(|v| v.as_string().map(str::to_string)).unwrap_or_default()
        };
        let name = text(&mut lockdown, "DeviceName").await;
        let model = text(&mut lockdown, "ProductType").await;
        let ios = text(&mut lockdown, "ProductVersion").await;
        out.push(Phone { udid: d.udid.clone(), name, model, ios, usb });
    }
    out.sort_by_key(|p| !p.usb);
    // The same phone shows twice when it is on both USB and Wi-Fi.
    let mut seen = std::collections::HashSet::new();
    out.retain(|p| seen.insert(p.udid.clone()));
    Ok(out)
}

/// A connection to [phone] over USB.
pub async fn provider(udid: &str) -> Result<UsbmuxdProvider> {
    let mut mux = usbmuxd().await?;
    let devices = mux.get_devices().await?;
    let d = devices
        .iter()
        .find(|d| d.udid == udid && matches!(d.connection_type, Connection::Usb))
        .or_else(|| devices.iter().find(|d| d.udid == udid))
        .ok_or(SetupError::Disconnected)?;
    Ok(d.to_provider(addr(), APP_NAME))
}

/// This computer's pairing with [udid], if the phone already trusts it and the record still works.
pub async fn pair_record(udid: &str) -> Result<Option<PairingFile>> {
    let mut mux = usbmuxd().await?;
    let Ok(mut record) = mux.get_pair_record(udid).await else { return Ok(None) };
    record.udid = Some(udid.to_string());
    let provider = provider(udid).await?;
    let mut lockdown = LockdownClient::connect(&provider).await?;
    match lockdown.start_session(&record).await {
        Ok(_) => Ok(Some(record)),
        // The phone forgot this computer (Trust reset): pair again.
        Err(_) => Ok(None),
    }
}

/// Asks the phone to trust this computer, once, without waiting. Call again until it answers
/// anything but [Readiness::NeedsTrust]. The phone shows "Trust This Computer?" while unlocked.
pub async fn trust(udid: &str) -> Result<Readiness> {
    if pair_record(udid).await?.is_some() {
        return developer_mode_readiness(udid).await;
    }
    let mut mux = usbmuxd().await?;
    let buid = mux.get_buid().await?;
    let host_id = host_id();
    let provider = provider(udid).await?;
    let mut lockdown = LockdownClient::connect(&provider).await?;
    match lockdown.pair_once(host_id, buid, Some(APP_NAME)).await {
        Ok(mut record) => {
            record.udid = Some(udid.to_string());
            let bytes = record.serialize().map_err(|e| SetupError::Phone(e.to_string()))?;
            usbmuxd().await?.save_pair_record(udid, bytes).await?;
            developer_mode_readiness(udid).await
        }
        Err(e) => match SetupError::from(e) {
            SetupError::WaitingForTrust => Ok(Readiness::NeedsTrust),
            SetupError::Locked => Ok(Readiness::Locked),
            SetupError::TrustDenied => Ok(Readiness::Denied),
            other => Err(other),
        },
    }
}

async fn developer_mode_readiness(udid: &str) -> Result<Readiness> {
    if developer_mode(udid).await? { Ok(Readiness::Ready) } else { Ok(Readiness::NeedsDeveloperMode) }
}

/// Whether Developer Mode is on.
pub async fn developer_mode(udid: &str) -> Result<bool> {
    let provider = provider(udid).await?;
    let mut amfi = AmfiClient::connect(&provider).await?;
    Ok(amfi.get_developer_mode_status().await?)
}

/// Puts the Developer Mode switch in Settings → Privacy & Security, where it is hidden until a
/// developer tool connects. The person still turns it on and restarts; Apple allows no shortcut.
pub async fn show_developer_mode_switch(udid: &str) -> Result<()> {
    let provider = provider(udid).await?;
    let mut amfi = AmfiClient::connect(&provider).await?;
    amfi.reveal_developer_mode_option_in_ui().await?;
    Ok(())
}

fn host_id() -> String {
    // A fresh host ID per pairing, like Finder; usbmuxd keeps the record.
    let n: u128 = rand_u128();
    let h = format!("{n:032X}");
    format!("{}-{}-{}-{}-{}", &h[0..8], &h[8..12], &h[12..16], &h[16..20], &h[20..32])
}

fn rand_u128() -> u128 {
    use std::hash::{BuildHasher, Hasher};
    let mut a = std::collections::hash_map::RandomState::new().build_hasher();
    a.write_u128(std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_nanos()).unwrap_or(0));
    let mut b = std::collections::hash_map::RandomState::new().build_hasher();
    b.write_u64(a.finish());
    ((a.finish() as u128) << 64) | b.finish() as u128
}
