package juby.invest.domain.personality_test.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record TestQuestionList(
        List<Questions> questions
) {
    @Builder
    public record Questions(
            Long questionId,
            String content,
            List<Choices> choices
    ) {
        @Builder
        public record Choices(
                Long choiceId,
                String content,
                int score
        ) {
        }
    }
}

