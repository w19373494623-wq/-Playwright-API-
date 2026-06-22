package com.example.model;

import java.util.List;
import java.util.Map;

/**
 * AI 中间态缓存，让 validate → analyze → testcases 共享语义上下文
 */
public class AiContext {

    private String sessionId;
    private List<ApiAsset> assets;
    private long createdAt;

    // validate 结果
    private List<Integer> removedIndices;
    private List<Map<String, Object>> removedDetails;

    // analyze 结果
    private Map<String, Object> flowResult;

    // testcases 结果
    private List<Map<String, Object>> testCases;

    // 共享的语义摘要（供 analyze 和 testcases 复用）
    private String semanticSummary;

    public AiContext() {
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isValidateDone() { return removedIndices != null; }
    public boolean isAnalyzeDone() { return flowResult != null; }
    public boolean isTestCasesDone() { return testCases != null; }

    // --- getters/setters ---

    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { sessionId = v; }
    public List<ApiAsset> getAssets() { return assets; }
    public void setAssets(List<ApiAsset> v) { assets = v; }
    public long getCreatedAt() { return createdAt; }

    public List<Integer> getRemovedIndices() { return removedIndices; }
    public void setRemovedIndices(List<Integer> v) { removedIndices = v; }
    public List<Map<String, Object>> getRemovedDetails() { return removedDetails; }
    public void setRemovedDetails(List<Map<String, Object>> v) { removedDetails = v; }

    public Map<String, Object> getFlowResult() { return flowResult; }
    public void setFlowResult(Map<String, Object> v) { flowResult = v; }

    public List<Map<String, Object>> getTestCases() { return testCases; }
    public void setTestCases(List<Map<String, Object>> v) { testCases = v; }

    public String getSemanticSummary() { return semanticSummary; }
    public void setSemanticSummary(String v) { semanticSummary = v; }
}
