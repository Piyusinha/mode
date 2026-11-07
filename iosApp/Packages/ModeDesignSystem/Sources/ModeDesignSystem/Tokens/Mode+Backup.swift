import SwiftUI

// Sizes and colours from the "Backup and restore" board (Settings → Backup and restore, Restore, Restored).

public extension ModeSize {
    /// A row of what's in a backup on Backup and restore, and the shorter one on Restore and Restored.
    static let backupRow: CGFloat = 52
    static let backupRowCompact: CGFloat = 50
    /// "Include my AI and voice sign-ins" with its two-line note and the switch: at least 72 between its 8-point
    /// margins, as drawn.
    static let backupSwitchRow: CGFloat = 72
    /// "Restore from a backup".
    static let backupLinkRow: CGFloat = 60
    /// A row's 32-point icon tile, and its corners.
    static let backupTile: CGFloat = 32
    static let backupTileRadius: CGFloat = 10
    /// The Password / Again labels' column.
    static let backupFieldLabel: CGFloat = 96
    /// The backup file on Restore: its tile, the tile's corners, and Mo inside it.
    static let backupFileTile: CGFloat = 48
    static let backupFileTileRadius: CGFloat = 14
    static let backupFileMo: CGFloat = 34
    /// Mo on Restored.
    static let backupDoneMo: CGFloat = 88
    /// The round green tick beside what was added.
    static let backupTick: CGFloat = 26
    /// The upload arrow in Make a backup.
    static let backupUpload: CGFloat = 18
    static let backupUploadLine: CGFloat = 2.4
    /// The red ring around a password that didn't open the backup.
    static let backupRing: CGFloat = 2
}

public extension ModeRadius {
    /// The orange "locked" note inside the password card.
    static let backupNote: CGFloat = 16
}

public extension ModeSpacing {
    /// Restored: Mo's space above it, and the line's inset from the card's edges.
    static let backupDoneTop: CGFloat = 28
    static let backupDoneLineInset: CGFloat = 24
}

public extension ModeOpacity {
    /// "Restore into Mode" while it can't be pressed (a wrong password).
    static let backupDisabled: Double = 0.4
}

/// Icon sizes on the Backup boards: a row's 17-point glyph, the small lock and the match tick, the green tick.
public enum ModeBackupFont {
    public static let tile = Font.system(size: 14, weight: .semibold)
    public static let field = Font.system(size: 16, weight: .medium)
    public static let tick = Font.system(size: 11, weight: .heavy)
    public static let match = Font.system(size: 15, weight: .bold)
    public static let note = Font.system(size: 14, weight: .semibold)
}

public extension ModeColor {
    /// What Mode knows: a lilac tile and its glyph.
    static let tileLilac = Color("TileLilac", bundle: .module)
    static let tileLilacText = Color("TileLilacText", bundle: .module)
    /// A switch that's on (iOS green in light, a deeper green in dark).
    static let switchOn = Color("SwitchOn", bundle: .module)
    /// Red words under a field ("That password doesn't open this backup"), lighter than Danger in dark.
    static let dangerText = Color("DangerText", bundle: .module)
}
