package gg.agit.konect.domain.club.service;

import static gg.agit.konect.domain.club.enums.ClubPositionGroup.PRESIDENT;
import static gg.agit.konect.global.code.ApiResponseCode.ALREADY_EXIST_CLUB_RECRUITMENT;
import static gg.agit.konect.global.code.ApiResponseCode.FORBIDDEN_CLUB_MANAGER_ACCESS;
import static gg.agit.konect.global.code.ApiResponseCode.FORBIDDEN_CLUB_RECRUITMENT_CREATE;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gg.agit.konect.domain.club.dto.ClubRecruitmentCreateRequest;
import gg.agit.konect.domain.club.dto.ClubRecruitmentResponse;
import gg.agit.konect.domain.club.dto.ClubRecruitmentUpdateRequest;
import gg.agit.konect.domain.club.enums.ClubPositionGroup;
import gg.agit.konect.domain.club.model.Club;
import gg.agit.konect.domain.club.model.ClubRecruitment;
import gg.agit.konect.domain.club.model.ClubRecruitmentImage;
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
public class ClubRecruitmentService {

    private static final Set<ClubPositionGroup> PRESIDENT_ALLOWED_GROUPS =
        EnumSet.of(PRESIDENT);
    private static final Set<ClubPositionGroup> MANAGER_ALLOWED_GROUPS =
        EnumSet.of(ClubPositionGroup.PRESIDENT, ClubPositionGroup.VICE_PRESIDENT);

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubApplyRepository clubApplyRepository;
    private final ClubRecruitmentRepository clubRecruitmentRepository;

    public ClubRecruitmentResponse getRecruitment(Integer clubId, Integer userId) {
        Club club = clubRepository.getById(clubId);
        User user = userRepository.getById(userId);
        ClubRecruitment recruitment = clubRecruitmentRepository.getByClubId(club.getId());
        boolean isMember = clubMemberRepository.existsByClubIdAndUserId(clubId, userId);
        boolean isApplied = isMember || clubApplyRepository.existsByClubIdAndUserId(club.getId(), user.getId());

        return ClubRecruitmentResponse.of(recruitment, isApplied);
    }

    @Transactional
    public void createRecruitment(Integer clubId, Integer userId, ClubRecruitmentCreateRequest request) {
        Club club = clubRepository.getById(clubId);
        userRepository.getById(userId);

        if (!hasClubManageAccess(clubId, userId, PRESIDENT_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_RECRUITMENT_CREATE);
        }

        if (clubRecruitmentRepository.existsByClubId(clubId)) {
            throw CustomException.of(ALREADY_EXIST_CLUB_RECRUITMENT);
        }

        ClubRecruitment clubRecruitment = ClubRecruitment.of(
            request.startDate(),
            request.endDate(),
            request.isAlwaysRecruiting(),
            request.content(),
            club
        );
        List<String> imageUrls = request.getImageUrls();
        for (int index = 0; index < imageUrls.size(); index++) {
            ClubRecruitmentImage clubRecruitmentImage = ClubRecruitmentImage.of(
                imageUrls.get(index),
                index,
                clubRecruitment
            );
            clubRecruitment.addImage(clubRecruitmentImage);
        }

        clubRecruitmentRepository.save(clubRecruitment);
    }

    @Transactional
    public void updateRecruitment(Integer clubId, Integer userId, ClubRecruitmentUpdateRequest request) {
        clubRepository.getById(clubId);
        userRepository.getById(userId);

        if (!hasClubManageAccess(clubId, userId, MANAGER_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_MANAGER_ACCESS);
        }

        ClubRecruitment clubRecruitment = clubRecruitmentRepository.getByClubId(clubId);
        clubRecruitment.update(
            request.startDate(),
            request.endDate(),
            request.isAlwaysRecruiting(),
            request.content()
        );

        clubRecruitment.getImages().clear();
        List<String> imageUrls = request.getImageUrls();
        for (int index = 0; index < imageUrls.size(); index++) {
            ClubRecruitmentImage newImage = ClubRecruitmentImage.of(
                imageUrls.get(index),
                index,
                clubRecruitment
            );
            clubRecruitment.addImage(newImage);
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
