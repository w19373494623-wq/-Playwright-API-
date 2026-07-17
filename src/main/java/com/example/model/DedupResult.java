package com.example.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DedupResult {
    private final List<Map<String, Object>> apis;
    private final int totalRaw;
    private final boolean fallback;
    private final String rawAiResponse;

    @JsonCreator
    public DedupResult(@JsonProperty("apis") List<Map<String, Object>> apis,
                       @JsonProperty("totalRaw") int totalRaw,
                       @JsonProperty("fallback") boolean fallback,
                       @JsonProperty("rawAiResponse") String rawAiResponse) {
        this.apis = apis;
        this.totalRaw = totalRaw;
        this.fallback = fallback;
        this.rawAiResponse = rawAiResponse;
    }

    public List<Map<String, Object>> getApis() { return apis; }
    public int getTotalRaw() { return totalRaw; }
    public boolean isFallback() { return fallback; }
    public String getRawAiResponse() { return rawAiResponse; }
    public int getTotal() { return apis.size(); }
}
