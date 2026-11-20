package com.shotabra.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 16) String emoji) {
}
