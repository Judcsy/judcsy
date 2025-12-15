package com.testgen.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.testgen.controller.TestCaseController;
import com.testgen.feishu.FeishuService;
import com.testgen.feishu.FeishuConfig;
import com.testgen.feishu.FeishuException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 简单的HTTP Web服务器
 * 提供静态资源和REST API服务
 */
public class SimpleWebServer {

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 8089;
    private int actualPort;
    private static final String STATIC_DIR = "src/main/resources/static";

    private HttpServer server;
    private TestCaseController controller;
    private FeishuService feishuService;
    private ObjectMapper objectMapper;
    private boolean useLLM;

    public SimpleWebServer() {
        this(false);
    }

    public SimpleWebServer(boolean useLLM) {
        this.useLLM = useLLM;
        this.controller = new TestCaseController(useLLM);
        this.feishuService = new FeishuService();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 检查端口是否可用
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 查找可用端口
     */
    private int findAvailablePort() {
        for (int port = DEFAULT_PORT; port <= MAX_PORT; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
        // 尝试找到可用端口
        actualPort = findAvailablePort();

        if (actualPort == -1) {
            throw new IOException("无法找到可用端口（尝试范围：" + DEFAULT_PORT + "-" + MAX_PORT + "）\n" +
                    "请关闭占用端口的程序或稍后再试。");
        }

        if (actualPort != DEFAULT_PORT) {
            System.out.println("[警告] 默认端口 " + DEFAULT_PORT + " 已被占用，使用端口 " + actualPort);
        }

        server = HttpServer.create(new InetSocketAddress(actualPort), 0);

        // 注册路由
        server.createContext("/", this::handleStaticRequest);
        server.createContext("/api/testcase/generate", this::handleGenerateRequest);
        server.createContext("/api/testcase/compare", this::handleCompareRequest);
        // 飞书相关路由
        server.createContext("/api/feishu/content", this::handleFeishuContentRequest);
        server.createContext("/api/feishu/status", this::handleFeishuStatusRequest);
        server.createContext("/api/feishu/config", this::handleFeishuConfigRequest);

        server.setExecutor(null);
        server.start();

        System.out.println("========================================");
        System.out.println("Web服务器已启动！");
        System.out.println("访问地址: http://localhost:" + actualPort);
        System.out.println("LLM模式: " + (useLLM ? "启用（火山引擎）" : "禁用"));
        System.out.println("========================================");
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("服务器已停止");
        }
    }

    /**
     * 处理静态资源请求
     */
    private void handleStaticRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // 默认页面
        if ("/".equals(path)) {
            path = "/index.html";
        }

        // 尝试从resources读取
        String resourcePath = STATIC_DIR + path;
        InputStream is = getClass().getClassLoader().getResourceAsStream("static" + path);

        if (is == null && Files.exists(Paths.get(resourcePath))) {
            // 从文件系统读取
            byte[] content = Files.readAllBytes(Paths.get(resourcePath));
            sendResponse(exchange, 200, content, getContentType(path));
        } else if (is != null) {
            // 从resources读取
            byte[] content = is.readAllBytes();
            is.close();
            sendResponse(exchange, 200, content, getContentType(path));
        } else {
            // 404
            String notFound = "404 Not Found";
            sendResponse(exchange, 404, notFound.getBytes(StandardCharsets.UTF_8), "text/plain");
        }
    }

