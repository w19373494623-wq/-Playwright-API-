package com.example.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SchemaInferrer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 从 JSON 字符串推断字段类型映射: {"title":"string","count":"integer"} */
    public Map<String, Object> inferSchema(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) return null;
        try {
            Object parsed = objectMapper.readValue(jsonBody, Object.class);
            Map<String, Object> schema = new LinkedHashMap<>();
            buildFlatSchema(schema, parsed);
            return schema.isEmpty() ? null : schema;
        } catch (Exception e) {
            return null;
        }
    }

    /** 递归推断，展平为 field → type 映射 */
    @SuppressWarnings("unchecked")
    private void buildFlatSchema(Map<String, Object> schema, Object value) {
        if (!(value instanceof Map)) return;
        Map<String, Object> map = (Map<String, Object>) value;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Map) {
                schema.put(entry.getKey(), "object");
            } else if (v instanceof List) {
                schema.put(entry.getKey(), "array");
            } else if (v instanceof String) {
                schema.put(entry.getKey(), "string");
            } else if (v instanceof Integer || v instanceof Long) {
                schema.put(entry.getKey(), "integer");
            } else if (v instanceof Float || v instanceof Double) {
                schema.put(entry.getKey(), "number");
            } else if (v instanceof Boolean) {
                schema.put(entry.getKey(), "boolean");
            } else if (v == null) {
                schema.put(entry.getKey(), "null");
            } else {
                schema.put(entry.getKey(), "unknown");
            }
        }
    }
}
