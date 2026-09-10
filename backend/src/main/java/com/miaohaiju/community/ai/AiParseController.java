package com.miaohaiju.community.ai;

import com.miaohaiju.community.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiParseController {
    private final AiParseService service;

    public AiParseController(AiParseService service) {
        this.service = service;
    }

    @PostMapping("/parse-customer")
    ApiResponse<AiParseService.ParseResult> parse(@Valid @RequestBody ParseRequest request) {
        return ApiResponse.ok(service.parse(request.text()));
    }

    public record ParseRequest(@NotBlank @Size(max = 20000) String text) {
    }
}
