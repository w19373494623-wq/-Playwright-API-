package com.example.capture;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BrowserManager {

    private static final Logger log = LoggerFactory.getLogger(BrowserManager.class);

    private static volatile Playwright sharedPlaywright;
    private static volatile Browser sharedBrowser;

    public synchronized Browser getOrCreateBrowser() {
        if (sharedPlaywright == null) {
            log.info("首次启动 Playwright 引擎...");
            sharedPlaywright = Playwright.create();
        }
        if (sharedBrowser == null || !sharedBrowser.isConnected()) {
            log.info("启动 Chromium 浏览器...");
            sharedBrowser = sharedPlaywright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false));
        }
        return sharedBrowser;
    }

    public void closePage(com.microsoft.playwright.Page page) {
        if (page != null) {
            try { page.close(); } catch (Exception ignored) {}
        }
    }

    public void closeContext(com.microsoft.playwright.BrowserContext context) {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
        }
    }

    @PreDestroy
    public synchronized void destroy() {
        log.info("销毁浏览器资源...");
        if (sharedBrowser != null) {
            try { sharedBrowser.close(); } catch (Exception ignored) {}
            sharedBrowser = null;
        }
        if (sharedPlaywright != null) {
            try { sharedPlaywright.close(); } catch (Exception ignored) {}
            sharedPlaywright = null;
        }
    }
}
