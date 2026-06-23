package com.example.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiContext {

    private String sessionId;
    private List<ApiAsset> assets;
    private List<Integer> removedIndices;
    private List<Map<String, Object>> removedDetails;
    private String semanticSummary;
    private Map<String, Object> flowResult;
    private boolean analyzeDone;
    private List<Map<String, Object>> testCases;

    public AiContext() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public List<ApiAsset> getAssets() { return assets; }
    public void setAssets(List<ApiAsset> assets) { this.assets = assets; }

    public List<Integer> getRemovedIndices() { return removedIndices; }
    public void setRemovedIndices(List<Integer> removedIndices) { this.removedIndices = removedIndices; }

    public List<Map<String, Object>> getRemovedDetails() { return removedDetails; }
    public void setRemovedDetails(List<Map<String, Object>> removedDetails) { this.removedDetails = removedDetails; }

    public String getSemanticSummary() { return semanticSummary; }
    public void setSemanticSummary(String semanticSummary) { this.semanticSummary = semanticSummary; }

    public Map<String, Object> getFlowResult() { return flowResult; }
    public void setFlowResult(Map<String, Object> flowResult) { this.flowResult = flowResult; }

    public boolean isAnalyzeDone() { return analyzeDone; }
    public void setAnalyzeDone(boolean analyzeDone) { this.analyzeDone = analyzeDone; }

    public List<Map<String, Object>> getTestCases() { return testCases; }
    public void setTestCases(List<Map<String, Object>> testCases) { this.testCases = testCases; }
}
