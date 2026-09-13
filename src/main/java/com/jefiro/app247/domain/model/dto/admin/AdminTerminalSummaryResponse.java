package com.jefiro.app247.domain.model.dto.admin;

public record AdminTerminalSummaryResponse(
        long total,
        long online,
        long offline,
        long comAlerta
) {
}
