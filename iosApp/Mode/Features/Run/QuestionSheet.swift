import ModeDesignSystem
import SwiftUI

/// The agent's question: a yes/no before a risky tap, or a free answer.
struct QuestionSheet: View {
    let question: RunStatusViewModel.PendingQuestion
    let onAnswer: (String) -> Void
    let onStop: () -> Void
    @State private var answer = ""

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.l) {
            ModeHero(icon: .waiting, title: L10n.Question.title, message: LocalizedStringResource(stringLiteral: question.text))
            if question.yesNo {
                ModeButton(L10n.Question.allow, fullWidth: true) { onAnswer(Answer.yes) }
                ModeButton(L10n.Question.dontAllow, role: .secondary, fullWidth: true) { onAnswer(Answer.no) }
            } else {
                ModeTextField(L10n.Question.answerPrompt, prompt: L10n.Question.answerPrompt, text: $answer)
                ModeButton(L10n.Question.send, fullWidth: true) { onAnswer(answer) }
                ModeButton(L10n.Question.stopTask, role: .destructive) { onStop() }
            }
        }
        .padding(ModeSpacing.xl)
        .presentationDetents([.medium, .large])
        .interactiveDismissDisabled()
    }

    /// What the runner expects for a yes/no question.
    private enum Answer {
        static let yes = "yes"
        static let no = "no"
    }
}
