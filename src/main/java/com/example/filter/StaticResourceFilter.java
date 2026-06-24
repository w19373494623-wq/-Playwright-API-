package com.example.filter;

import com.example.model.ApiAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Order(1)
public class StaticResourceFilter implements AssetFilter {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceFilter.class);

    private static final Set<String> STATIC_SUFFIXES = Set.of(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".webp", ".mp4",
            ".mp3", ".pdf", ".zip", ".gz", ".tar"
    );

    private static final Set<String> STATIC_PREFIX_PATTERNS = Set.of(
            "/js/", "/css/", "/static/", "/assets/", "/images/", "/img/",
            "/fonts/", "/chunks/", "/_next/static/", "/favicon."
    );

    @Override
    public String name() {
        return "静态资源过滤";
    }

    @Override
    public List<ApiAsset> filter(List<ApiAsset> all) {
        List<ApiAsset> stage1 = new ArrayList<>();
        List<String> suffixFiltered = new ArrayList<>();
        for (ApiAsset req : all) {
            String url = req.getUrl();
            if (url == null) continue;
            String lower = url.toLowerCase();

            boolean isStatic = false;
            for (String suffix : STATIC_SUFFIXES) {
                if (lower.endsWith(suffix)) { isStatic = true; break; }
                if (lower.contains(suffix + "?")) { isStatic = true; break; }
                if (lower.contains(suffix + "#")) { isStatic = true; break; }
            }
            if (isStatic) {
                suffixFiltered.add(methodAndUrl(req));
                continue;
            }
            stage1.add(req);
        }
        log.info("  [后缀过滤] {} -> {} (过滤 {} 条)", all.size(), stage1.size(), suffixFiltered.size());
        logFilteredExamples(suffixFiltered);

        List<ApiAsset> stage2 = new ArrayList<>();
        List<String> prefixFiltered = new ArrayList<>();
        for (ApiAsset req : stage1) {
            String url = req.getUrl();
            if (url == null) continue;
            String lower = url.toLowerCase();

            boolean isStatic = false;
            for (String pattern : STATIC_PREFIX_PATTERNS) {
                if (lower.contains(pattern)) { isStatic = true; break; }
            }
            if (isStatic) {
                prefixFiltered.add(methodAndUrl(req));
                continue;
            }
            stage2.add(req);
        }
        log.info("  [目录前缀过滤] {} -> {} (过滤 {} 条)", stage1.size(), stage2.size(), prefixFiltered.size());
        logFilteredExamples(prefixFiltered);

        return stage2;
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
