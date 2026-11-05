import SwiftUI

/// Where the first run is: done steps and the current one (a wider bar) in ink, the rest as rings.
public struct ModeProgressDots: View {
    private let current: Int
    private let total: Int
    private let label: String

    public init(current: Int, total: Int, label: String) {
        self.current = current
        self.total = total
        self.label = label
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            ForEach(1...total, id: \.self) { i in
                if i <= current {
                    Capsule().fill(ModeColor.accent)
                        .frame(width: i == current ? ModeSize.stepDotCurrentV2 : ModeSize.stepDotV2, height: ModeSize.stepDotV2)
                } else {
                    Circle().strokeBorder(ModeColor.ring, lineWidth: 1.5).frame(width: ModeSize.stepDotV2, height: ModeSize.stepDotV2)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
    }
}

/// A first job to tap: a white capsule with the task and an arrow.
public struct ModeJobButton: View {
    private let title: String
    private let action: () -> Void

    public init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack {
                Text(title).modeText(.calloutEmphasis).lineLimit(1).minimumScaleFactor(ModeTextScale.title)
                Spacer(minLength: ModeSpacing.s)
                ModeIcon.arrowRight.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary)
            }
            .padding(.horizontal, ModeSpacing.xl)
            .frame(minHeight: ModeSize.jobButton)
            .background(ModeColor.card, in: .capsule)
            .modeShadow(in: Capsule())
        }
        .buttonStyle(ModePressStyle())
    }
}

/// Your own first job: a field shaped like the job buttons, with a send arrow once there are words.
public struct ModeJobField: View {
    @Binding private var text: String
    private let prompt: LocalizedStringResource
    private let sendLabel: LocalizedStringResource
    private let onSend: () -> Void
    @FocusState private var focused: Bool

    public init(text: Binding<String>, prompt: LocalizedStringResource, sendLabel: LocalizedStringResource, onSend: @escaping () -> Void) {
        self._text = text
        self.prompt = prompt
        self.sendLabel = sendLabel
        self.onSend = onSend
    }

    private var hasWords: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            TextField(text: $text) { Text(prompt).foregroundStyle(ModeColor.textSecondary) }
                .modeText(.calloutEmphasis)
                .focused($focused)
                .submitLabel(.go)
                .onSubmit { if hasWords { onSend() } }
            if hasWords {
                Button(action: onSend) {
                    ModeIcon.arrowRight.image.font(ModeIconFont.small).foregroundStyle(ModeColor.onAccent)
                        .frame(width: ModeSize.jobButton - ModeSpacing.m, height: ModeSize.jobButton - ModeSpacing.m)
                        .background(ModeColor.accent, in: .circle)
                }
                .buttonStyle(ModePressStyle())
                .accessibilityLabel(Text(sendLabel))
            }
        }
        .padding(.leading, ModeSpacing.xl)
        .padding(.trailing, ModeSpacing.s)
        .frame(minHeight: ModeSize.jobButton)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
        .contentShape(.capsule)
        .onTapGesture { focused = true }
    }
}

/// A provider to connect: its mark, its name with a tag, a line, and what to do (the first run's card).
public struct ModeProviderCard<Actions: View>: View {
    private let logo: ModeLogo
    private let title: String
    private let tag: LocalizedStringResource?
    private let detail: String
    private let warning: Bool
    private let actions: Actions

    /// [warning]: something stands in the way (the plan can't be used): an orange card with a warning mark.
    public init(logo: ModeLogo, title: String, tag: LocalizedStringResource?, detail: String, warning: Bool = false, @ViewBuilder actions: () -> Actions) {
        self.warning = warning
        self.logo = logo
        self.title = title
        self.tag = tag
        self.detail = detail
        self.actions = actions()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.groupInset) {
            HStack(spacing: ModeSpacing.m) {
                Group {
                    if warning { ModeIcon.warning.image.font(ModeIconFont.regular).foregroundStyle(ModeColor.tileOrangeText) }
                    else { logo.image.foregroundStyle(ModeColor.textPrimary) }
                }
                    .frame(width: ModeSize.providerTileV2, height: ModeSize.providerTileV2)
                    .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.levelPicker))
                VStack(alignment: .leading, spacing: ModeSpacing.xxs + 1) {
                    HStack(spacing: ModeSpacing.s) {
                        Text(title).modeText(.headline)
                        if let tag {
                            Text(tag).modeText(.captionEmphasis, color: ModeColor.tint)
                                .padding(.horizontal, ModeSpacing.s).padding(.vertical, ModeSpacing.xxs)
                                .background(ModeColor.tintSoft, in: .capsule)
                        }
                    }
                    Text(detail).modeText(.subheadline, color: ModeColor.textSecondary)
                }
            }
            actions
        }
        .padding(ModeSpacing.l)
        .background(warning ? ModeColor.tileOrange : ModeColor.card, in: .rect(cornerRadius: ModeRadius.firstRunCard))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.firstRunCard))
    }
}
