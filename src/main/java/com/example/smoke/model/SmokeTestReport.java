package com.example.smoke.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 烟雾测试总体报告。
 */
public class SmokeTestReport {

    private String collectionName;
    private int total;
    private int passed;
    private int failed;
    private double successRate;
    private long totalDurationMs;
    private List<SmokeTestResult> results = new ArrayList<>();
    private String timestamp;
    private String errorMessage;
    private Map<String, String> latestVars;

    public SmokeTestReport() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public List<SmokeTestResult> getResults() { return results; }
    public void setResults(List<SmokeTestResult> results) { this.results = results; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Map<String, String> getLatestVars() { return latestVars; }
    public void setLatestVars(Map<String, String> latestVars) { this.latestVars = latestVars; }

    /** 汇总统计 */
    public void summarize() {
        this.total = results.size();
        this.passed = (int) results.stream().filter(SmokeTestResult::isPassed).count();
        this.failed = total - passed;
        this.successRate = total > 0 ? (double) passed / total * 100 : 0;
        this.totalDurationMs = results.stream().mapToLong(SmokeTestResult::getDurationMs).sum();
    }
}
