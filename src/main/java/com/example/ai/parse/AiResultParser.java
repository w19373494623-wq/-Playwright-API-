package com.example.ai.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 响应解析工具。
 * 封装了从 AI 返回文本中提取 JSON 的通用逻辑：
 * 1. 清理 markdown 代码块包装
 * 2. 提取 JSON 数组
 * 3. 提取 JSON 对象
 *
 * 消除 DedupServiceImpl 和 CaptureController 中重复的 parseArrayJson / cleanJson 方法。
 */
@Component
public class AiResultParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 AI 响应中提取 JSON 数组。
     * 如果无法解析，返回空列表（不抛异常）。
     */
    public List<Map<String, Object>> parseArray(String aiResponse) {
        try {
            String json = cleanJson(aiResponse);
            json = json.replaceAll("^[^\\[]*", "").replaceAll("[^\\]]*$", "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 从 AI 响应中提取 JSON 对象。
     * 如果无法解析，返回 null。
     */
    public Map<String, Object> parseObject(String aiResponse) {
        try {
            String json = cleanJson(aiResponse);
            json = json.replaceAll("^[^{]*", "").replaceAll("[^}]*$", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 去除 markdown 代码块包装（```json ... ``` 或 ``` ... ```）。
     */
    public String cleanJson(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("```\\w*", "").replace("```", "").trim();
        }
        return s;
    }
}
