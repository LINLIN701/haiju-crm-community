package com.miaohaiju.community.config;

import com.miaohaiju.community.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "product", "海聚客户管理系统社区版", "version", "V1.00.01"));
    }
}
