package com.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器。
 *
 * 职责：
 *  统一管理自动化执行过程中的动态变量。
 *  支持变量保存、获取、模板替换、从 JSON 响应中提取。
 *
 * 使用场景：
 *  登录后将 token 存入 → resolve() 替换 Collection 中 {{token}}
 *  执行 API 后将响应中的 ugcId 存入 → 后续接口直接使用 {{ugcId}}
 */
@Component
public class VariableResolver {

    private static final Logger log = LoggerFactory.getLogger(VariableResolver.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> variables = new LinkedHashMap<>();

    /**
     * 保存变量。
     */
    public void put(String key, String value) {
        if (key != null && value != null) {
            variables.put(key, value);
            log.debug("变量设置: {}={}", key, value);
        }
    }

    /**
     * 获取变量值。
     */
    public String get(String key) {
        return variables.getOrDefault(key, "");
    }

    /**
     * 获取所有变量的只读视图。
     */
    public Map<String, String> getAll() {
        return Map.copyOf(variables);
    }

    /**
     * 清空所有变量。
     */
    public void clear() {
        variables.clear();
    }

    /**
     * 替换输入字符串中的 {{key}} 占位符为已存储的变量值。
     * 未找到的变量将被替换为空字符串。
     *
     * 示例：
     *  "Bearer {{token}}" → "Bearer eyJhbGci..."
     *  "{\"id\":\"{{ugcId}}\"}" → "{\"id\":\"123456\"}"
     */
    public String resolve(String input) {
        if (input == null || input.isBlank()) return input;
        StringBuilder sb = new StringBuilder();
        Matcher matcher = VAR_PATTERN.matcher(input);
        int last = 0;
        while (matcher.find()) {
            sb.append(input, last, matcher.start());
            String key = matcher.group(1);
            sb.append(variables.getOrDefault(key, ""));
            last = matcher.end();
        }
        sb.append(input.substring(last));
        return sb.toString();
    }

    /**
     * 从 JSON 响应中提取指定路径的值并保存为变量。
     *
     * 变量名取 jsonPath 最后一段。
     * 例如：
     *  extract(response, "data.ugcId") → 保存 ugcId=123456
     *  extract(response, "token")      → 保存 token=eyJ...
     *
     * @param response  JSON 响应节点
     * @param jsonPath  点分隔路径，如 "data.ugcId"
     * @return 提取到的值，未找到返回 null
     */
    public String extract(JsonNode response, String jsonPath) {
        if (response == null || jsonPath == null || jsonPath.isBlank()) return null;

        JsonNode node = response;
        String[] parts = jsonPath.split("\\.");
        for (String part : parts) {
            if (node == null || !node.isObject()) return null;
            node = node.get(part);
        }
        if (node == null) return null;

        String value = node.isTextual() ? node.asText() : node.toString();
        String varName = parts[parts.length - 1];
        put(varName, value);
        log.info("变量提取: {}={} (路径: {})", varName, value, jsonPath);
        return value;
    }
}
