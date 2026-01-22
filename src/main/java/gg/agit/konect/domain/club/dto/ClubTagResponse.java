package gg.agit.konect.domain.club.dto;

import gg.agit.konect.domain.club.model.ClubTag;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClubTagResponse(
    @Schema(description = "태그 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    Integer id,

    @Schema(description = "태그 이름", example = "웹개발", requiredMode = Schema.RequiredMode.REQUIRED)
    String name
) {
    public static ClubTagResponse from(ClubTag clubTag) {
        return new ClubTagResponse(clubTag.getId(), clubTag.getName());
    }
}
