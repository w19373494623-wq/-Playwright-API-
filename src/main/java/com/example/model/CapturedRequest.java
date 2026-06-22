package com.example.model;

import java.util.Map;

public class CapturedRequest {

    private String url;
    private String method;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private int statusCode;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private long timestamp;
    private int sequenceNumber;

    public CapturedRequest(String url, String method,
                           Map<String, String> requestHeaders, String requestBody,
                           int statusCode, Map<String, String> responseHeaders,
                           String responseBody, long timestamp, int sequenceNumber) {
        this.url = url;
        this.method = method;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.statusCode = statusCode;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public Map<String, String> getRequestHeaders() { return requestHeaders; }
    public String getRequestBody() { return requestBody; }
    public int getStatusCode() { return statusCode; }
    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public String getResponseBody() { return responseBody; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }

    @Override
    public String toString() {
        return method + " " + url + " -> " + statusCode;
    }
}
