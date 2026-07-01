package com.example.smoke.assertion;

import com.example.smoke.model.SmokeTestResult;
import com.example.smoke.model.PostmanItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 默认断言实现：
 * 1. HTTP Status == 200
 * 2. 如果响应体为 JSON，优先判断 code == 0（兼容 {"code": 0, "message": "ok"} 结构）
 * 3. 如果 JSON 中不存在 code 字段，仅校验 HTTP Status
 */
public class DefaultAssertion implements Assertion {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssertion.class);

    @Override
    public boolean assertApi(PostmanItem item, SmokeTestResult result) {
        // 1. 校验 HTTP Status
        if (result.getHttpStatus() != 200) {
            result.setFailureReason("HTTP Status 期望 200，实际 " + result.getHttpStatus());
            return false;
        }

        // 2. 尝试解析 JSON 响应体中的 code 字段
        String body = result.getResponseBody();
        if (body != null && !body.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = (Map<String, Object>) com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .readValue(body, Map.class);

                if (parsed.containsKey("code")) {
                    Object codeVal = parsed.get("code");
                    int code;
                    if (codeVal instanceof Number) {
                        code = ((Number) codeVal).intValue();
                    } else {
                        code = Integer.parseInt(codeVal.toString());
                    }
                    if (code != 0 && code != 200) {
                        result.setFailureReason("业务 code 期望 0，实际 " + code);
                        return false;
                    }
                }
                // 没有 code 字段则只校验 HTTP Status
            } catch (Exception e) {
                // 响应体非 JSON 或解析失败，仅以 HTTP Status 为准
                log.trace("响应体非 JSON 或解析失败，仅校验 HTTP Status: {}", e.getMessage());
            }
        }

        return true;
    }
}
