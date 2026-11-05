import SwiftUI

/// A choice of a few options in a raised-grey capsule, the chosen one on a white pill with a soft lift
/// (Tasks' Now / Scheduled / Done; [compact] for Repeat's Every day / Weekdays / Weekends / Once).
public struct ModePillSegments<Option: Hashable>: View {
    private let options: [(Option, LocalizedStringResource)]
    @Binding private var selection: Option
    private let compact: Bool
    @Namespace private var pill

    public init(_ options: [(Option, LocalizedStringResource)], selection: Binding<Option>, compact: Bool = false) {
        self.options = options
        self._selection = selection
        self.compact = compact
    }

    public var body: some View {
        HStack(spacing: compact ? ModeSpacing.xxs : ModeSize.segmentsInset) {
            ForEach(options, id: \.0) { option, title in
                let selected = option == selection
                Button {
                    withAnimation(ModeMotion.quick) { selection = option }
                } label: {
                    Text(title)
                        .font(font(selected))
                        .foregroundStyle(selected || compact ? ModeColor.textPrimary : ModeColor.textSecondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            if selected {
                                Capsule().fill(ModeColor.card).modeShadow(in: Capsule()).matchedGeometryEffect(id: "pill", in: pill)
                            }
                        }
                        .contentShape(.capsule)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(ModeSize.segmentsInset)
        .frame(height: compact ? ModeSize.segmentsCompactHeight : ModeSize.segmentsHeight)
        .background(ModeColor.cardRaised, in: .capsule)
    }

    private func font(_ selected: Bool) -> Font {
        if compact { return .system(size: 14, weight: selected ? .semibold : .medium) }
        return .subheadline.weight(selected ? .bold : .medium)
    }
}

/// The running task on Tasks: Mo at work, the task, what it's doing and for how long, and a bar per plan item.
public struct ModeRunningCard: View {
    private let title: String
    private let status: String
    private let since: Date?
    private let paused: Bool
    private let progress: (done: Int, total: Int)?
    private let action: () -> Void

    public init(title: String, status: String, since: Date?, paused: Bool, progress: (done: Int, total: Int)?, action: @escaping () -> Void) {
        self.title = title
        self.status = status
        self.since = since
        self.paused = paused
        self.progress = progress
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
                HStack(spacing: ModeSpacing.m) {
                    ModeMo(paused ? .sleep : .working, size: ModeSize.moCard)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(title).modeText(.calloutEmphasis).lineLimit(2)
                        subtitle.modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1).monospacedDigit()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    ModeChevron()
                }
                if let progress, progress.total > 0 {
                    HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs / 2) {
                        ForEach(0..<progress.total, id: \.self) { index in
                            Capsule().fill(index < progress.done ? ModeColor.brand : ModeColor.track).frame(height: ModeSize.taskBar)
                        }
                    }
                    .padding(.leading, ModeSize.taskBarInset)
                    .accessibilityHidden(true)
                }
            }
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.m)
            .frame(minHeight: ModeSize.taskCardMin)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
            .contentShape(.rect(cornerRadius: ModeRadius.card))
        }
        .buttonStyle(ModePressStyle())
    }

    private var subtitle: Text {
        guard let since, !paused else { return Text(status) }
        return Text("\(status) · \(since, style: .timer)")
    }
}

/// A task waiting for the user, on Tasks: Mo with a "needs you" badge on a soft blue card, and Open.
public struct ModeWaitingCard: View {
    private let title: String
    private let subtitle: String
    private let actionTitle: LocalizedStringResource
    private let action: () -> Void

    public init(title: String, subtitle: String, actionTitle: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(.yourTurn, size: ModeSize.moCard)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.calloutEmphasis).lineLimit(2)
                Text(subtitle).modeText(.footnote, color: ModeColor.infoText).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: action) {
                Text(actionTitle)
                    .modeText(.subheadlineEmphasis, color: ModeColor.onInfo)
                    .padding(.horizontal, ModeSpacing.l)
                    .frame(height: ModeSize.pillButton)
                    .background(ModeColor.info, in: .capsule)
                    .frame(minHeight: ModeSize.touch)
                    .contentShape(.rect)
            }
            .buttonStyle(ModePressStyle())
        }
        .padding(.leading, ModeSpacing.l)
        .padding(.trailing, ModeSpacing.m)
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.taskCardMin)
        .background(ModeColor.infoSoft, in: .rect(cornerRadius: ModeRadius.card))
    }
}

