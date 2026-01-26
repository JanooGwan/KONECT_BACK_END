package gg.agit.konect.domain.club.service;

import static gg.agit.konect.domain.club.enums.ClubPositionGroup.MANAGER;
import static gg.agit.konect.domain.club.enums.ClubPositionGroup.PRESIDENT;
import static gg.agit.konect.global.code.ApiResponseCode.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gg.agit.konect.domain.bank.repository.BankRepository;
import gg.agit.konect.domain.club.dto.ClubAppliedClubsResponse;
import gg.agit.konect.domain.club.dto.ClubCondition;
import gg.agit.konect.domain.club.dto.ClubCreateRequest;
import gg.agit.konect.domain.club.dto.ClubDetailResponse;
import gg.agit.konect.domain.club.dto.ClubFeeInfoReplaceRequest;
import gg.agit.konect.domain.club.dto.ClubFeeInfoResponse;
import gg.agit.konect.domain.club.dto.ClubMemberCondition;
import gg.agit.konect.domain.club.dto.ClubMembersResponse;
import gg.agit.konect.domain.club.dto.ClubMembershipsResponse;
import gg.agit.konect.domain.club.dto.ClubUpdateRequest;
import gg.agit.konect.domain.club.dto.ClubsResponse;
import gg.agit.konect.domain.club.enums.ClubPositionGroup;
import gg.agit.konect.domain.club.model.Club;
import gg.agit.konect.domain.club.model.ClubApply;
import gg.agit.konect.domain.club.model.ClubMember;
import gg.agit.konect.domain.club.model.ClubMembers;
import gg.agit.konect.domain.club.model.ClubPosition;
import gg.agit.konect.domain.club.model.ClubRecruitment;
import gg.agit.konect.domain.club.model.ClubSummaryInfo;
import gg.agit.konect.domain.club.repository.ClubApplyRepository;
import gg.agit.konect.domain.club.repository.ClubMemberRepository;
import gg.agit.konect.domain.club.repository.ClubPositionRepository;
import gg.agit.konect.domain.club.repository.ClubQueryRepository;
import gg.agit.konect.domain.club.repository.ClubRecruitmentRepository;
import gg.agit.konect.domain.club.repository.ClubRepository;
import gg.agit.konect.domain.user.model.User;
import gg.agit.konect.domain.user.repository.UserRepository;
import gg.agit.konect.global.exception.CustomException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubService {

    private static final Set<ClubPositionGroup> PRESIDENT_ALLOWED_GROUPS =
        EnumSet.of(PRESIDENT);
    private static final Set<ClubPositionGroup> MANAGER_ALLOWED_GROUPS =
        EnumSet.of(PRESIDENT, MANAGER);

    private final ClubQueryRepository clubQueryRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubPositionRepository clubPositionRepository;
    private final ClubRecruitmentRepository clubRecruitmentRepository;
    private final ClubApplyRepository clubApplyRepository;
    private final UserRepository userRepository;
    private final BankRepository bankRepository;

    public ClubsResponse getClubs(ClubCondition condition, Integer userId) {
        User user = userRepository.getById(userId);
        PageRequest pageable = PageRequest.of(condition.page() - 1, condition.limit());
        Page<ClubSummaryInfo> clubSummaryInfoPage = clubQueryRepository.findAllByFilter(
            pageable, condition.query(), condition.isRecruiting(), user.getUniversity().getId()
        );

        return ClubsResponse.of(clubSummaryInfoPage);
    }

    public ClubDetailResponse getClubDetail(Integer clubId, Integer userId) {
        Club club = clubRepository.getById(clubId);
        ClubMembers clubMembers = ClubMembers.from(clubMemberRepository.findAllByClubId(club.getId()));

        List<ClubMember> clubPresidents = clubMembers.getPresidents();
        Integer memberCount = clubMembers.getCount();
        ClubRecruitment recruitment = club.getClubRecruitment();

        boolean isMember = clubMembers.contains(userId);
        Boolean isApplied = isMember || clubApplyRepository.existsByClubIdAndUserId(clubId, userId);

        return ClubDetailResponse.of(club, memberCount, recruitment, clubPresidents, isMember, isApplied);
    }

    @Transactional
    public ClubDetailResponse createClub(Integer userId, ClubCreateRequest request) {
        User user = userRepository.getById(userId);
        Club club = request.toEntity(user.getUniversity());

        Club savedClub = clubRepository.save(club);

        List<ClubPosition> defaultPositions = Arrays.stream(ClubPositionGroup.values())
            .map(group -> ClubPosition.builder()
                .name(group.getDescription())
                .clubPositionGroup(group)
                .club(savedClub)
                .build())
            .toList();

        defaultPositions.forEach(clubPositionRepository::save);

        ClubPosition presidentPosition = defaultPositions.get(0);
        ClubMember president = ClubMember.builder()
            .club(savedClub)
            .user(user)
            .clubPosition(presidentPosition)
            .isFeePaid(false)
            .build();

        clubMemberRepository.save(president);

        return getClubDetail(savedClub.getId(), userId);
    }

    @Transactional
    public ClubDetailResponse updateClub(Integer clubId, Integer userId, ClubUpdateRequest request) {
        userRepository.getById(userId);
        Club club = clubRepository.getById(clubId);

        if (!hasClubManageAccess(clubId, userId, MANAGER_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_MANAGER_ACCESS);
        }

        club.update(
            request.name(),
            request.description(),
            request.imageUrl(),
            request.location(),
            request.clubCategory(),
            request.introduce()
        );

        return getClubDetail(clubId, userId);
    }

    public ClubMembershipsResponse getJoinedClubs(Integer userId) {
        List<ClubMember> clubMembers = clubMemberRepository.findAllByUserId(userId);
        return ClubMembershipsResponse.from(clubMembers);
    }

    public ClubMembershipsResponse getManagedClubs(Integer userId) {
        List<ClubMember> clubMembers = clubMemberRepository.findAllByUserIdAndClubPosition(userId, PRESIDENT);
        return ClubMembershipsResponse.from(clubMembers);
    }

    public ClubAppliedClubsResponse getAppliedClubs(Integer userId) {
        List<ClubApply> clubApplies = clubApplyRepository.findAllPendingByUserIdWithClub(userId);
        return ClubAppliedClubsResponse.from(clubApplies);
    }

    public ClubMembersResponse getClubMembers(Integer clubId, Integer userId, ClubMemberCondition condition) {
        boolean isMember = clubMemberRepository.existsByClubIdAndUserId(clubId, userId);
        if (!isMember) {
            throw CustomException.of(FORBIDDEN_CLUB_MEMBER_ACCESS);
        }

        List<ClubMember> clubMembers;
        if (condition != null && condition.positionGroup() != null) {
            clubMembers = clubMemberRepository.findAllByClubIdAndPositionGroup(clubId, condition.positionGroup());
        } else {
            clubMembers = clubMemberRepository.findAllByClubId(clubId);
        }

        return ClubMembersResponse.from(clubMembers);
    }

    public ClubFeeInfoResponse getFeeInfo(Integer clubId, Integer userId) {
        Club club = clubRepository.getById(clubId);
        userRepository.getById(userId);

        boolean isApplied = clubApplyRepository.existsByClubIdAndUserId(clubId, userId);
        boolean isManager = clubMemberRepository.existsByClubIdAndUserIdAndPositionGroupIn(
            clubId,
            userId,
            MANAGER_ALLOWED_GROUPS
        );

        if (!isApplied && !isManager) {
            throw CustomException.of(FORBIDDEN_CLUB_FEE_INFO);
        }

        return ClubFeeInfoResponse.from(club);
    }

    @Transactional
    public ClubFeeInfoResponse replaceFeeInfo(Integer clubId, Integer userId, ClubFeeInfoReplaceRequest request) {
        userRepository.getById(userId);
        Club club = clubRepository.getById(clubId);

        if (!hasClubManageAccess(clubId, userId, MANAGER_ALLOWED_GROUPS)) {
            throw CustomException.of(FORBIDDEN_CLUB_MANAGER_ACCESS);
        }

        String bankName = bankRepository.getById(request.bankId()).getName();

        club.replaceFeeInfo(
            request.amount(),
            bankName,
            request.accountNumber(),
            request.accountHolder(),
            request.deadLine()
        );

        return ClubFeeInfoResponse.from(club);
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
