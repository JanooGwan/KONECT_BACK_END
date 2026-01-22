package gg.agit.konect.domain.club.dto;

import java.util.List;

import gg.agit.konect.domain.club.model.ClubTag;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClubTagsResponse(
    @Schema(description = "태그 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ClubTagResponse> tags
) {
    public static ClubTagsResponse from(List<ClubTag> clubTags) {
        List<ClubTagResponse> tags = clubTags.stream()
            .map(ClubTagResponse::from)
            .toList();
        return new ClubTagsResponse(tags);
    }
}
