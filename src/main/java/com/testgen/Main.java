package com.testgen;

import com.testgen.server.SimpleWebServer;

/**
 * 主程序入口
 * 直接启动Web服务模式 + LLM智能解析
 */
public class Main {

    public static void main(String[] args) {
        // 直接启动Web服务 + LLM模式
        startWebServer(true);
    }

    private static void startWebServer(boolean useLLM) {
        System.out.println("========================================");
        System.out.println("  PRD 测试用例生成器 v2.0");
        System.out.println("========================================");
        System.out.println();
        System.out.println("启动模式: Web服务 + AI智能解析");
        System.out.println();

        try {
            SimpleWebServer server = new SimpleWebServer(useLLM);
            server.start();

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n正在关闭服务器...");
                server.stop();
            }));

            // 保持运行
            System.out.println("按 Ctrl+C 停止服务器\n");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
