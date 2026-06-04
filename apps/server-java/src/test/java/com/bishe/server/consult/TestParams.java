package com.bishe.server.consult;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

/**
 * 测试参数转换工具。
 */
public final class TestParams {

    private TestParams() {
    }

    public static MultiValueMap<String, String> fromMap(Map<String, String> input) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        input.forEach(params::add);
        return params;
    }
}
