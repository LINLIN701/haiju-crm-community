package com.miaohaiju.community.audit;

import com.miaohaiju.community.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operation-logs")
public class OperationLogController {
    private final OperationLogService service;

    public OperationLogController(OperationLogService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<OperationLogService.OperationLogItem>> latest() {
        return ApiResponse.ok(service.latest());
    }
}
