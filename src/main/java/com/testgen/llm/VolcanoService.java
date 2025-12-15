package com.testgen.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Color;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/**
 * 火山引擎大模型服务实现（使用官方SDK）
 * 支持豆包系列模型
 */
public class VolcanoService implements LLMService {

    private static final Logger logger = LoggerFactory.getLogger(VolcanoService.class);

    private static final String DEFAULT_API_URL = "https://ark.cn-beijing.volces.com/api/v3";

    // 火山方舟推理接入点ID（Endpoint ID）
    // 默认使用 thinking 模型用于文本生成 (deepseek-3.2)
    private static final String DEFAULT_ENDPOINT_ID = System.getenv("VOLCANO_ENDPOINT_ID") != null
            ? System.getenv("VOLCANO_ENDPOINT_ID")
            : ""; // 请通过环境变量VOLCANO_ENDPOINT_ID配置

    // API Key（火山方舟的访问密钥）
    private static final String DEFAULT_API_KEY = System.getenv("ARK_API_KEY") != null
            ? System.getenv("ARK_API_KEY")
            : ""; // 请通过环境变量ARK_API_KEY配置

    private final String endpointId;
    private final ArkService arkService;
    private final ObjectMapper objectMapper;

    public VolcanoService(String apiKey) {
        this(apiKey, DEFAULT_API_URL, DEFAULT_ENDPOINT_ID);
    }

    /**
     * 使用默认配置创建服务
     */
    public VolcanoService() {
        this(DEFAULT_API_KEY, DEFAULT_API_URL, DEFAULT_ENDPOINT_ID);
    }

    /**
     * @param apiKey     API密钥（ARK_API_KEY）
     * @param baseUrl    API地址
     * @param endpointId 推理接入点ID
     */
    public VolcanoService(String apiKey, String baseUrl, String endpointId) {
        if (endpointId == null || endpointId.trim().isEmpty()) {
            throw new RuntimeException(
                    "火山方舟推理接入点ID未配置！\n" +
                            "当前endpoint ID为空，请在 VolcanoService.java 中设置 DEFAULT_ENDPOINT_ID");
        }

        // 检查API Key
        if (apiKey == null || apiKey.trim().isEmpty() || "your-ark-api-key-here".equals(apiKey)) {
            throw new RuntimeException(
                    "火山引擎API Key未配置！\n" +
                            "错误提示: missing api_key or ak&sk\n" +
                            "\n请按以下步骤配置：\n" +
                            "1. 访问 https://console.volcengine.com/ark/region:ark+cn-beijing/endpoint\n" +
                            "2. 找到你的接入点: " + endpointId + "\n" +
                            "3. 获取API密钥（API Key）\n" +
                            "4. 配置方式任选其一：\n" +
                            "   方式1（推荐）: 设置环境变量 set ARK_API_KEY=你的API密钥\n" +
                            "   方式2: 在 VolcanoService.java 第30行修改 DEFAULT_API_KEY\n" +
                            "\n提示: 公开接入点也需要API Key才能调用");
        }

        this.endpointId = endpointId;
        this.objectMapper = new ObjectMapper();

        // 初始化火山引擎官方SDK
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();

        this.arkService = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        logger.info("火山引擎服务初始化成功，接入点ID: {}，已配置API Key", endpointId);
    }

    @Override
    public String parsePRD(String prdText) {
        String systemPrompt = buildPRDParseSystemPrompt();
        String userPrompt = "请解析以下PRD文档：\n\n" + prdText;
        return chat(systemPrompt, userPrompt);
    }

