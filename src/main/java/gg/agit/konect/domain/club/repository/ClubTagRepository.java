package gg.agit.konect.domain.club.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import gg.agit.konect.domain.club.model.ClubTag;
import gg.agit.konect.global.code.ApiResponseCode;
import gg.agit.konect.global.exception.CustomException;

public interface ClubTagRepository extends Repository<ClubTag, Integer> {

    Optional<ClubTag> findById(Integer id);

    default ClubTag getById(Integer id) {
        return findById(id)
            .orElseThrow(() -> CustomException.of(ApiResponseCode.NOT_FOUND_CLUB_TAG));
    }

    List<ClubTag> findAll();

    List<ClubTag> findAllByIdIn(List<Integer> ids);

    ClubTag save(ClubTag clubTag);
}
