//! modecore: Mode's native layer. Drives this iPhone through iOS 27's
//! CoreDevice remote-control services (the ones Xcode's Device Hub uses),
//! built on idevice. See docs/coredevice.md.

pub mod keys;
mod ffi;
mod session;
#[cfg(feature = "usb")]
mod pairing;

#[cfg(feature = "usb")]
pub use pairing::{pair_over_usb, remove_disk_image_over_usb};

pub use session::{Button, Clock, Error, Result, Session};
