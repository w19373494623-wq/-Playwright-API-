package com.example.smoke.model;

import java.util.List;
import java.util.Map;

/**
 * Postman Request 模型。
 * 支持 method、header、url（字符串或对象）、body。
 */
public class PostmanRequest {

    private String method;
    private List<Map<String, String>> header;
    private Object url;          // String 或 Map
    private Map<String, Object> body;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public List<Map<String, String>> getHeader() { return header; }
    public void setHeader(List<Map<String, String>> header) { this.header = header; }

    public Object getUrl() { return url; }
    public void setUrl(Object url) { this.url = url; }

    public Map<String, Object> getBody() { return body; }
    public void setBody(Map<String, Object> body) { this.body = body; }
}
