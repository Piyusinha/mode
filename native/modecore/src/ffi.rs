//! The C API Kotlin/Native binds to (`include/modecore.h`).
//!
//! Every call returns NULL on success or an error message the caller frees
//! with `mc_string_free`. Panics are caught at the boundary and reported as
//! errors. A session is used from one thread at a time.

use std::ffi::{CStr, CString, c_char};
use std::net::{IpAddr, SocketAddr};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr::null_mut;
use std::time::Duration;

use crate::keys;
use crate::session::{Button, Clock, Error, Result, Session};

/// Opaque to C.
pub struct McSession(Session);

fn guard(f: impl FnOnce() -> Result<()>) -> *mut c_char {
    let outcome = catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|panic| {
        let what = panic
            .downcast_ref::<&str>()
            .map(|s| s.to_string())
            .or_else(|| panic.downcast_ref::<String>().cloned())
            .unwrap_or_else(|| "unknown".into());
        Err(Error(format!("modecore panicked: {what}")))
    });
    match outcome {
        Ok(()) => null_mut(),
        Err(e) => owned_c_string(e.0),
    }
}

fn owned_c_string(s: String) -> *mut c_char {
    CString::new(s.replace('\0', " ")).unwrap_or_default().into_raw()
}

unsafe fn text<'a>(ptr: *const c_char, what: &str) -> Result<&'a str> {
    if ptr.is_null() {
        return Err(Error(format!("{what} is NULL")));
    }
    // SAFETY: the caller passes a NUL-terminated string that outlives the call.
    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .map_err(|_| Error(format!("{what} is not UTF-8")))
}

unsafe fn session<'a>(s: *mut McSession) -> Result<&'a Session> {
    // SAFETY: the caller passes a pointer from mc_session_open_* that it has not freed.
    unsafe { s.as_ref() }.map(|s| &s.0).ok_or_else(|| Error("session is NULL".into()))
}

/// Connects to the phone's remotepairingd at `host`:`port` (an IP literal)
/// using a remote-pairing file's bytes, and opens the screen stream.
///
/// `disk_image_dir` (may be NULL) is an unpacked iOS DDI `Restore` folder,
/// mounted first when the phone has none mounted (after a restart).
/// `out_mounted` (may be NULL) is set to 1 when that happened.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_session_open_rppairing(
    host: *const c_char,
    port: u16,
    hostname: *const c_char,
    pairing: *const u8,
    pairing_len: usize,
    disk_image_dir: *const c_char,
    out: *mut *mut McSession,
    out_mounted: *mut u8,
) -> *mut c_char {
    guard(|| {
        if out.is_null() || pairing.is_null() {
            return Err(Error("NULL argument".into()));
        }
        let ip: IpAddr = unsafe { text(host, "host") }?
            .parse()
            .map_err(|_| Error("host must be an IP address".into()))?;
        let hostname = unsafe { text(hostname, "hostname") }?;
        // SAFETY: the caller passes `pairing_len` readable bytes.
        let bytes = unsafe { std::slice::from_raw_parts(pairing, pairing_len) };
        let disk_image = if disk_image_dir.is_null() {
            None
        } else {
            Some(std::path::PathBuf::from(unsafe { text(disk_image_dir, "disk image folder") }?))
        };
        let s = Session::open_rppairing(SocketAddr::new(ip, port), hostname, bytes, disk_image.as_deref())?;
        if !out_mounted.is_null() {
            unsafe { *out_mounted = s.mounted_disk_image() as u8 };
        }
        unsafe { *out = Box::into_raw(Box::new(McSession(s))) };
        Ok(())
    })
}

/// Closes the session and frees it. NULL is a no-op.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_session_close(s: *mut McSession) {
    if !s.is_null() {
        // SAFETY: the pointer came from Box::into_raw in mc_session_open_*.
        let s = unsafe { Box::from_raw(s) };
        let _ = catch_unwind(AssertUnwindSafe(|| s.0.close()));
    }
}

/// `clock`: 0 = mach_absolute_time, 1 = wall clock.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_set_clock(s: *mut McSession, clock: u8) -> *mut c_char {
    guard(|| unsafe { session(s) }?.set_clock(if clock == 1 { Clock::Wall } else { Clock::Mach }))
}