/// A page's header with Mo beside it (All screens): Bricolage 28 heavy and a line under it.
public struct ModeScreensHeader: View {
    private let mood: ModeMo.Mood
    private let title: String
    private let subtitle: String

    public init(mood: ModeMo.Mood, title: String, subtitle: String) {
        self.mood = mood
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(mood, size: ModeSize.moPanel)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title)
                    .font(ModeTypography.display(size: 28, weight: 800, relativeTo: .title))
                    .tracking(-28 * 0.02)
                    .foregroundStyle(ModeColor.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text(subtitle).font(.system(size: 14)).foregroundStyle(ModeColor.textSecondary).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// One screen on All screens: the screenshot on a white card with its number, and what Mode did under it.
public struct ModeScreenCell: View {
    private let number: String
    private let label: String
    private let image: Image?

    public init(number: String, label: String, image: Image?) {
        self.number = number
        self.label = label
        self.image = image
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s - ModeSpacing.xxs) {
            ModeColor.card
                .frame(height: ModeSize.screenCell)
                .overlay(alignment: .top) {
                    image?.resizable().scaledToFill()
                }
                .clipShape(.rect(cornerRadius: ModeRadius.screenCell))
                .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.screenCell))
                .overlay(alignment: .topLeading) {
                    Text(number)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(ModeColor.textPrimary)
                        .padding(.horizontal, ModeSpacing.s - ModeSpacing.xxs)
                        .frame(minWidth: ModeSize.screenBadge, minHeight: ModeSize.screenBadge)
                        .background(ModeColor.cardRaised, in: .capsule)
                        .padding(ModeSpacing.s - ModeSpacing.xxs)
                }
            Text(label).modeText(.caption, color: ModeColor.textSecondary).lineLimit(1)
        }
        .contentShape(.rect)
    }
}

/// "● Live · step 7 · 0:41", the white label at the top of the live run.
public struct ModeRunBadge: View {
    private let text: String
    private let since: Date?
    private let paused: Bool

    /// [text] such as "Live · step 7 ·"; [since] adds a ticking timer.
    public init(_ text: String, since: Date?, paused: Bool) {
        self.text = text
        self.since = since
        self.paused = paused
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            Circle().fill(paused ? ModeColor.textSecondary : ModeColor.brand).frame(width: ModeSize.liveDot, height: ModeSize.liveDot)
            Group {
                if let since, !paused { Text("\(text) \(since, style: .timer)") } else { Text(text) }
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(ModeColor.textPrimary)
            .monospacedDigit()
            .lineLimit(1)
        }
        .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
        .frame(height: ModeSize.liveBadgeHeight)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
    }
}

/// The white panel at the bottom of the live run: the task, what Mode is doing now, a bar per step
/// with its label (the current one in orange), Pause and Stop.
public struct ModeRunPanel: View {
    private let task: String
    private let now: String
    private let segments: [ModeSegments.Segment]
    private let paused: Bool
    private let pauseLabel: LocalizedStringResource
    private let resumeLabel: LocalizedStringResource
    private let stopLabel: LocalizedStringResource
    private let onPauseToggle: () -> Void
    private let onStop: () -> Void