    /**
     * 处理测试用例生成请求
     */
    private void handleGenerateRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            // 读取请求体
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            // 调用控制器处理
            String response = controller.generateTestCases(requestBody);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Internal Server Error\"}");
        }
    }

    /**
     * 处理测试用例对比评分请求
     */
    private void handleCompareRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            // 读取请求体
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            // 调用控制器处理对比评分
            String response = controller.compareTestCases(requestBody);

            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Internal Server Error\"}");
        }
    }

    /**
     * 处理飞书文档内容获取请求
     * GET /api/feishu/content?url=xxx
     */
    private void handleFeishuContentRequest(HttpExchange exchange) throws IOException {
        // 支持CORS预检请求
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            sendJsonResponse(exchange, 200, "{}");
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            // 获取URL参数
            String query = exchange.getRequestURI().getQuery();
            String documentUrl = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if ("url".equals(pair[0]) && pair.length > 1) {
                        documentUrl = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        break;
                    }
                }
            }

            if (documentUrl == null || documentUrl.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"缺少url参数\"}");
                return;
            }

            System.out.println("[Feishu] 获取文档: " + documentUrl);

            // 获取文档内容
            FeishuService.DocumentContent docContent = feishuService.getDocumentContent(documentUrl);

            // 构建响应
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"success\":true,\"content\":");
            jsonBuilder.append(objectMapper.writeValueAsString(docContent.getFullContent()));
            jsonBuilder.append(",\"textContent\":");
            jsonBuilder.append(objectMapper.writeValueAsString(docContent.textContent));
            jsonBuilder.append(",\"imageCount\":").append(docContent.images.size());
            jsonBuilder.append(",\"message\":\"获取成功\"}");

            sendJsonResponse(exchange, 200, jsonBuilder.toString());

        } catch (FeishuException e) {
            System.err.println("[Feishu] 获取文档失败: " + e.getMessage());
            String errorJson = "{\"success\":false,\"message\":" +
                    objectMapper.writeValueAsString(e.getMessage()) + "}";
            sendJsonResponse(exchange, 400, errorJson);
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"服务器内部错误\"}");
        }
    }

    /**
     * 处理飞书配置状态检查请求
     * GET /api/feishu/status
     */
    private void handleFeishuStatusRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        boolean configured = FeishuConfig.isConfigured();
        boolean hasToken = FeishuConfig.getCachedToken() != null;

        String response = String.format(
                "{\"success\":true,\"configured\":%b,\"hasValidToken\":%b,\"appId\":\"%s\"}",
                configured,
                hasToken,
                configured ? maskString(FeishuConfig.getAppId()) : "");

        sendJsonResponse(exchange, 200, response);
    }

    /**
     * 处理飞书配置更新请求
     * POST /api/feishu/config
     */
    private void handleFeishuConfigRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            JsonNode json = objectMapper.readTree(requestBody);

            if (json.has("appId")) {
                FeishuConfig.setAppId(json.get("appId").asText());
            }
            if (json.has("appSecret")) {
                FeishuConfig.setAppSecret(json.get("appSecret").asText());
            }

            // 验证配置是否有效（尝试获取token）
            try {
                feishuService.getTenantAccessToken();
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"配置成功，已验证有效\"}");
            } catch (FeishuException e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"配置无效: " + e.getMessage() + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"服务器内部错误\"}");
        }
    }

    /**
     * 掩码字符串（用于显示敏感信息）
     */
    private String maskString(String str) {
        if (str == null || str.length() <= 8) {
            return "****";
        }
        return str.substring(0, 4) + "****" + str.substring(str.length() - 4);
    }

    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendResponse(exchange, statusCode, json.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    /**
     * 发送HTTP响应
     */
    private void sendResponse(HttpExchange exchange, int statusCode, byte[] content, String contentType)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, content.length);

        OutputStream os = exchange.getResponseBody();
        os.write(content);
        os.close();
    }

    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String path) {
        if (path.endsWith(".html"))
            return "text/html";
        if (path.endsWith(".css"))
            return "text/css";
        if (path.endsWith(".js"))
            return "application/javascript";
        if (path.endsWith(".json"))
            return "application/json";
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
            return "image/jpeg";
        if (path.endsWith(".svg"))
            return "image/svg+xml";
        return "text/plain";
    }

    /**
     * 主方法 - 支持独立启动
     */
    public static void main(String[] args) {
        boolean useLLM = false;

        // 解析参数
        for (String arg : args) {
            if ("--llm".equals(arg) || "--use-llm".equals(arg)) {
                useLLM = true;
            }
        }

        // 检查环境变量
        String envUseLLM = System.getenv("USE_LLM");
        if ("true".equalsIgnoreCase(envUseLLM) || "1".equals(envUseLLM)) {
            useLLM = true;
        }

        try {
            SimpleWebServer server = new SimpleWebServer(useLLM);
            server.start();

            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n正在关闭服务器...");
                server.stop();
            }));

            // 保持运行
            System.out.println("按 Ctrl+C 停止服务器");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
