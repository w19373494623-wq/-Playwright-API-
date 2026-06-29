package com.example.ai.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 响应解析器。
 * 兼容主流大模型的常见返回格式：
 * - markdown 代码块包裹（```json ... ```）
 * - 纯 JSON 数组 [...]
 * - 对象包裹数组 {"apis":[...]} / {"data":[...]}
 * - AI 前后解释性文字
 */
@Component
public class AiResultParser {

    private static final Logger log = LoggerFactory.getLogger(AiResultParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 AI 响应中提取 JSON 数组。
     * 自动兼容以下格式：
     *   [...]
     *   {"apis":[...]}
     *   {"data":[...]}
     *   以及 markdown 代码块包裹的任何上述格式。
     */
    public List<Map<String, Object>> parseArray(String aiResponse) {
        String json = cleanAndExtract(aiResponse);
        if (json == null) return List.of();

        try {
            // 情况 1: 顶层是数组
            if (json.trim().startsWith("[")) {
                return readArray(json);
            }
            // 情况 2: 顶层是对象，查找数组字段
            if (json.trim().startsWith("{")) {
                Map<String, Object> obj = objectMapper.readValue(json, Map.class);
                for (String key : new String[]{"apis", "data", "items", "list", "result", "records"}) {
                    Object val = obj.get(key);
                    if (val instanceof List) {
                        return filterMaps((List<?>) val);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("parseArray 解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 从 AI 响应中提取 JSON 对象。
     */
    public Map<String, Object> parseObject(String aiResponse) {
        String json = cleanAndExtract(aiResponse);
        if (json == null) return null;
        try {
            // 定位顶层 {}
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (Exception e) {
            log.warn("parseObject 解析失败: {}", e.getMessage());
            return null;
        }
    }

    // ===== 内部方法 =====

    /** 清理 markdown + 提取 JSON 主体 */
    private String cleanAndExtract(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();

        // 去 markdown 代码块包裹
        s = s.replace("```json", "").replace("```JSON", "").replace("```", "").trim();

        // 找到第一个 [ 或 {
        int arrayStart = s.indexOf('[');
        int objectStart = s.indexOf('{');
        if (arrayStart < 0 && objectStart < 0) return null;

        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            int end = s.lastIndexOf(']');
            if (end < 0 || end <= arrayStart) return null;
            return s.substring(arrayStart, end + 1);
        } else {
            int end = s.lastIndexOf('}');
            if (end < 0 || end <= objectStart) return null;
            return s.substring(objectStart, end + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readArray(String json) throws Exception {
        List<?> raw = objectMapper.readValue(json, List.class);
        return filterMaps(raw);
    }

    private List<Map<String, Object>> filterMaps(List<?> raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }
}