    @Override
    public String generateTestCases(Map<String, Object> prdData) {
        try {
            String systemPrompt = buildTestCaseGenerateSystemPrompt();
            String userPrompt = "基于以下PRD数据生成测试用例：\n\n" +
                    objectMapper.writeValueAsString(prdData);
            return chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("生成测试用例失败", e);
            throw new RuntimeException("生成测试用例失败: " + e.getMessage());
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            // 构建消息列表
            final List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.SYSTEM)
                    .content(systemPrompt)
                    .build());
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(userPrompt)
                    .build());

            // 构建请求（设置足够大的maxTokens避免输出截断）
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(endpointId) // 使用接入点ID
                    .messages(messages)
                    .maxTokens(8192) // 增加输出长度限制，避免JSON被截断
                    .build();

            // 调用API
            logger.info("调用火山方舟API，接入点: {}", endpointId);

            StringBuilder response = new StringBuilder();
            arkService.createChatCompletion(request)
                    .getChoices()
                    .forEach(choice -> response.append(choice.getMessage().getContent()));

            String result = response.toString();
            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("无效的API响应：返回内容为空");
            }

            logger.info("火山方舟API调用成功");
            return result;

        } catch (Exception e) {
            logger.error("调用火山方舟API失败", e);

            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("401") || errorMsg.contains("Unauthorized")) {
                    throw new RuntimeException(
                            "API调用失败: 401 认证失败\n" +
                                    "接入点ID: " + endpointId + "\n" +
                                    "请检查：\n" +
                                    "1. 接入点ID是否正确\n" +
                                    "2. 如果是私有接入点，需要配置环境变量: set ARK_API_KEY=你的API密钥\n" +
                                    "3. 访问 https://console.volcengine.com/ark 查看接入点详情");
                }

                if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
                    throw new RuntimeException(
                            "API调用失败: 404 接入点不存在\n" +
                                    "当前接入点ID: " + endpointId + "\n" +
                                    "请检查：\n" +
                                    "1. 访问 https://console.volcengine.com/ark/region:ark+cn-beijing/endpoint\n" +
                                    "2. 确认接入点ID是否存在且状态正常\n" +
                                    "3. 如果接入点已删除，需要重新创建");
                }
            }

            throw new RuntimeException("调用火山方舟API失败: " + errorMsg);
        }
    }

    @Override
    public String getProviderName() {
        return "火山引擎";
    }

    /**
     * 识别图片中的文字内容（OCR模式，优化token消耗）
     * 使用直接HTTP请求，正确传递multimodal content结构
     * 包含429错误重试逻辑
     * 
     * @param imageBase64 图片Base64数据
     * @param context     上下文信息
     * @return 图片中提取的文字
     */
    public String describeImage(String imageBase64, String context) {
        // 使用视觉模型 endpoint（从环境变量读取）
        String visionEndpointId = System.getenv("VOLCANO_VISION_ENDPOINT_ID") != null
                ? System.getenv("VOLCANO_VISION_ENDPOINT_ID")
                : System.getenv("VOLCANO_ENDPOINT_ID"); // 回退到默认endpoint

        // 重试配置
        int maxRetries = 3;
        long baseDelayMs = 2000; // 基础等待时间2秒

        try {
            // 大幅压缩图片以减少token消耗（目标：最大宽度512px，质量50%）
            String compressedBase64 = compressImageBase64(imageBase64, 512);
            logger.info("图片压缩完成，原始大小: {} chars, 压缩后: {} chars",
                    imageBase64.length(), compressedBase64.length());

            // 使用OkHttp直接调用API，正确构造multimodal消息
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build();

            // 构建符合OpenAI格式的请求体
            String requestJson = buildVisionRequestJson(visionEndpointId, compressedBase64);

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                RequestBody body = RequestBody.create(
                        requestJson,
                        MediaType.parse("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url("https://ark.cn-beijing.volces.com/api/v3/chat/completions")
                        .addHeader("Authorization", "Bearer " + DEFAULT_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                logger.info("调用火山方舟视觉模型API: {} [尝试 {}/{}]", visionEndpointId, attempt, maxRetries);

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    // 处理429速率限制错误
                    if (response.code() == 429) {
                        if (attempt < maxRetries) {
                            long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1); // 指数退避
                            logger.warn("Vision API速率限制(429)，等待{}ms后重试...", delayMs);
                            Thread.sleep(delayMs);
                            continue;
                        } else {
                            logger.error("Vision API速率限制(429)，已达到最大重试次数");
                            return "图片识别失败: API速率限制，请稍后重试";
                        }
                    }

                    if (!response.isSuccessful()) {
                        logger.warn("Vision API调用失败: HTTP {}, body={}", response.code(),
                                responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
                        return "图片识别失败: HTTP " + response.code();
                    }

                    // 解析响应
                    com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(responseBody);
                    if (json.has("choices") && json.get("choices").size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode message = json.get("choices").get(0).get("message");
                        if (message != null && message.has("content")) {
                            String result = message.get("content").asText();
                            // 记录token使用情况
                            if (json.has("usage")) {
                                com.fasterxml.jackson.databind.JsonNode usage = json.get("usage");
                                logger.info("Vision API token使用: prompt={}, completion={}, total={}",
                                        usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : "?",
                                        usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : "?",
                                        usage.has("total_tokens") ? usage.get("total_tokens").asInt() : "?");
                            }
                            return result != null && !result.isEmpty() ? result.trim() : "无法识别图片内容";
                        }
                    }

                    return "无法识别图片内容";
                }
            }

            return "图片识别失败: 超过最大重试次数";
        } catch (Exception e) {
            logger.warn("图片识别失败: {}", e.getMessage(), e);
            return "图片识别失败: " + e.getMessage();
        }
    }

    /**
     * 构建Vision API请求JSON
     * 必须使用正确的multimodal content结构（数组形式）
     */
    private String buildVisionRequestJson(String modelId, String imageBase64) {
        // 手动构建JSON以确保content是数组而不是字符串
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"model\": \"").append(modelId).append("\",\n");
        json.append("  \"messages\": [\n");
        json.append("    {\n");
        json.append("      \"role\": \"user\",\n");
        json.append("      \"content\": [\n");
        json.append("        {\n");
        json.append("          \"type\": \"text\",\n");
        json.append("          \"text\": \"请提取图片中的所有信息，需要识别出图片中流程图的逻辑、图片中的所有文字，输出的文字表述需有助于测试用例编写。**关注约束条件**：对于输入字段，明确提取长度、格式、类型等约束（如最大长度、最小值、允许的特殊字符等）。\"\n");

        json.append("        },\n");
        json.append("        {\n");
        json.append("          \"type\": \"image_url\",\n");
        json.append("          \"image_url\": {\n");
        json.append("            \"url\": \"data:image/jpeg;base64,").append(imageBase64).append("\"\n");
        json.append("          }\n");
        json.append("        }\n");
        json.append("      ]\n");
        json.append("    }\n");
        json.append("  ],\n");
        json.append("  \"max_tokens\": 1000\n"); // 限制输出token
        json.append("}");
        return json.toString();
    }

    /**
     * 压缩图片Base64以减少token消耗
     * 更激进的压缩策略：512px宽度 + 50%质量
     * 
     * @param base64Image 原始Base64图片
     * @param maxWidth    最大宽度（像素）
     * @return 压缩后的Base64图片
     */
    private String compressImageBase64(String base64Image, int maxWidth) {
        try {
            // 解码Base64为字节数组
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage originalImage = ImageIO.read(bis);

            if (originalImage == null) {
                logger.warn("无法解析图片，返回原始数据");
                return base64Image;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // 计算缩放比例，强制缩放到maxWidth以内
            double scale = Math.min(1.0, (double) maxWidth / originalWidth);
            int newWidth = (int) (originalWidth * scale);
            int newHeight = (int) (originalHeight * scale);

            // 创建缩放后的RGB图片（去除透明通道）
            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resizedImage.createGraphics();
            // 填充白色背景（处理透明PNG）
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, newWidth, newHeight);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            logger.info("图片尺寸调整: {}x{} -> {}x{}", originalWidth, originalHeight, newWidth, newHeight);

            // 转换为JPEG格式并使用较低质量压缩（50%）
            return convertToJpeg(resizedImage, 0.5f);

        } catch (Exception e) {
            logger.warn("图片压缩失败，使用原始图片: {}", e.getMessage());
            return base64Image;
        }
    }

    /**
     * 将BufferedImage转换为JPEG格式的Base64字符串
     */
    private String convertToJpeg(BufferedImage image, float quality) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // 使用ImageIO写入JPEG（默认压缩）
            // 如果需要更精细的质量控制，可以使用ImageWriter
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            writer.dispose();
            ios.close();

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            logger.warn("JPEG转换失败: {}", e.getMessage());
            // 回退到PNG格式
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception ex) {
                throw new RuntimeException("图片格式转换失败", ex);
            }
        }
    }

    /**
     * 构建PRD解析的系统提示词
     */
    private String buildPRDParseSystemPrompt() {
        return "你是一位拥有10年经验的高级产品经理和系统架构师。你的任务是深度全面解析产品需求文档(PRD)，提取出用于测试生成的结构化信息。\n\n" +
                "解析要求：\n" +
                "1. **深度挖掘隐含需求**：必须提取所有显式需求，并且推断隐含的边界条件、异常处理和安全需求。\n" +
                "2. **关注约束条件**：对于输入字段，明确提取长度、格式、类型等约束（如最大长度、最小值、允许的特殊字符等）。\n" +
                "3. **必须识别异常场景**：主动思考并列出以下异常情况：\n" +
                "   - 必填字段为空的情况\n" +
                "   - 字段长度超限的情况（超过最大值或低于最小值）\n" +
                "   - 特殊字符输入的情况\n" +
                "   - 并发访问冲突\n" +
                "   - 网络异常/超时\n" +
                "   - 权限不足（401/403）\n" +
                "   - 资源不存在（404）\n" +
                "   - 服务端错误（500）\n" +
                "4. **边界值场景**：对于每个输入字段，必须考虑：\n" +
                "   - 最大值/最小值测试\n" +
                "   - 空值/null测试\n" +
                "   - 零值测试\n" +
                "   - 负数测试（如适用）\n" +
                "5. **场景类型覆盖**：确保提取的信息能够生成以下所有类型的测试场景：\n" +
                "   - FRONTEND（前端UI交互场景）\n" +
                "   - BACKEND（后端API接口场景）\n" +
                "   - INTEGRATION（前后端协同场景）\n" +
                "   - EXCEPTION（异常和边界场景）\n" +
                "6. **结构化输出**：只输出标准的JSON格式，严禁包含Markdown代码块标记（如```json）或其他说明文字。"+
                "请按照以下JSON格式输出解析结果（确保JSON格式合法）：\n" +
                "{\n" +
                "  \"frontendModules\": [\n" +
                "    {\n" +
                "      \"moduleName\": \"模块名称\",\n" +
                "      \"pageElements\": [{\"type\": \"button/input/select/display\", \"id\": \"建议ID\", \"label\": \"元素标签\", \"constraints\": \"长度/格式限制\"}],\n"
                +
                "      \"interactionFlow\": [\"步骤1\", \"步骤2\"],\n" +
                "      \"visualFeedback\": [\"成功提示\", \"错误提示\", \"加载状态\"],\n" +
                "      \"validationRules\": [\"必填校验\", \"格式校验\", \"业务逻辑校验\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"backendModules\": [\n" +
                "    {\n" +
                "      \"moduleName\": \"模块名称\",\n" +
                "      \"interfaces\": [\n" +
                "        {\n" +
                "          \"method\": \"POST/GET\",\n" +
                "          \"path\": \"/api/v1/resource\",\n" +
                "          \"params\": {\"paramName\": \"type(constraints)\"},\n" +
                "          \"response\": {\"code\": \"int\", \"message\": \"string\", \"data\": \"object\"},\n" +
                "          \"securityRequirements\": [\"Auth Token\", \"Permission Check\"],\n" +
                "          \"performanceRequirements\": \"响应时间<200ms\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"businessRules\": [\"核心业务规则1\", \"规则2\"],\n" +
                "      \"exceptionScenarios\": [\"数据库连接失败\", \"并发冲突\", \"数据不存在\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"crossModuleLogics\": [\n" +
                "    {\n" +
                "      \"name\": \"协同场景名称\",\n" +
                "      \"frontendModule\": \"前端模块名\",\n" +
                "      \"backendModule\": \"后端模块名\",\n" +
                "      \"dataFlow\": [\"数据流向描述\"],\n" +
                "      \"stateSync\": [\"状态同步机制\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" ;
    }

    /**
     * 构建测试用例生成的系统提示词
     */
    private String buildTestCaseGenerateSystemPrompt() {
        return "你是一个专业的高级测试开发工程师。基于用户提供的PRD数据（包含需求点、字段约束、业务规则、接口定义、模块划分等完整内容）生成结构化测试用例，核心价值方向为：优先覆盖PRD中的高频核心场景、高风险业务流程及历史缺陷关联模块，确保测试用例的有效性与针对性。PRD数据将通过<PRD_DATA>{{PRD_DATA}}</PRD_DATA>标签传入，你需严格依据该数据执行任务。\n\n" +
                "每个测试用例必须包含以下字段：\n" +
                "- caseId: 用例ID（格式：TC_0001）\n" +
                "- module: 所属模块（需严格从PRD数据中提取官方模块名称，若PRD未明确则按核心功能域划分，命名规则为大驼峰式，如\"UserManagement\"）\n" +
                "- sceneType: 场景类型（FRONTEND/BACKEND/INTEGRATION/EXCEPTION）\n" +
                "- title: 用例标题（需完整清晰，避免截断，命名规范：[模块名称]-[场景类型]-[核心验证点]，如\"UserManagement-FRONTEND-用户登录功能验证\"）\n" +
                "- preCondition: 前置条件列表\n" +
                "- frontEndSteps: 前端操作步骤（每个步骤需明确\"动作+元素定位符+输入值/预期动作结果\"，元素定位符需精确如\"账号输入框#account-input\"）\n" +
                "- backEndSteps: 后端操作步骤（每个步骤需明确API路径、HTTP方法、请求参数）\n" +
                "- frontEndExpected: 前端预期结果\n" +
                "- backEndExpected: 后端预期结果\n" +
                "- assertRules: 断言规则列表（细化断言内容，每条需包含assertType、target、expectedValue）\n" +
                "- priority: 优先级（P0/P1/P2）\n" +
                "- tags: 标签列表\n\n" +
                "生成规则：\n" +
                "1. **场景类型分布要求**：\n" +
                "   - FRONTEND场景：至少占比20%\n" +
                "   - BACKEND场景：至少占比20%\n" +
                "   - INTEGRATION场景：至少占比10%\n" +
                "   - EXCEPTION场景：必须占比20%（异常场景是重点！需覆盖PRD中所有必填字段、权限控制、资源依赖等约束对应的异常类型，每个约束至少生成1条异常用例）\n" +
                "   - 边界值测试用例：必须占比15%（需严格遵循PRD中定义的字段长度、数值范围等约束，最大值取PRD定义上限值+1，最小值取下限值-1，超长字符串取定义长度+1字符）\n" +
                "2. **异常场景根据PRD包含**（标题或内容可体现以下关键词）：\n" +
                "   - 必填字段为空测试\n" +
                "   - 字段长度超限测试（超过最大值或低于最小值）\n" +
                "   - 特殊字符输入测试\n" +
                "   - 权限不足测试（401/403）\n" +
                "   - 资源不存在测试（404）\n" +
                "   - 服务端错误测试（500）\n" +
                "   - 网络超时/断网测试\n" +
                "   - 并发冲突测试\n" +
                "3. **边界值测试根据PRD包含**（标题或内容需体现以下关键词）：\n" +
                "   - 最大值/最小值测试\n" +
                "   - 空值/null测试\n" +
                "   - 零值测试\n" +
                "   - 负数测试\n" +
                "   - 超长字符串测试\n" +
                "4. **测试步骤要求**：\n" +
                "   - 每个用例3-8个步骤为最佳\n" +
                "   - 具体描述测试步骤，确保步骤可执行性\n" +
                "   - 前端步骤：必须将模糊动作拆分为具体操作（如\"点击账号输入框#account-input输入有效账号\"），必须明确元素定位符（格式为\"元素名称+选择器\"，如\"密码输入框#password-input\"）\n" +
                "   - 后端步骤：必须包含完整接口调用信息（API路径、HTTP方法、请求头、请求参数、请求体等）\n" +
                "5. **断言规则要求**：\n" +
                "   - 断言规则必须完全匹配前端、后端预期结果，确保每个验证点都有对应的可落地断言\n" +
                "   - 每条断言必须填写具体内容，禁止为null或模糊描述：assertType（断言类型，如textEquals、textContains、statusEquals、fieldEquals、elementVisible）、target（验证目标，如UI元素定位符、接口返回状态码、接口返回字段）、expectedValue（预期验证值）\n" +
                "6. **前端用例必须包含精确元素定位符**\n" +
                "7. **后端用例必须包含完整的接口调用信息**\n" +
                "8. **确保测试用例可执行性**：步骤无歧义、断言可验证、元素定位准确\n" +
                "9. **场景覆盖要求**：无遗漏关键场景，降低冗余度，避免重复覆盖相同场景\n\n" +
                "只输出JSON数组格式的测试用例，不要包含其他说明文字。";
    }
}
