package gg.agit.konect.domain.club.service;

import static gg.agit.konect.global.code.ApiResponseCode.ALREADY_APPLIED_CLUB;
import static gg.agit.konect.global.code.ApiResponseCode.DUPLICATE_CLUB_APPLY_QUESTION;
import static gg.agit.konect.global.code.ApiResponseCode.FORBIDDEN_CLUB_MANAGER_ACCESS;
import static gg.agit.konect.global.code.ApiResponseCode.NOT_FOUND_CLUB_APPLY_QUESTION;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gg.agit.konect.domain.club.dto.ClubApplicationAnswersResponse;
import gg.agit.konect.domain.club.dto.ClubApplicationsResponse;
import gg.agit.konect.domain.club.dto.ClubApplyQuestionsReplaceRequest;
import gg.agit.konect.domain.club.dto.ClubApplyQuestionsResponse;
import gg.agit.konect.domain.club.dto.ClubApplyRequest;
import gg.agit.konect.domain.club.dto.ClubFeeInfoResponse;
import gg.agit.konect.domain.club.enums.ClubPositionGroup;
import gg.agit.konect.domain.club.model.Club;
import gg.agit.konect.domain.club.model.ClubApply;
import gg.agit.konect.domain.club.model.ClubApplyAnswer;
import gg.agit.konect.domain.club.model.ClubApplyQuestion;
import gg.agit.konect.domain.club.model.ClubApplyQuestionAnswers;
import gg.agit.konect.domain.club.model.ClubRecruitment;
import gg.agit.konect.domain.club.repository.ClubApplyAnswerRepository;
import gg.agit.konect.domain.club.repository.ClubApplyQuestionRepository;
import gg.agit.konect.domain.club.repository.ClubApplyRepository;
import gg.agit.konect.domain.club.repository.ClubMemberRepository;
import gg.agit.konect.domain.club.repository.ClubRecruitmentRepository;
import gg.agit.konect.domain.club.repository.ClubRepository;
import gg.agit.konect.domain.user.model.User;
import gg.agit.konect.domain.user.repository.UserRepository;
import gg.agit.konect.global.exception.CustomException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubApplyService {

    private static final Set<ClubPositionGroup> MANAGER_ALLOWED_GROUPS =
        EnumSet.of(ClubPositionGroup.PRESIDENT, ClubPositionGroup.VICE_PRESIDENT);

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubApplyRepository clubApplyRepository;
    private final ClubApplyQuestionRepository clubApplyQuestionRepository;
    private final ClubApplyAnswerRepository clubApplyAnswerRepository;
    private final ClubRecruitmentRepository clubRecruitmentRepository;

    public ClubApplicationsResponse getClubApplications(Integer clubId, Integer userId) {
        clubRepository.getById(clubId);

        if (!hasClubManageAccess(clubId, userId, MANAGER_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_MANAGER_ACCESS);
        }

        ClubRecruitment recruitment = clubRecruitmentRepository.getByClubId(clubId);
        List<ClubApply> clubApplies = findApplicationsByRecruitmentPeriod(clubId, recruitment);

        return ClubApplicationsResponse.from(clubApplies);
    }

    public ClubApplicationAnswersResponse getClubApplicationAnswers(
        Integer clubId,
        Integer applicationId,
        Integer userId
    ) {
        clubRepository.getById(clubId);

        if (!hasClubManageAccess(clubId, userId, MANAGER_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_MANAGER_ACCESS);
        }

        ClubApply clubApply = clubApplyRepository.getByIdAndClubId(applicationId, clubId);
        List<ClubApplyQuestion> questions =
            clubApplyQuestionRepository.findAllByClubIdOrderByIdAsc(clubId);
        List<ClubApplyAnswer> answers = clubApplyAnswerRepository.findAllByApplyIdWithQuestion(applicationId);

        return ClubApplicationAnswersResponse.of(clubApply, questions, answers);
    }

    public ClubApplyQuestionsResponse getApplyQuestions(Integer clubId, Integer userId) {
        userRepository.getById(userId);
        List<ClubApplyQuestion> questions =
            clubApplyQuestionRepository.findAllByClubIdOrderByIdAsc(clubId);

        return ClubApplyQuestionsResponse.from(questions);
    }

    @Transactional
    public ClubApplyQuestionsResponse replaceApplyQuestions(
        Integer clubId,
        Integer userId,
        ClubApplyQuestionsReplaceRequest request
    ) {
        Club club = clubRepository.getById(clubId);

        if (!hasClubManageAccess(clubId, userId, MANAGER_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_MANAGER_ACCESS);
        }

        List<ClubApplyQuestionsReplaceRequest.ApplyQuestionRequest> questionRequests = request.questions();
        Set<Integer> requestedQuestionIds = new HashSet<>();

        List<ClubApplyQuestion> existingQuestions =
            clubApplyQuestionRepository.findAllByClubIdOrderByIdAsc(clubId);
        Map<Integer, ClubApplyQuestion> existingQuestionMap = existingQuestions.stream()
            .collect(Collectors.toMap(ClubApplyQuestion::getId, question -> question));

        updateQuestions(existingQuestionMap, questionRequests, requestedQuestionIds);

        List<ClubApplyQuestion> questionsToCreate = createQuestions(club, questionRequests);

        deleteQuestions(existingQuestions, requestedQuestionIds);

        if (!questionsToCreate.isEmpty()) {
            clubApplyQuestionRepository.saveAll(questionsToCreate);
        }

        List<ClubApplyQuestion> questions =
            clubApplyQuestionRepository.findAllByClubIdOrderByIdAsc(clubId);

        return ClubApplyQuestionsResponse.from(questions);
    }

    @Transactional
    public ClubFeeInfoResponse applyClub(Integer clubId, Integer userId, ClubApplyRequest request) {
        Club club = clubRepository.getById(clubId);
        User user = userRepository.getById(userId);

        if (clubApplyRepository.existsByClubIdAndUserId(clubId, userId)) {
            throw CustomException.of(ALREADY_APPLIED_CLUB);
        }

        List<ClubApplyQuestion> questions =
            clubApplyQuestionRepository.findAllByClubIdOrderByIdAsc(clubId);
        ClubApplyQuestionAnswers answers = ClubApplyQuestionAnswers.of(questions, request.toAnswerMap());

        ClubApply apply = clubApplyRepository.save(ClubApply.of(club, user));

        List<ClubApplyAnswer> applyAnswers = answers.toEntities(apply);

        if (!applyAnswers.isEmpty()) {
            clubApplyAnswerRepository.saveAll(applyAnswers);
        }

        return ClubFeeInfoResponse.from(club);
    }

    private List<ClubApply> findApplicationsByRecruitmentPeriod(
        Integer clubId,
        ClubRecruitment recruitment
    ) {
        if (recruitment.getIsAlwaysRecruiting()) {
            return clubApplyRepository.findAllByClubIdWithUser(clubId);
        }

        LocalDateTime startDateTime = recruitment.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = recruitment.getEndDate().atTime(LocalTime.MAX);

        return clubApplyRepository.findAllByClubIdAndCreatedAtBetweenWithUser(
            clubId,
            startDateTime,
            endDateTime
        );
    }

    private List<ClubApplyQuestion> createQuestions(
        Club club,
        List<ClubApplyQuestionsReplaceRequest.ApplyQuestionRequest> questionRequests
    ) {
        List<ClubApplyQuestion> questionsToCreate = new ArrayList<>();

        for (ClubApplyQuestionsReplaceRequest.ApplyQuestionRequest questionRequest : questionRequests) {
            if (questionRequest.questionId() != null) {
                continue;
            }

            questionsToCreate.add(ClubApplyQuestion.of(
                club,
                questionRequest.question(),
                questionRequest.isRequired())
            );
        }
        return questionsToCreate;
    }

    private void updateQuestions(
        Map<Integer, ClubApplyQuestion> existingQuestionMap,
        List<ClubApplyQuestionsReplaceRequest.ApplyQuestionRequest> questionRequests,
        Set<Integer> requestedQuestionIds
    ) {
        for (ClubApplyQuestionsReplaceRequest.ApplyQuestionRequest questionRequest : questionRequests) {
            Integer questionId = questionRequest.questionId();

            if (questionId == null) {
                continue;
            }

            if (!requestedQuestionIds.add(questionId)) {
                throw CustomException.of(DUPLICATE_CLUB_APPLY_QUESTION);
            }

            ClubApplyQuestion existingQuestion = existingQuestionMap.get(questionId);

            if (existingQuestion == null) {
                throw CustomException.of(NOT_FOUND_CLUB_APPLY_QUESTION);
            }

            existingQuestion.update(
                questionRequest.question(),
                questionRequest.isRequired()
            );
        }
    }

    private void deleteQuestions(
        List<ClubApplyQuestion> existingQuestions,
        Set<Integer> requestedQuestionIds
    ) {
        List<ClubApplyQuestion> questionsToDelete = existingQuestions.stream()
            .filter(question -> !requestedQuestionIds.contains(question.getId()))
            .toList();

        if (!questionsToDelete.isEmpty()) {
            clubApplyQuestionRepository.deleteAll(questionsToDelete);
        }
    }

    private boolean hasClubManageAccess(
        Integer clubId,
        Integer userId,
        Set<ClubPositionGroup> allowedGroups
    ) {
        return clubMemberRepository.existsByClubIdAndUserIdAndPositionGroupIn(
            clubId,
            userId,
            allowedGroups
        );
    }
}
