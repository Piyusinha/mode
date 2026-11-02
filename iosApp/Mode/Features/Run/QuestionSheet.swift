import ModeDesignSystem
import SwiftUI

/// The agent's question: a yes/no before a risky tap, a choice to tap (or type), or a free answer.
struct QuestionSheet: View {
    let question: RunStatusViewModel.PendingQuestion
    let onAnswer: (String) -> Void
    let onStop: () -> Void
    @State private var answer = ""

    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: ModeSpacing.l) {
            // The question is what matters: in full, large; "Mode needs you" is just its label.
            Label { Text(question.yourTurn ? L10n.Question.yourTurn : L10n.Question.title) } icon: {
                (question.yourTurn ? ModeIcon.lock : ModeIcon.waiting).image
            }
            .modeText(.subheadline, color: question.yourTurn ? ModeColor.warning : ModeColor.tint)
            Text(question.text).modeText(.title2).fixedSize(horizontal: false, vertical: true)
            if question.yourTurn {
                Text(L10n.Question.yourTurnNote).modeText(.callout, color: ModeColor.textSecondary)
                ModeButton(L10n.Question.entered, fullWidth: true) { onAnswer(Answer.done) }
                ModeButton(L10n.Question.stopTask, role: .destructive) { onStop() }
            } else if question.yesNo {
                ModeButton(L10n.Question.yes, fullWidth: true) { onAnswer(Answer.yes) }
                if question.allowAlways {
                    ModeButton(L10n.Question.always, role: .secondary, fullWidth: true) { onAnswer(Answer.always) }
                }
                ModeButton(L10n.Question.no, role: .secondary, fullWidth: true) { onAnswer(Answer.no) }
                if question.allowAlways {
                    Text(L10n.Question.alwaysNote).modeText(.footnote, color: ModeColor.textSecondary)
                }
            } else if !question.options.isEmpty {
                ModeOptionList(question.options, recommended: question.recommended,
                               recommendedLabel: String(localized: L10n.Question.recommended), onPick: onAnswer)
                ModeTextField(L10n.Question.somethingElse, prompt: L10n.Question.somethingElse, text: $answer)
                if !answer.isEmpty { ModeButton(L10n.Question.send, fullWidth: true) { onAnswer(answer) } }
                ModeButton(L10n.Question.stopTask, role: .destructive) { onStop() }
            } else {
                ModeTextField(L10n.Question.answerPrompt, prompt: L10n.Question.answerPrompt, text: $answer)
                ModeButton(L10n.Question.send, fullWidth: true) { onAnswer(answer) }
                ModeButton(L10n.Question.stopTask, role: .destructive) { onStop() }
            }
        }
        .padding(ModeSpacing.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.medium, .large])
        .interactiveDismissDisabled()
    }

    /// What the runner expects for a yes/no question.
    private enum Answer {
        static let yes = "yes"
        static let no = "no"
        static let always = "always"
        static let done = "done"
    }
}
