//! Phone control: the remote-pairing file that lets Mode (and SideStore) reach the phone's
//! developer services on their own, through a loopback VPN, with no computer.
//!
//! Made like iLoader's `pairing.rs` (nab138, MIT), itself after idevice_pair (Jackson Coxson, MIT).

use idevice::{
    IdeviceService,
    afc::opcode::AfcFopenMode,
    house_arrest::HouseArrestClient,
    lockdown::LockdownClient,
    remote_pairing::{RemotePairingLockdownService, RpPairingFile},
};

use crate::{Result, SetupError, apps, device};

/// The host name Mode connects as (engine/phone-ios PhoneConnector; tools/make-pairing.sh).
pub const HOST: &str = "Mode";
/// Where Mode reads it: Documents/mode.rppairing (engine/phone-ios PairingFile).
pub const MODE_FILE: &str = "mode.rppairing";
/// Where SideStore reads it (iLoader's list).
pub const SIDESTORE_FILES: [&str; 3] = ["ALTPairingFile.mobiledevicepairing", "PairingFile_RemoteRP.plist", "PairingFile_Lockdown.plist"];

/// One phone's pairing, in the two shapes apps read.
pub struct PhoneControl {
    /// The remote-pairing file alone (Mode).
    pub remote: Vec<u8>,
    /// This computer's lockdown pairing and the remote pairing in one plist (SideStore).
    pub combined: Vec<u8>,
}

/// Makes a fresh pairing for [udid]. The phone must trust this computer and be unlocked.
pub async fn make(udid: &str) -> Result<PhoneControl> {
    let record = device::pair_record(udid).await?.ok_or(SetupError::WaitingForTrust)?;
    let provider = device::provider(udid).await?;

    // Lets the phone be reached over its network side too (as iLoader does).
    let mut lockdown = LockdownClient::connect(&provider).await?;
    lockdown.start_session(&record).await?;
    let _ = lockdown.set_value("EnableWifiDebugging", true.into(), Some("com.apple.mobile.wireless_lockdown")).await;

    let service = RemotePairingLockdownService::connect(&provider).await.map_err(|e| SetupError::Pairing(e.to_string()))?;
    let mut client = service.into_client(HOST).map_err(|e| SetupError::Pairing(e.to_string()))?;
    let mut file = RpPairingFile::generate(HOST);
    client.connect(&mut file, async || "000000".to_string()).await.map_err(|e| SetupError::Pairing(e.to_string()))?;
    let remote = file.to_bytes();

    let lockdown_xml = record.serialize().map_err(|e| SetupError::Pairing(e.to_string()))?;
    let mut combined: plist::Dictionary = plist::from_bytes(&lockdown_xml).map_err(|e| SetupError::Pairing(e.to_string()))?;
    let rp: plist::Dictionary = plist::from_bytes(&remote).map_err(|e| SetupError::Pairing(e.to_string()))?;
    combined.extend(rp);
    let mut out = Vec::new();
    plist::to_writer_xml(&mut out, &combined).map_err(|e| SetupError::Pairing(e.to_string()))?;
    Ok(PhoneControl { remote, combined: out })
}

/// Writes [bytes] into the app's Documents under each of [names], then reads the first back.
pub async fn place(udid: &str, bundle_id: &str, names: &[&str], bytes: &[u8]) -> Result<()> {
    let provider = device::provider(udid).await?;
    let house_arrest = HouseArrestClient::connect(&provider).await?;
    let mut afc = house_arrest.vend_documents(bundle_id).await.map_err(|e| SetupError::Pairing(format!("opening the app’s files: {e}")))?;
    let _ = afc.mk_dir("/Documents").await;
    for name in names {
        let mut f = afc.open(format!("/Documents/{name}"), AfcFopenMode::Wr).await?;
        f.write_entire(bytes).await?;
        f.close().await?;
    }
    if let Some(first) = names.first() {
        let mut f = afc.open(format!("/Documents/{first}"), AfcFopenMode::RdOnly).await?;
        let back = f.read_entire().await?;
        f.close().await?;
        if back != bytes {
            return Err(SetupError::Pairing("the file read back differs".into()));
        }
    }
    Ok(())
}

/// Gives Mode its phone-control file. Mode must be installed.
pub async fn give_to_mode(udid: &str, control: &PhoneControl) -> Result<String> {
    let apps = apps::installed(udid).await?;
    let mode = apps::mode(&apps).ok_or(SetupError::ModeMissing)?;
    place(udid, &mode.bundle_id, &[MODE_FILE], &control.remote).await?;
    Ok(mode.bundle_id.clone())
}

/// Gives SideStore the same pairing, so it can renew apps on the phone.
pub async fn give_to_sidestore(udid: &str, control: &PhoneControl) -> Result<()> {
    let apps = apps::installed(udid).await?;
    let ss = apps::sidestore(&apps).ok_or_else(|| SetupError::Install("SideStore isn’t on this iPhone".into()))?;
    place(udid, &ss.bundle_id, &SIDESTORE_FILES, &control.combined).await
}