    public init(task: String, now: String, segments: [ModeSegments.Segment], paused: Bool, pauseLabel: LocalizedStringResource,
                resumeLabel: LocalizedStringResource, stopLabel: LocalizedStringResource, onPauseToggle: @escaping () -> Void, onStop: @escaping () -> Void) {
        self.task = task
        self.now = now
        self.segments = segments
        self.paused = paused
        self.pauseLabel = pauseLabel
        self.resumeLabel = resumeLabel
        self.stopLabel = stopLabel
        self.onPauseToggle = onPauseToggle
        self.onStop = onStop
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            Capsule().fill(ModeColor.ring).frame(width: ModeSize.grabberWidth, height: ModeSize.grabberHeight).frame(maxWidth: .infinity)
            HStack(spacing: ModeSpacing.m) {
                ModeMo(paused ? .sleep : .working, size: ModeSize.moPanel)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(task).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
                    Text(now).font(ModeTypography.display(size: 21, weight: 700, relativeTo: .title3)).tracking(-21 * 0.02)
                        .foregroundStyle(ModeColor.textPrimary).lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !segments.isEmpty {
                HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        Capsule().fill(segment.state == .done ? ModeColor.brand : ModeColor.track).frame(height: ModeSize.progressBar)
                    }
                }
                .accessibilityHidden(true)
                HStack(spacing: ModeSpacing.s) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { index, segment in
                        Text(segment.label)
                            .font(.caption.weight(segment.state == .now ? .semibold : .regular))
                            .foregroundStyle(segment.state == .now ? ModeColor.tint : ModeColor.textSecondary)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: alignment(index))
                    }
                }
            }
            Spacer(minLength: ModeSpacing.s)
            HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                Button(action: onPauseToggle) {
                    Text(paused ? resumeLabel : pauseLabel).modeText(.headline)
                        .frame(maxWidth: .infinity, minHeight: ModeSize.fieldHeight)
                        .background(ModeColor.cardRaised, in: .capsule)
                        .contentShape(.capsule)
                }
                .buttonStyle(ModePressStyle())
                Button(action: onStop) {
                    Text(stopLabel).modeText(.headline, color: ModeColor.danger)
                        .frame(maxWidth: .infinity, minHeight: ModeSize.fieldHeight)
                        .background(ModeColor.tileRed, in: .capsule)
                        .contentShape(.capsule)
                }
                .buttonStyle(ModePressStyle())
            }
        }
        .padding(.top, ModeSpacing.s + ModeSpacing.xxs)
        .padding(.horizontal, ModeSpacing.xl)
        .padding(.bottom, ModeSpacing.xl)
        .frame(minHeight: ModeSize.livePanelHeight)
        .fixedSize(horizontal: false, vertical: true)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.livePanel))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.livePanel))
    }

    private func alignment(_ index: Int) -> Alignment {
        if segments.count < 2 || index == 0 { return .leading }
        return index == segments.count - 1 ? .trailing : .center
    }
}

/// Mo and a line on a white card (the top of Repeat).
public struct ModeRepeatNote: View {
    private let mood: ModeMo.Mood
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource, mood: ModeMo.Mood = .idle) {
        self.mood = mood
        self.text = text
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(mood, size: ModeSize.moCard)
            Text(text).font(.system(size: 14)).foregroundStyle(ModeColor.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
        .padding(.vertical, ModeSpacing.s + ModeSpacing.xxs)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.note))
    }
}

/// A sheet's own top bar: Cancel in a white capsule, the title, and the ink Save.
public struct ModeRepeatHeader: View {
    private let title: LocalizedStringResource
    private let cancelTitle: LocalizedStringResource
    private let saveTitle: LocalizedStringResource
    private let canSave: Bool
    private let onCancel: () -> Void
    private let onSave: () -> Void

    public init(_ title: LocalizedStringResource, cancelTitle: LocalizedStringResource, saveTitle: LocalizedStringResource, canSave: Bool,
                onCancel: @escaping () -> Void, onSave: @escaping () -> Void) {
        self.title = title
        self.cancelTitle = cancelTitle
        self.saveTitle = saveTitle
        self.canSave = canSave
        self.onCancel = onCancel
        self.onSave = onSave
    }

