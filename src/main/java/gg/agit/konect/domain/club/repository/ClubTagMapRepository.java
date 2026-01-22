package gg.agit.konect.domain.club.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import gg.agit.konect.domain.club.model.ClubTagMap;
import gg.agit.konect.domain.club.model.ClubTagMapId;

public interface ClubTagMapRepository extends Repository<ClubTagMap, ClubTagMapId> {

    @Query("SELECT ctm FROM ClubTagMap ctm JOIN FETCH ctm.tag WHERE ctm.club.id = :clubId")
    List<ClubTagMap> findAllByClubId(@Param("clubId") Integer clubId);

    void deleteByClubId(Integer clubId);

    ClubTagMap save(ClubTagMap clubTagMap);

    void saveAll(List<ClubTagMap> clubTagMaps);
}
