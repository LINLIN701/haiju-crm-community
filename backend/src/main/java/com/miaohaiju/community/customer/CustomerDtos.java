package com.miaohaiju.community.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 32) String phone,
            @Size(max = 100) String wechat,
            @Size(max = 500) String tags,
            @Size(max = 5000) String notes) {
    }

    public record CustomerView(Long id, String name, String phone, String wechat, String tags,
                               String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record ImportResult(int totalRows, int createdRows, int skippedRows) {
    }
}
