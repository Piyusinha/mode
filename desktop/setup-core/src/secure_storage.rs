//! Cache successful Keychain reads for this process; retain the existing persistent item names.
//! A repeated provider lookup must not repeatedly request the same macOS authorization.
use std::{collections::HashMap, sync::{Arc, Mutex, OnceLock}};
use isideload::util::{keyring_storage::KeyringStorage, storage::SideloadingStorage};
use rootcause::Report;

struct CachedStorage<S> {
    inner: S,
    values: Mutex<HashMap<String, Option<String>>>,
}
impl<S: SideloadingStorage> SideloadingStorage for CachedStorage<S> {
    fn retrieve(&self, key: &str) -> Result<Option<String>, Report> {
        let mut values = self.values.lock().unwrap();
        if let Some(value) = values.get(key) { return Ok(value.clone()); }
        let value = self.inner.retrieve(key)?;
        values.insert(key.into(), value.clone());
        Ok(value)
    }
    fn store(&self, key: &str, value: &str) -> Result<(), Report> {
        let mut values = self.values.lock().unwrap();
        if values.get(key).and_then(|v| v.as_deref()) == Some(value) { return Ok(()); }
        self.inner.store(key, value)?;
        values.insert(key.into(), Some(value.into()));
        Ok(())
    }
    // Preserve KeyringStorage's binary-secret API on Windows (base64 exceeds its size limit).
    #[cfg(target_os = "windows")]
    fn store_data(&self, key: &str, value: &[u8]) -> Result<(), Report> { self.inner.store_data(key, value) }
    #[cfg(target_os = "windows")]
    fn retrieve_data(&self, key: &str) -> Result<Option<Vec<u8>>, Report> { self.inner.retrieve_data(key) }
    fn delete(&self, key: &str) -> Result<(), Report> {
        let mut values = self.values.lock().unwrap();
        self.inner.delete(key)?;
        values.insert(key.into(), None);
        Ok(())
    }
}
type Cache = CachedStorage<KeyringStorage>;
static CACHE: OnceLock<Arc<Cache>> = OnceLock::new();
struct SharedStorage(Arc<Cache>);
impl SideloadingStorage for SharedStorage {
    fn retrieve(&self, key: &str) -> Result<Option<String>, Report> { self.0.retrieve(key) }
    fn store(&self, key: &str, value: &str) -> Result<(), Report> { self.0.store(key, value) }
    fn delete(&self, key: &str) -> Result<(), Report> { self.0.delete(key) }
    #[cfg(target_os = "windows")]
    fn store_data(&self, key: &str, value: &[u8]) -> Result<(), Report> { self.0.store_data(key, value) }
    #[cfg(target_os = "windows")]
    fn retrieve_data(&self, key: &str) -> Result<Option<Vec<u8>>, Report> { self.0.retrieve_data(key) }
}
pub fn storage() -> Box<dyn SideloadingStorage> {
    Box::new(SharedStorage(CACHE.get_or_init(|| Arc::new(Cache {
        inner: KeyringStorage::new(crate::APP_NAME.into()), values: Mutex::new(HashMap::new()),
    })).clone()))
}
pub fn clear_cache() {
    if let Some(cache) = CACHE.get() { cache.values.lock().unwrap().clear(); }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    struct Counted { reads: AtomicUsize, writes: AtomicUsize }
    impl SideloadingStorage for Counted {
        fn retrieve(&self, key: &str) -> Result<Option<String>, Report> {
            self.reads.fetch_add(1, Ordering::SeqCst);
            Ok((key == "certificate").then(|| "secret".into()))
        }
        fn store(&self, _: &str, _: &str) -> Result<(), Report> {
            self.writes.fetch_add(1, Ordering::SeqCst); Ok(())
        }
    }
    #[test]
    fn repeated_reads_and_unchanged_writes_do_not_reopen_keychain_items() {
        let cache = CachedStorage { inner: Counted { reads: AtomicUsize::new(0), writes: AtomicUsize::new(0) }, values: Mutex::new(HashMap::new()) };
        for _ in 0..8 {
            assert_eq!(cache.retrieve("certificate").unwrap().as_deref(), Some("secret"));
            assert_eq!(cache.retrieve("missing").unwrap(), None);
            cache.store("certificate", "secret").unwrap();
        }
        assert_eq!(cache.inner.reads.load(Ordering::SeqCst), 2);
        assert_eq!(cache.inner.writes.load(Ordering::SeqCst), 0);
        cache.store("certificate", "rotated").unwrap();
        assert_eq!(cache.retrieve("certificate").unwrap().as_deref(), Some("rotated"));
        assert_eq!(cache.inner.writes.load(Ordering::SeqCst), 1);
    }
}
