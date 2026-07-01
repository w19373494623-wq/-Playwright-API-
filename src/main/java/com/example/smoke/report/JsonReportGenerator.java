package com.example.smoke.report;

import com.example.smoke.model.SmokeTestReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON 格式报告生成器。
 */
public class JsonReportGenerator implements ReportGenerator {

    private final ObjectMapper objectMapper;

    public JsonReportGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String generate(SmokeTestReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
