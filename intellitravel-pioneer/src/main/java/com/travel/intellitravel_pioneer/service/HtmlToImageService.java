package com.travel.intellitravel_pioneer.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.travel.intellitravel_pioneer.vo.trip.ReportData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class HtmlToImageService {

    @Autowired
    private TemplateEngine templateEngine;

    public byte[] generateJourneyReportPoster(ReportData data, String templateName) throws Exception {
        // 1. 准备传入 HTML 模板的上下文变量
        Context context = new Context();
        context.setVariable("trajectoryUrl", data.getTrajectoryUrl());
        context.setVariable("steps", data.getSteps());
        context.setVariable("powerCost", data.getPowerCost());
        context.setVariable("durationMin", data.getDurationMin());
        
        // 传递主题给模板，如果没有则默认使用自然(nature)
        String theme = data.getTheme() != null ? data.getTheme() : "nature";
        context.setVariable("theme", theme);

        // 2. 渲染模板为纯 HTML 字符串
        String htmlContent = templateEngine.process(templateName, context);

        // 3. 使用 Playwright 启动无头浏览器，加载 HTML 并截图
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            
            // 加载 HTML 字符串
            page.setContent(htmlContent);
            
            // 设置浏览器视口大小 (也就是生成海报的像素大小)
            page.setViewportSize(375, 667);
            
            // 等待页面网络空闲，确保图片等资源加载完毕
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            
            // 截图为字节数组
            return page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true)
                .setType(com.microsoft.playwright.options.ScreenshotType.JPEG)
                .setQuality(90));
        }
    }
}