/// Finger down at (x1, y1), held `hold_ms`, moved to (x2, y2) over
/// `travel_ms`, lifted. Coordinates 0..=65535 across the screen.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_touch(
    s: *mut McSession,
    x1: u16,
    y1: u16,
    x2: u16,
    y2: u16,
    hold_ms: u32,
    travel_ms: u32,
) -> *mut c_char {
    guard(|| {
        unsafe { session(s) }?.touch(
            (x1, y1),
            (x2, y2),
            Duration::from_millis(hold_ms.into()),
            Duration::from_millis(travel_ms.into()),
        )
    })
}

/// `button`: 0 = home, 1 = lock, 2 = volume up, 3 = volume down.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_button(s: *mut McSession, button: u8) -> *mut c_char {
    guard(|| {
        let b = match button {
            0 => Button::Home,
            1 => Button::Lock,
            2 => Button::VolumeUp,
            3 => Button::VolumeDown,
            other => return Err(Error(format!("no button {other}"))),
        };
        unsafe { session(s) }?.button(b)
    })
}

/// A named key: return, enter, escape, backspace, delete, tab, space, arrows.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_key_named(s: *mut McSession, name: *const c_char) -> *mut c_char {
    guard(|| {
        let name = unsafe { text(name, "name") }?;
        let usage = keys::named(name).ok_or_else(|| Error(format!("no key named '{name}'")))?;
        unsafe { session(s) }?.key(usage, &[])
    })
}

/// Types ASCII text on the keyboard; fails on anything else.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_type_ascii(s: *mut McSession, utf8: *const c_char) -> *mut c_char {
    guard(|| unsafe { session(s) }?.type_ascii(unsafe { text(utf8, "text") }?))
}

/// Puts text on the clipboard and pastes it with Cmd+V.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_paste_text(s: *mut McSession, utf8: *const c_char) -> *mut c_char {
    guard(|| unsafe { session(s) }?.paste_text(unsafe { text(utf8, "text") }?))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_set_clipboard(s: *mut McSession, utf8: *const c_char) -> *mut c_char {
    guard(|| unsafe { session(s) }?.set_clipboard(unsafe { text(utf8, "text") }?))
}

/// Screenshot as PNG. Free with `mc_bytes_free`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_screenshot(
    s: *mut McSession,
    out: *mut *mut u8,
    out_len: *mut usize,
) -> *mut c_char {
    guard(|| {
        if out.is_null() || out_len.is_null() {
            return Err(Error("NULL argument".into()));
        }
        let image = unsafe { session(s) }?.screenshot()?.into_boxed_slice();
        unsafe {
            *out_len = image.len();
            *out = Box::into_raw(image) as *mut u8;
        }
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_launch(s: *mut McSession, bundle_id: *const c_char) -> *mut c_char {
    guard(|| unsafe { session(s) }?.launch(unsafe { text(bundle_id, "bundle id") }?, false))
}

/// Like `mc_launch`, but closes a running copy first: the app starts on its home page.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_launch_fresh(s: *mut McSession, bundle_id: *const c_char) -> *mut c_char {
    guard(|| unsafe { session(s) }?.launch(unsafe { text(bundle_id, "bundle id") }?, true))
}

/// `[{"name", "bundle_id"}]` JSON. Free with `mc_string_free`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_list_apps(s: *mut McSession, out_json: *mut *mut c_char) -> *mut c_char {
    guard(|| {
        if out_json.is_null() {
            return Err(Error("NULL argument".into()));
        }
        let json = unsafe { session(s) }?.list_apps_json()?;
        unsafe { *out_json = owned_c_string(json) };
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_string_free(s: *mut c_char) {
    if !s.is_null() {
        // SAFETY: every string this library hands out comes from CString::into_raw.
        drop(unsafe { CString::from_raw(s) });
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn mc_bytes_free(ptr: *mut u8, len: usize) {
    if !ptr.is_null() {
        // SAFETY: from Box<[u8]>::into_raw in mc_screenshot, with its length.
        drop(unsafe { Box::from_raw(std::ptr::slice_from_raw_parts_mut(ptr, len)) });
    }
}
