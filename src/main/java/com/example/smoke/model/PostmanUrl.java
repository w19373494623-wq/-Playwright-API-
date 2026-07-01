package com.example.smoke.model;

import java.util.List;
import java.util.Map;

/**
 * Postman URL 对象模型。
 * 支持 raw、protocol、host、path、query 等字段。
 */
public class PostmanUrl {

    private String raw;
    private String protocol;
    private List<String> host;
    private List<String> path;
    private List<Map<String, String>> query;
    private String port;
    private List<String> variable;

    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public List<String> getHost() { return host; }
    public void setHost(List<String> host) { this.host = host; }

    public List<String> getPath() { return path; }
    public void setPath(List<String> path) { this.path = path; }

    public List<Map<String, String>> getQuery() { return query; }
    public void setQuery(List<Map<String, String>> query) { this.query = query; }

    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    public List<String> getVariable() { return variable; }
    public void setVariable(List<String> variable) { this.variable = variable; }

    /**
     * 将 URL 对象重建为完整 URL 字符串。
     * 即使 raw 有值，也会追加 query 参数（raw 通常不含 query）。
     * 如果 raw 已经包含 query，则不会重复追加。
     */
    public String buildUrl() {
        StringBuilder sb = new StringBuilder();
        if (raw != null && !raw.isBlank()) {
            sb.append(raw);
        } else {
            if (protocol != null && !protocol.isBlank()) {
                sb.append(protocol).append("://");
            }
            if (host != null && !host.isEmpty()) {
                sb.append(String.join(".", host));
            }
            if (port != null && !port.isBlank()) {
                sb.append(":").append(port);
            }
            if (path != null && !path.isEmpty()) {
                for (String seg : path) {
                    sb.append("/").append(seg);
                }
            }
        }
        // 始终追加 query 参数（raw 通常不包含 query）
        appendQuery(sb);
        return sb.toString();
    }

    /**
     * 追加 query 参数到 URL，避免与 raw 中已有的 query 重复。
     */
    private void appendQuery(StringBuilder sb) {
        if (query == null || query.isEmpty()) return;

        String rawLower = raw != null ? raw.toLowerCase() : "";
        for (Map<String, String> q : query) {
            String key = q.getOrDefault("key", "");
            String value = q.getOrDefault("value", "");
            if (key.isBlank()) continue;

            // 检查该参数是否已存在于 raw 中
            String pair = key + "=";
            if (rawLower.contains(pair.toLowerCase())) continue;

            sb.append(sb.indexOf("?") >= 0 ? "&" : "?");
            sb.append(key).append("=").append(value);
        }
    }
}
