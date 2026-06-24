package com.example.analysis;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UrlParser {

    /** 从 URL 中提取域名 */
    public String extractDomain(String url) {
        if (url == null) return null;
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return null; }
    }

    /** 从 URL 中解析 query 参数 */
    public Map<String, Object> parseQueryParams(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query == null || query.isEmpty()) return null;
            Map<String, Object> result = new LinkedHashMap<>();
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv[0].isEmpty()) continue;
                result.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 URL 中提取 baseUrl (scheme://host:port) */
    public String extractBaseUrl(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }

    /** 从 URL 中提取路径 */
    public String getPath(String url) {
        if (url == null) return null;
        try { return URI.create(url).getPath(); }
        catch (Exception e) { return url; }
    }

    /** 从 URL 中提取主机名 */
    public String extractHost(String url) {
        if (url == null) return null;
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return null; }
    }
}
