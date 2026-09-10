package com.miaohaiju.community.customer;

import com.miaohaiju.community.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.miaohaiju.community.customer.CustomerDtos.CustomerRequest;
import static com.miaohaiju.community.customer.CustomerDtos.CustomerView;
import static com.miaohaiju.community.customer.CustomerDtos.ImportResult;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<CustomerView>> list(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    ApiResponse<CustomerView> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    ApiResponse<CustomerView> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    ApiResponse<CustomerView> update(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<ImportResult> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(service.importCsv(file.getBytes()));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=haiju-customers.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.exportCsv());
    }
}
