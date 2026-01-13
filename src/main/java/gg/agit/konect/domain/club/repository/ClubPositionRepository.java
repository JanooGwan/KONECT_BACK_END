package gg.agit.konect.domain.club.repository;

import org.springframework.data.repository.Repository;

import gg.agit.konect.domain.club.model.ClubPosition;

public interface ClubPositionRepository extends Repository<ClubPosition, Integer> {

    ClubPosition save(ClubPosition clubPosition);
}


