package com.bishe.server.controller;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.TraceId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "server-java");
        data.put("time", TimePayloads.toEpochMillis(java.time.Instant.now()));
        return ApiResponse.ok(data, TraceId.next());
    }
}
