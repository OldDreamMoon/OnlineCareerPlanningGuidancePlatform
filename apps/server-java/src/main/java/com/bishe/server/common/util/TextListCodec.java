package com.bishe.server.common.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 逗号分隔文本与列表之间的转换工具，统一处理 trim、去重与空值过滤。
 */
public final class TextListCodec {

    private TextListCodec() {
    }

    public static List<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }

        String[] rawItems = csv.split(",");
        List<String> result = new ArrayList<>();
        for (String rawItem : rawItems) {
            String item = normalizeText(rawItem);
            if (item != null) {
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    public static List<String> normalize(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String item : items) {
            String value = normalizeText(item);
            if (value != null) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    public static String join(List<String> items) {
        List<String> normalized = normalize(items);
        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(",", normalized);
    }

    public static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
