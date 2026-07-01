package com.example.smoke.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条接口的烟雾测试结果。
 */
public class SmokeTestResult {

    private String name;
    private String method;
    private String url;
    private int httpStatus;
    private long durationMs;
    private boolean passed;
    private String failureReason;
    private Map<String, Object> responseHeaders = new LinkedHashMap<>();
    private String responseBody;
    private String errorMessage;

    /** 当前接口在执行时使用了哪些变量的实际值（用于报告展示） */
    private Map<String, String> usedVariables = new LinkedHashMap<>();

    /** 当前接口响应中提取出的变量（供后续接口使用） */
    private Map<String, String> extractedVariables = new LinkedHashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getHttpStatus() { return httpStatus; }
    public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Map<String, Object> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, Object> responseHeaders) { this.responseHeaders = responseHeaders; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Map<String, String> getUsedVariables() { return usedVariables; }
    public void setUsedVariables(Map<String, String> usedVariables) { this.usedVariables = usedVariables; }

    public Map<String, String> getExtractedVariables() { return extractedVariables; }
    public void setExtractedVariables(Map<String, String> extractedVariables) { this.extractedVariables = extractedVariables; }
}
