package com.example.capture;

import com.example.analysis.SchemaInferrer;
import com.example.analysis.UrlParser;
import com.example.model.ApiAsset;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class FetchXhrInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FetchXhrInterceptor.class);

    private final SchemaInferrer schemaInferrer;
    private final UrlParser urlParser;

    public FetchXhrInterceptor(SchemaInferrer schemaInferrer, UrlParser urlParser) {
        this.schemaInferrer = schemaInferrer;
        this.urlParser = urlParser;
    }

    private static final String FETCH_XHR_PATCH = """
            (function() {
              if (window.__apiPatchInstalled) return;
              window.__apiPatchInstalled = true;
              if (!Array.isArray(window.__capturedApiCalls)) window.__capturedApiCalls = [];
              var MAX = 2000;
              var MAX_BODY = 524288;
              function push(e) { if (window.__capturedApiCalls.length < MAX) window.__capturedApiCalls.push(e); }
              function h2o(h) {
                if (!h) return {};
                try { if (typeof h.forEach === 'function') { var r = {}; h.forEach(function(v,k){ r[k]=v; }); return r; } } catch(e){}
                return h;
              }
              function safeBody(b) {
                if (b == null) return null;
                if (typeof b === 'string') return b.length > MAX_BODY ? b.substring(0, MAX_BODY) : b;
                try { var s = JSON.stringify(b); return s.length > MAX_BODY ? s.substring(0, MAX_BODY) : s; }
                catch(e) { return String(b).substring(0, MAX_BODY); }
              }
              var origFetch = window.fetch;
              if (origFetch) {
                window.fetch = function(input, init) {
                  var url = (typeof input === 'string') ? input : (input instanceof Request ? input.url : String(input));
                  var opts = init || (input instanceof Request ? {} : {});
                  var method = (opts.method || (input instanceof Request ? input.method : null) || 'GET').toUpperCase();
                  var body = safeBody(opts.body);
                  var headers = h2o(opts.headers);
                  if (input instanceof Request && !init) headers = h2o(input.headers);
                  return origFetch.call(this, input, init).then(function(r) {
                    r.clone().text().then(function(t) {
                      push({ url:url, method:method, requestBody:body, responseBody:t.length>MAX_BODY?t.substring(0,MAX_BODY):t, statusCode:r.status, requestHeaders:headers });
                    }).catch(function(){ push({ url:url, method:method, requestBody:body, statusCode:r.status, requestHeaders:headers }); });
                    return r;
                  }).catch(function(e) {
                    push({ url:url, method:method, requestBody:body, statusCode:0, error:e.message, requestHeaders:headers });
                    throw e;
                  });
                };
              }
              if (XMLHttpRequest) {
                var XHRopen = XMLHttpRequest.prototype.open;
                var XHRsend = XMLHttpRequest.prototype.send;
                var XHRset = XMLHttpRequest.prototype.setRequestHeader;
                XMLHttpRequest.prototype.open = function(m, u) {
                  this.__method = m; this.__url = (typeof u==='string')?u:String(u); this.__headers = {}; this.__body = null;
                  return XHRopen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.setRequestHeader = function(k, v) {
                  if (!this.__headers) this.__headers = {};
                  this.__headers[k] = v;
                  return XHRset.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function(b) {
                  this.__body = safeBody(b);
                  var self = this;
                  this.addEventListener('load', function() {
                    push({ url:self.__url, method:(self.__method||'GET').toUpperCase(), requestBody:self.__body, responseBody:self.responseText?self.responseText.substring(0,MAX_BODY):null, statusCode:self.status, requestHeaders:self.__headers||{} });
                  });
                  this.addEventListener('error', function() {
                    push({ url:self.__url, method:(self.__method||'GET').toUpperCase(), requestBody:self.__body, statusCode:0, error:'Network error', requestHeaders:self.__headers||{} });
                  });
                  return XHRsend.apply(this, arguments);
                };
              }
            })();
            """;

    public void install(BrowserContext context) {
        context.addInitScript(FETCH_XHR_PATCH);
    }

    @SuppressWarnings("unchecked")
    public int extractPatchedData(Page page, String pageUrl, String sessionId, Consumer<ApiAsset> sink) {
        if (page == null) return 0;
        try {
            Object raw = page.evaluate(
                    "(function(){ var a = window.__capturedApiCalls || []; window.__capturedApiCalls = []; return JSON.parse(JSON.stringify(a)); })()");
            if (!(raw instanceof List)) return 0;
            List<Map<String, Object>> entries = (List<Map<String, Object>>) raw;
            int count = 0;
            for (Map<String, Object> e : entries) {
                try {
                    String url = (String) e.getOrDefault("url", "");
                    if (url.isEmpty()) continue;
                    if (url.startsWith("data:") || url.startsWith("blob:")) continue;
                    String method = ((String) e.getOrDefault("method", "GET")).toUpperCase();
                    int status = e.containsKey("statusCode") ? ((Number) e.get("statusCode")).intValue() : 0;
                    String requestBody = (String) e.get("requestBody");
                    String responseBody = (String) e.get("responseBody");
                    Object rawHeaders = e.get("requestHeaders");

                    ApiAsset asset = new ApiAsset(url, method, requestBody, status, responseBody);
                    asset.setPageUrl(pageUrl);
                    asset.setSessionId(sessionId);
                    asset.setQuery(urlParser.parseQueryParams(url));
                    asset.setRequestSchema(schemaInferrer.inferSchema(requestBody));
                    asset.setResponseSchema(schemaInferrer.inferSchema(responseBody));

                    if (rawHeaders instanceof Map) {
                        Map<String, String> h = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> he : ((Map<String, Object>) rawHeaders).entrySet()) {
                            h.put(he.getKey(), he.getValue() != null ? he.getValue().toString() : "");
                        }
                        asset.setHeaders(h);
                    }

                    sink.accept(asset);
                    count++;
                } catch (Exception ignored) {
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("读取 fetch/XHR 补丁数据失败 (页面可能已关闭): {}", e.getMessage());
            return 0;
        }
    }
}
