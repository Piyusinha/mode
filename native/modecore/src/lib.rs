//! modecore: Mode's native layer. Drives this iPhone through iOS 27's
//! CoreDevice remote-control services (the ones Xcode's Device Hub uses),
//! built on idevice. See docs/coredevice.md.

pub mod keys;
mod ffi;
mod session;

pub use session::{Button, Clock, Error, Result, Session};
