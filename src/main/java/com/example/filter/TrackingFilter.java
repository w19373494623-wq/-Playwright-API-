package com.example.filter;

import com.example.model.ApiAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Order(2)
public class TrackingFilter implements AssetFilter {

    private static final Logger log = LoggerFactory.getLogger(TrackingFilter.class);

    private static final Set<String> TRACKING_HOSTS = Set.of(
            "google-analytics.com", "googletagmanager.com",
            "baidu.com", "hm.baidu.com", "tongji.baidu.com",
            "doubleclick.net", "facebook.net",
            "hotjar.com", "clarity.ms", "mouseflow.com"
    );

    private static final Set<String> TRACKING_PATH_KEYWORDS = Set.of(
            "gtag", "analytics", "tracking", "beacon", "collect",
            "log.gif", "rum", "telemetry", "metrics", "sentry"
    );

    @Override
    public String name() {
        return "埋点/监控过滤";
    }

    @Override
    public List<ApiAsset> filter(List<ApiAsset> all) {
        List<ApiAsset> result = new ArrayList<>();
        List<String> filtered = new ArrayList<>();

        for (ApiAsset req : all) {
            String url = req.getUrl();
            if (url == null) continue;

            boolean tracking = isTracking(url);

            if (tracking) {
                filtered.add(methodAndUrl(req));
            } else {
                result.add(req);
            }
        }

        log.info("  [{}] {} -> {} (过滤 {} 条)", name(), all.size(), result.size(), filtered.size());
        logFilteredExamples(filtered);

        return result;
    }

    private boolean isTracking(String url) {
        String lower = url.toLowerCase();

        // 1) 检查 hostname（精确域名匹配，解决 baidu.com 误杀）
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null) {
                for (String h : TRACKING_HOSTS) {
                    if (host.equals(h) || host.endsWith("." + h)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2) 检查 path/keyword（全文 contains 匹配）
        for (String kw : TRACKING_PATH_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private void logFilteredExamples(List<String> items) {
        if (items.isEmpty()) return;
        int limit = Math.min(10, items.size());
        for (int i = 0; i < limit; i++) {
            log.info("    - {}", items.get(i));
        }
        if (items.size() > limit) log.info("    ... 共 {} 条", items.size());
    }

    private String methodAndUrl(ApiAsset a) {
        return a.getMethod() + " " + a.getUrl();
    }
}
