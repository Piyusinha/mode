//! HID Keyboard/Keypad page (0x07) usages, US layout.

pub const LEFT_SHIFT: u64 = 0xE1;
/// Command.
pub const LEFT_GUI: u64 = 0xE3;
pub const V: u64 = 0x19;

/// A named key, as the `input.keys` op spells it.
pub fn named(name: &str) -> Option<u64> {
    Some(match name.to_ascii_lowercase().as_str() {
        "return" | "enter" => 0x28,
        "escape" | "esc" => 0x29,
        "backspace" | "delete" => 0x2A,
        "tab" => 0x2B,
        "space" => 0x2C,
        "right" => 0x4F,
        "left" => 0x50,
        "down" => 0x51,
        "up" => 0x52,
        _ => return None,
    })
}

/// The usage for an ASCII character and whether it needs Shift.
pub fn ascii(c: char) -> Option<(u64, bool)> {
    Some(match c {
        'a'..='z' => (0x04 + (c as u64 - 'a' as u64), false),
        'A'..='Z' => (0x04 + (c as u64 - 'A' as u64), true),
        '1'..='9' => (0x1E + (c as u64 - '1' as u64), false),
        '0' => (0x27, false),
        '\n' => (0x28, false),
        '\t' => (0x2B, false),
        ' ' => (0x2C, false),
        '!' => (0x1E, true),
        '@' => (0x1F, true),
        '#' => (0x20, true),
        '$' => (0x21, true),
        '%' => (0x22, true),
        '^' => (0x23, true),
        '&' => (0x24, true),
        '*' => (0x25, true),
        '(' => (0x26, true),
        ')' => (0x27, true),
        '-' => (0x2D, false),
        '_' => (0x2D, true),
        '=' => (0x2E, false),
        '+' => (0x2E, true),
        '[' => (0x2F, false),
        '{' => (0x2F, true),
        ']' => (0x30, false),
        '}' => (0x30, true),
        '\\' => (0x31, false),
        '|' => (0x31, true),
        ';' => (0x33, false),
        ':' => (0x33, true),
        '\'' => (0x34, false),
        '"' => (0x34, true),
        '`' => (0x35, false),
        '~' => (0x35, true),
        ',' => (0x36, false),
        '<' => (0x36, true),
        '.' => (0x37, false),
        '>' => (0x37, true),
        '/' => (0x38, false),
        '?' => (0x38, true),
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn letters_digits_and_shift() {
        assert_eq!(ascii('a'), Some((0x04, false)));
        assert_eq!(ascii('Z'), Some((0x1D, true)));
        assert_eq!(ascii('0'), Some((0x27, false)));
        assert_eq!(ascii('9'), Some((0x26, false)));
        assert_eq!(ascii('?'), Some((0x38, true)));
        assert_eq!(ascii('इ'), None);
    }

    #[test]
    fn named_keys() {
        assert_eq!(named("Return"), Some(0x28));
        assert_eq!(named("backspace"), named("delete"));
        assert_eq!(named("cmd+v"), None);
    }
}
