package com.example.filter;

import com.example.model.ApiAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Component
public class ClassifyFilter {

    private static final Logger log = LoggerFactory.getLogger(ClassifyFilter.class);

    private static final Set<String> AUTH_PATHS = Set.of(
            "/login", "/signin", "/signup", "/register", "/auth", "/oauth",
            "/token", "/captcha", "/verify", "/password", "/logout",
            "/session", "/permission"
    );

    private static final Set<String> MONITORING_PATHS = Set.of(
            "analytics", "tracking", "metrics", "rum", "sentry",
            "beacon", "collect", "telemetry", "monitor"
    );

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/", "font/", "video/", "audio/"
    );

    private static final Set<String> STATIC_SUFFIXES = Set.of(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".webp", ".mp4",
            ".mp3", ".pdf", ".zip", ".gz", ".tar"
    );

    private static final Set<String> STATIC_PREFIX_PATTERNS = Set.of(
            "/js/", "/css/", "/static/", "/assets/", "/images/", "/img/",
            "/fonts/", "/chunks/", "/_next/static/", "/favicon."
    );

    private String mainDomain;

    public void setMainDomain(String mainDomain) {
        this.mainDomain = mainDomain;
    }

    public void classify(List<ApiAsset> assets) {
        for (ApiAsset a : assets) {
            String cat = classifyApi(a);
            a.setCategory(cat);
        }
    }

    private String classifyApi(ApiAsset a) {
        String url = a.getUrl();
        if (url == null) return "unknown";
        String lower = url.toLowerCase();

        String contentType = "";
        try {
            Object ct = a.getResponse().get("content-type");
            if (ct != null) contentType = ct.toString().toLowerCase();
        } catch (Exception ignored) {}

        // 1) 第三方资源：域名不同于主域名
        String domain = extractDomain(url);
        if (domain != null && !domain.isEmpty() && !domain.equals(mainDomain)) {
            if (!domain.startsWith("api.") && !domain.equals("localhost")) {
                for (String imgCt : IMAGE_CONTENT_TYPES) {
                    if (contentType.contains(imgCt)) return "static";
                }
                return "third_party";
            }
        }

        // 2) 监控上报
        for (String kw : MONITORING_PATHS) {
            if (lower.contains(kw)) return "monitoring";
        }

        // 3) 静态资源（根据 content-type）
        if (!contentType.isEmpty()) {
            for (String imgCt : IMAGE_CONTENT_TYPES) {
                if (contentType.contains(imgCt)) return "static";
            }
        }

        // 4) 静态资源（根据后缀/目录）
        for (String suffix : STATIC_SUFFIXES) {
            if (lower.endsWith(suffix)) return "static";
        }
        for (String pattern : STATIC_PREFIX_PATTERNS) {
            if (lower.contains(pattern)) return "static";
        }

        // 5) 鉴权接口
        for (String authPath : AUTH_PATHS) {
            if (lower.contains(authPath)) return "auth";
        }

        // 6) 业务接口（默认）
        return "business";
    }

    private String extractDomain(String url) {
        if (url == null) return null;
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return null; }
    }
}
