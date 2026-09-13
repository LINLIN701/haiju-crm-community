package com.miaohaiju.community.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;

import java.time.LocalDateTime;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 32) String phone,
            @Size(max = 100) String wechat,
            @Size(max = 500) String tags,
            @Size(max = 5000) String notes,
            @Valid RelationshipProfile relationship) {
    }

    public record RelationshipProfile(
            @Size(max = 30) String entityType,
            @Size(max = 60) String industry,
            @Size(max = 200) String organization,
            @Size(max = 100) String jobTitle,
            @Email @Size(max = 200) String email,
            @Size(max = 60) String relationshipType,
            @Size(max = 60) String stage,
            @Size(max = 2000) String needs) {
    }

    public record CustomerView(Long id, String name, String phone, String wechat, String tags,
                               String notes, LocalDateTime createdAt, LocalDateTime updatedAt,
                               RelationshipProfile relationship) {
    }

    public record ImportResult(int totalRows, int createdRows, int skippedRows) {
    }
}