    public var body: some View {
        ZStack {
            Text(title).modeText(.section).accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: onCancel) {
                    Text(cancelTitle).modeText(.callout)
                        .padding(.horizontal, ModeSpacing.l)
                        .frame(height: ModeSize.touch)
                        .background(ModeColor.card, in: .capsule)
                        .modeShadow(in: Capsule())
                }
                .buttonStyle(ModePressStyle())
                Spacer()
                Button(action: onSave) {
                    Text(saveTitle).modeText(.calloutEmphasis, color: ModeColor.onAccent)
                        .padding(.horizontal, ModeSpacing.l + ModeSpacing.xxs)
                        .frame(height: ModeSize.touch)
                        .background(ModeColor.accent, in: .capsule)
                }
                .buttonStyle(ModePressStyle())
                .disabled(!canSave)
                .opacity(canSave ? 1 : 0.45)
            }
        }
    }
}

/// A time as one wheel with two columns, hours 0–23 and minutes, three rows tall on a white card (Repeat):
/// the chosen row in Bricolage, the others in secondary text.
public struct ModeTimeWheel: View {
    @Binding private var hour: Int
    @Binding private var minute: Int

    public init(hour: Binding<Int>, minute: Binding<Int>) {
        self._hour = hour
        self._minute = minute
    }

    public var body: some View {
        ModeTimePicker(hour: $hour, minute: $minute)
            .frame(height: ModeSize.timeWheelPicker)
            .frame(height: ModeSize.timeWheel)
            .frame(maxWidth: .infinity)
            .clipped()
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.xs + ModeSpacing.xxs)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }
}

private struct ModeTimePicker: UIViewRepresentable {
    @Binding var hour: Int
    @Binding var minute: Int

    func makeUIView(context: Context) -> UIPickerView {
        let picker = UIPickerView()
        picker.dataSource = context.coordinator
        picker.delegate = context.coordinator
        picker.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        picker.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return picker
    }

    func updateUIView(_ picker: UIPickerView, context: Context) {
        context.coordinator.parent = self
        if picker.selectedRow(inComponent: 0) != hour { picker.selectRow(hour, inComponent: 0, animated: false) }
        if picker.selectedRow(inComponent: 1) != minute { picker.selectRow(minute, inComponent: 1, animated: false) }
        picker.reloadAllComponents()
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UIPickerViewDataSource, UIPickerViewDelegate {
        var parent: ModeTimePicker

        init(parent: ModeTimePicker) { self.parent = parent }

        func numberOfComponents(in pickerView: UIPickerView) -> Int { 2 }

        func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int { component == 0 ? 24 : 60 }

        func pickerView(_ pickerView: UIPickerView, rowHeightForComponent component: Int) -> CGFloat { ModeSize.timeWheelRow }

        func pickerView(_ pickerView: UIPickerView, widthForComponent component: Int) -> CGFloat { ModeSize.timeWheelColumn }

        func pickerView(_ pickerView: UIPickerView, viewForRow row: Int, forComponent component: Int, reusing view: UIView?) -> UIView {
            let label = (view as? UILabel) ?? UILabel()
            let selected = row == (component == 0 ? parent.hour : parent.minute)
            label.text = component == 0 ? String(row) : String(format: "%02d", row)
            label.textAlignment = .center
            label.font = selected ? ModeTypography.uiDisplay(size: 26, weight: 700, textStyle: .title2) : .systemFont(ofSize: 20)
            label.textColor = selected ? UIColor(ModeColor.textPrimary) : UIColor(ModeColor.textSecondary)
            return label
        }

        func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
            if component == 0 { parent.hour = row } else { parent.minute = row }
            pickerView.reloadComponent(component)
        }
    }
}

/// A suggested task to try (Tasks, nothing yet): a dot in Mo's colour and the task, on a capsule.
public struct ModeTryRow: View {
    private let text: String
    private let action: () -> Void

    public init(_ text: String, action: @escaping () -> Void) {
        self.text = text
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                Circle().fill(ModeColor.brand).frame(width: ModeSize.tryDot, height: ModeSize.tryDot)
                Text(text).modeText(.calloutEmphasis).multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, ModeSpacing.l + ModeSpacing.xxs)
            .padding(.vertical, ModeSpacing.m)
            .frame(minHeight: ModeSize.tryRow)
            .background(ModeColor.card, in: .capsule)
        }
        .buttonStyle(ModePressStyle())
    }
}
