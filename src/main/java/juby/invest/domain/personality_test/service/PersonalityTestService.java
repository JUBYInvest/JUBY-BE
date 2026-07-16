package juby.invest.domain.personality_test.service;

import juby.invest.domain.member.entity.Member;
import juby.invest.domain.member.entity.Personality;
import juby.invest.domain.member.enums.InvestPersonality;
import juby.invest.domain.member.exception.MemberException;
import juby.invest.domain.member.exception.code.member.MemberErrorCode;
import juby.invest.domain.member.repository.MemberRepository;
import juby.invest.domain.member.repository.PersonalityRepository;
import juby.invest.domain.personality_test.dto.TestQuestionList;
import juby.invest.domain.personality_test.dto.TestResponseDto;
import juby.invest.domain.personality_test.entity.Choices;
import juby.invest.domain.personality_test.entity.PersonalityTest;
import juby.invest.domain.personality_test.exception.PersonalityTestException;
import juby.invest.domain.personality_test.exception.code.PersonalityTestErrorCode;
import juby.invest.domain.personality_test.repository.ChoiceRepository;
import juby.invest.domain.personality_test.repository.PersonalityTestRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalityTestService {

    private final PersonalityTestRepository personalityTestRepository;
    private final ChoiceRepository choiceRepository;
    private final MemberRepository memberRepository;
    private final PersonalityRepository personalityRepository;

    /***
     * 함수 기능: 성향 테스트 10개의 질문과 각 질문의 5개의 보기를 반환한다.
     * @return 10개의 질문, 각 5개의 보기
     */
    public TestQuestionList getQuestions(){

        List<PersonalityTest> personalityTests = personalityTestRepository.findAll();

        List<TestQuestionList.Questions> response = new LinkedList<>();

        for (PersonalityTest p : personalityTests) {

            List<Choices> choices = choiceRepository.findByPersonalityTest(p);
            List<TestQuestionList.Questions.Choices> list = new LinkedList<>();

            for (Choices c : choices){
                list.add(TestQuestionList.Questions.Choices.builder()
                                .choiceId(c.getId())
                                .content(c.getContent())
                                .score(c.getScore())
                        .build());
            }

            response.add(TestQuestionList.Questions.builder()
                            .questionId(p.getId())
                            .content(p.getQuestion())
                            .choices(list)
                    .build());
        }

        return TestQuestionList.builder()
                .questions(response)
                .build();
    }

    /***
     * 함수 기능: 사용자가 응답한 조사 결과를 받아, 기준에 따른 성향을 산출한다.
     * @param userId 사용자 번호
     * @param dto 응답 리스트
     * @return 성향 테스트 결과 응답 dto
     */
    @Transactional
    public TestResponseDto.TestResultRes calculatePersonality(Long userId, TestResponseDto.TestResultReq dto) {

        // 회원을 못찾을 경우 예외
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // 응답 항목 전체를 더해 값을 매긴다.
        InvestPersonality personality = getInvestPersonality(dto);

        // DB에 저장
        Personality findPersonality = personalityRepository.findByInvestPersonality(personality);
        member.updatePersonality(findPersonality);

        return TestResponseDto.TestResultRes.builder()
                .memberId(userId)
                .memberName(member.getName())
                .personalityId(findPersonality.getId())
                .personalityName(findPersonality.getInvestPersonality())
                .description(findPersonality.getDescription())
                .url(findPersonality.getPersonalityImg())
                .build();
    }

    /***
     * 함수 기능: 점수 합산에 따른 결과를 계산한다.
     * @param dto 사용자 응답 리스트
     * @return 투자 성향
     */
    private static @NonNull InvestPersonality getInvestPersonality(TestResponseDto.TestResultReq dto) {
        int sum = 0;
        for (int score : dto.scores()){
            sum += score;
        }

        InvestPersonality personality;
        if (10 <= sum && sum < 15){
            personality = InvestPersonality.안정형;
        } else if (15 <= sum && sum < 35){
            personality = InvestPersonality.안정추구형;
        } else if (35 <= sum && sum < 55){
            personality = InvestPersonality.위험중립형;
        } else if (55 <= sum && sum < 75){
            personality = InvestPersonality.적극투자형;
        } else if (75 <= sum && sum <= 90){
            personality = InvestPersonality.공격투자형;
        } else {
            throw new PersonalityTestException(PersonalityTestErrorCode.SCORE_NOT_FOUND);
        }
        return personality;
    }
}
