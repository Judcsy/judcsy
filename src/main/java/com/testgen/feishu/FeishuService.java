package com.testgen.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.testgen.llm.VolcanoService;

/**
 * 飞书文档服务
 * 提供获取飞书文档内容（包括文字和图片）的功能
 */
public class FeishuService {

    private static final Logger logger = LoggerFactory.getLogger(FeishuService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 飞书文档URL正则表达式
    private static final Pattern DOCX_URL_PATTERN = Pattern.compile(
            "https?://[\\w.-]+\\.feishu\\.cn/(?:docx|wiki)/([\\w-]+)");
    private static final Pattern DOC_URL_PATTERN = Pattern.compile(
            "https?://[\\w.-]+\\.feishu\\.cn/docs/([\\w-]+)");

    private final OkHttpClient httpClient;
    private VolcanoService volcanoService;

    public FeishuService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        // 尝试初始化LLM服务用于图片识别
        try {
            this.volcanoService = new VolcanoService();
        } catch (Exception e) {
            // LLM服务初始化失败不影响文档获取
            this.volcanoService = null;
        }
    }

    /**
     * 获取飞书文档内容
     * 
     * @param documentUrl 飞书文档链接
     * @return 文档内容（包括图片描述）
     */
    public DocumentContent getDocumentContent(String documentUrl) throws FeishuException {
        // 1. 解析文档URL
        DocumentInfo docInfo = parseDocumentUrl(documentUrl);
        if (docInfo == null) {
            throw new FeishuException("无效的飞书文档链接格式");
        }

        // 2. 获取访问令牌
        String accessToken = getTenantAccessToken();

        // 3. 如果是wiki文档，需要先获取实际的文档token
        String actualDocId = docInfo.documentId;
        String docType = docInfo.type;

        if ("wiki".equals(docInfo.type)) {
            logger.info("检测到wiki文档，正在获取实际文档token...");
            WikiNodeInfo nodeInfo = getWikiNodeInfo(accessToken, docInfo.documentId);
            if (nodeInfo != null) {
                actualDocId = nodeInfo.objToken;
                docType = nodeInfo.objType;
                logger.info("Wiki节点类型: {}, 实际文档ID: {}", docType, actualDocId);
            }
        }

        // 4. 获取文档内容（带图片位置信息）
        String textContent;
        List<ImageInfo> images = new ArrayList<>();

        if ("docx".equals(docType) || "doc".equals(docType) || "wiki".equals(docType)) {
            // 新版文档 (docx) 或 wiki - 使用块解析以获取图片位置
            ContentWithImages contentWithImages = getDocxContentWithImages(accessToken, actualDocId);
            textContent = contentWithImages.content;
            images = contentWithImages.images;
        } else {
            // 旧版文档 (doc)
            textContent = getDocContent(accessToken, actualDocId);
        }

        // 5. 如果有图片，并行下载并使用LLM描述
        if (!images.isEmpty()) {
            processImagesParallel(accessToken, images, actualDocId);
        }

        return new DocumentContent(textContent, images);
    }

    /**
     * 并行处理图片：下载并使用LLM生成描述
     * 优化：使用并行下载 + 控制LLM调用并发度以避免速率限制
     */
    private void processImagesParallel(String accessToken, List<ImageInfo> images, String documentId) {
        int imageCount = images.size();
        logger.info("开始并行处理文档中的 {} 张图片", imageCount);
        System.out.println("[图片处理] 开始并行处理 " + imageCount + " 张图片...");
        
        // 创建线程池：下载使用较多线程，识别使用有限线程避免429
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(Math.min(imageCount, 5));
        // LLM识别使用信号量控制并发，最多2个并发避免429错误
        Semaphore llmSemaphore = new Semaphore(2);
        
        AtomicInteger completedCount = new AtomicInteger(0);
        
        // 第一阶段：并行下载所有图片
        List<CompletableFuture<Void>> downloadFutures = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            final int index = i;
            final ImageInfo img = images.get(i);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    logger.info("并行下载图片 {}/{}: token={}", index + 1, imageCount, img.token);
                    img.base64Data = downloadImageAsBase64(accessToken, img.token, documentId);
                } catch (Exception e) {
                    logger.warn("下载图片 {} 失败: {}", index + 1, e.getMessage());
                    img.base64Data = null;
                }
            }, downloadExecutor);
            
            downloadFutures.add(future);
        }
        
        // 等待所有下载完成
        try {
            CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0])).join();
            logger.info("所有图片下载完成，开始并行识别");
            System.out.println("[图片处理] 下载完成，开始并行识别...");
        } catch (Exception e) {
            logger.warn("部分图片下载失败: {}", e.getMessage());
        }
        
        // 第二阶段：并行识别图片内容（带并发控制）
        List<CompletableFuture<Void>> recognizeFutures = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            final int index = i;
            final ImageInfo img = images.get(i);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    if (img.base64Data != null && !img.base64Data.isEmpty()) {
                        // 获取信号量，控制LLM并发
                        llmSemaphore.acquire();
                        try {
                            // 1. 尝试使用LLM描述图片
                            if (volcanoService != null) {
                                logger.info("并行识别图片 {}/{}", index + 1, imageCount);
                                String description = volcanoService.describeImage(img.base64Data, "飞书PRD文档中的图片");

                                if (description != null && !description.contains("模型暂不支持")
                                        && !description.contains("无法识别") && !description.contains("识别失败")) {
                                    img.description = description;
                                    int done = completedCount.incrementAndGet();
                                    System.out.println("[图片处理] 识别进度: " + done + "/" + imageCount);
                                    return;
                                }
                            }

                            // 2. 如果LLM不可用或不支持，尝试使用飞书OCR
                            logger.info("使用飞书OCR识别图片 {}/{}", index + 1, imageCount);
                            String ocrText = recognizeImageText(accessToken, img.base64Data);
                            if (ocrText != null && !ocrText.isEmpty()) {
                                img.description = "图片包含的文字内容：\n" + ocrText;
                            } else {
                                img.description = "【文档图片 " + (index + 1) + "】(OCR未识别到文字)";
                            }
                        } finally {
                            llmSemaphore.release();
                            // 释放后短暂延迟，避免请求过于密集
                            Thread.sleep(200);
                        }
                    } else {
                        img.description = "【文档图片 " + (index + 1) + "】请在原始飞书文档中查看此图片内容";
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    img.description = "【文档图片 " + (index + 1) + "】处理被中断";
                } catch (Exception e) {
                    logger.warn("识别图片 {} 失败: {}", index + 1, e.getMessage());
                    img.description = "【文档图片 " + (index + 1) + "】识别失败: " + e.getMessage();
                }
                completedCount.incrementAndGet();
            }, downloadExecutor);
            
            recognizeFutures.add(future);
        }
        
        // 等待所有识别完成
        try {
            CompletableFuture.allOf(recognizeFutures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            logger.warn("部分图片识别失败: {}", e.getMessage());
        }
        
        // 关闭线程池
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("所有图片处理完成");
        System.out.println("[图片处理] 全部完成！共处理 " + imageCount + " 张图片");
    }

    /**
     * 使用飞书OCR识别图片文字
     */
    private String recognizeImageText(String accessToken, String imageBase64) {
        String url = FeishuConfig.API_BASE_URL + "/optical_char_recognition/v1/image/basic_recognize";

        String jsonBody = "{\"image\":\"" + imageBase64 + "\"}";

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                logger.warn("OCR识别失败: HTTP {}, body={}", response.code(), responseBody);
                return null;
            }

            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.has("code") ? json.get("code").asInt() : -1;

            if (code != 0) {
                logger.warn("OCR识别API错误: code={}, msg={}", code, json.has("msg") ? json.get("msg").asText() : "");
                return null;
            }

            JsonNode data = json.get("data");
            if (data != null && data.has("text_list")) {
                JsonNode textList = data.get("text_list");
                StringBuilder sb = new StringBuilder();
                for (JsonNode text : textList) {
                    sb.append(text.asText()).append("\n");
                }
                return sb.toString().trim();
            }

            return null;

        } catch (Exception e) {
            logger.warn("OCR识别异常: {}", e.getMessage());
            return null;
        }
    }

    private DocumentInfo parseDocumentUrl(String url) {
        // 尝试匹配 docx/wiki 格式
        Matcher docxMatcher = DOCX_URL_PATTERN.matcher(url);
        if (docxMatcher.find()) {
            String documentId = docxMatcher.group(1);
            String type = url.contains("/wiki/") ? "wiki" : "docx";
            return new DocumentInfo(documentId, type);
        }

        // 尝试匹配旧版 docs 格式
        Matcher docMatcher = DOC_URL_PATTERN.matcher(url);
        if (docMatcher.find()) {
            return new DocumentInfo(docMatcher.group(1), "doc");
        }

        return null;
    }

    /**
     * 获取Wiki节点信息，返回实际的文档token
     * Wiki节点的obj_token才是真正的文档ID
     */
    private WikiNodeInfo getWikiNodeInfo(String accessToken, String nodeToken) {
        // Wiki节点API: GET /wiki/v2/spaces/get_node?token={node_token}
        String url = FeishuConfig.API_BASE_URL + "/wiki/v2/spaces/get_node?token=" + nodeToken;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.has("code") ? json.get("code").asInt() : -1;

            if (code != 0) {
                logger.warn("获取Wiki节点信息失败: code={}", code);
                return null;
            }

            JsonNode data = json.get("data");
            if (data != null && data.has("node")) {
                JsonNode node = data.get("node");
                String objToken = node.has("obj_token") ? node.get("obj_token").asText() : null;
                String objType = node.has("obj_type") ? node.get("obj_type").asText() : "docx";

                if (objToken != null) {
                    logger.info("获取Wiki节点信息成功: obj_type={}, obj_token={}", objType, objToken);
                    return new WikiNodeInfo(objToken, objType);
                }
            }

            return null;

        } catch (IOException e) {
            logger.warn("获取Wiki节点信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Wiki节点信息
     */
    private static class WikiNodeInfo {
        public final String objToken;
        public final String objType;

        public WikiNodeInfo(String objToken, String objType) {
            this.objToken = objToken;
            this.objType = objType;
        }
    }

    /**
     * 获取 tenant_access_token
     */
    public String getTenantAccessToken() throws FeishuException {
        // 检查缓存
        String cachedToken = FeishuConfig.getCachedToken();
        if (cachedToken != null) {
            return cachedToken;
        }

        // 检查配置
        if (!FeishuConfig.isConfigured()) {
            throw new FeishuException("飞书应用未配置，请设置 App ID 和 App Secret");
        }

        String url = FeishuConfig.API_BASE_URL + "/auth/v3/tenant_access_token/internal";

        String jsonBody = String.format(
                "{\"app_id\":\"%s\",\"app_secret\":\"%s\"}",
                FeishuConfig.getAppId(),
                FeishuConfig.getAppSecret());

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.has("code") ? json.get("code").asInt() : -1;

            if (code != 0) {
                String msg = json.has("msg") ? json.get("msg").asText() : "未知错误";
                throw new FeishuException("获取访问令牌失败: " + msg + " (code: " + code + ")");
            }

            String token = json.get("tenant_access_token").asText();
            int expire = json.get("expire").asInt();

            // 缓存令牌
            FeishuConfig.cacheToken(token, expire);

            logger.info("获取飞书访问令牌成功，有效期: {}秒", expire);
            return token;

        } catch (IOException e) {
            throw new FeishuException("网络请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取新版文档(docx)内容
     */
    private String getDocxContent(String accessToken, String documentId) throws FeishuException {
        String url = FeishuConfig.API_BASE_URL + "/docx/v1/documents/" + documentId + "/raw_content";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.has("code") ? json.get("code").asInt() : -1;

            if (code != 0) {
                String msg = json.has("msg") ? json.get("msg").asText() : "未知错误";
                throw new FeishuException("获取文档内容失败: " + msg + " (code: " + code + ")");
            }

            JsonNode data = json.get("data");
            if (data != null && data.has("content")) {
                return data.get("content").asText();
            }

            return "";

        } catch (IOException e) {
            throw new FeishuException("获取文档内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文档内容和图片信息（按块顺序解析）
     * 图片会以占位符形式插入到文本中对应位置
     */
    private ContentWithImages getDocxContentWithImages(String accessToken, String documentId) throws FeishuException {
        StringBuilder contentBuilder = new StringBuilder();
        List<ImageInfo> images = new ArrayList<>();
        int imageIndex = 0;
        
        String url = FeishuConfig.API_BASE_URL + "/docx/v1/documents/" + documentId + "/blocks";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.has("code") ? json.get("code").asInt() : -1;

            if (code != 0) {
                logger.warn("获取文档块信息失败: code={}", code);
                // 降级到raw_content API
                String rawContent = getDocxContent(accessToken, documentId);
                return new ContentWithImages(rawContent, images);
            }

            JsonNode data = json.get("data");
            if (data != null && data.has("items")) {
                JsonNode items = data.get("items");
                for (JsonNode item : items) {
                    int blockType = item.has("block_type") ? item.get("block_type").asInt() : 0;
                    
                    switch (blockType) {
                        case 2: // text 文本块
                            String text = extractTextFromBlock(item, "text");
                            if (text != null && !text.isEmpty()) {
                                contentBuilder.append(text).append("\n");
                            }
                            break;
                        case 3: // heading1
                            String h1 = extractTextFromBlock(item, "heading1");
                            if (h1 != null && !h1.isEmpty()) {
                                contentBuilder.append("# ").append(h1).append("\n");
                            }
                            break;
                        case 4: // heading2
                            String h2 = extractTextFromBlock(item, "heading2");
                            if (h2 != null && !h2.isEmpty()) {
                                contentBuilder.append("## ").append(h2).append("\n");
                            }
                            break;
                        case 5: // heading3
                            String h3 = extractTextFromBlock(item, "heading3");
                            if (h3 != null && !h3.isEmpty()) {
                                contentBuilder.append("### ").append(h3).append("\n");
                            }
                            break;
                        case 6: // heading4
                            String h4 = extractTextFromBlock(item, "heading4");
                            if (h4 != null && !h4.isEmpty()) {
                                contentBuilder.append("#### ").append(h4).append("\n");
                            }
                            break;
                        case 7: // heading5
                            String h5 = extractTextFromBlock(item, "heading5");
                            if (h5 != null && !h5.isEmpty()) {
                                contentBuilder.append("##### ").append(h5).append("\n");
                            }
                            break;
                        case 8: // heading6
                            String h6 = extractTextFromBlock(item, "heading6");
                            if (h6 != null && !h6.isEmpty()) {
                                contentBuilder.append("###### ").append(h6).append("\n");
                            }
                            break;
                        case 9: // heading7
                            String h7 = extractTextFromBlock(item, "heading7");
                            if (h7 != null && !h7.isEmpty()) {
                                contentBuilder.append("####### ").append(h7).append("\n");
                            }
                            break;
                        case 10: // heading8
                            String h8 = extractTextFromBlock(item, "heading8");
                            if (h8 != null && !h8.isEmpty()) {
                                contentBuilder.append("######## ").append(h8).append("\n");
                            }
                            break;
                        case 11: // heading9
                            String h9 = extractTextFromBlock(item, "heading9");
                            if (h9 != null && !h9.isEmpty()) {
                                contentBuilder.append("######### ").append(h9).append("\n");
                            }
                            break;
                        case 12: // bullet 无序列表
                            String bullet = extractTextFromBlock(item, "bullet");
                            if (bullet != null && !bullet.isEmpty()) {
                                contentBuilder.append("• ").append(bullet).append("\n");
                            }
                            break;
                        case 13: // ordered 有序列表
                            String ordered = extractTextFromBlock(item, "ordered");
                            if (ordered != null && !ordered.isEmpty()) {
                                contentBuilder.append("• ").append(ordered).append("\n");
                            }
                            break;
                        case 14: // code 代码块
                            String codeBlock = extractTextFromBlock(item, "code");
                            if (codeBlock != null && !codeBlock.isEmpty()) {
                                contentBuilder.append(codeBlock).append("\n");
                            }
                            break;
                        case 15: // quote 引用
                            String quote = extractTextFromBlock(item, "quote");
                            if (quote != null && !quote.isEmpty()) {
                                contentBuilder.append("> ").append(quote).append("\n");
                            }
                            break;
                        case 27: // image 图片块
                            JsonNode imageBlock = item.get("image");
                            if (imageBlock != null && imageBlock.has("token")) {
                                String token = imageBlock.get("token").asText();
                                String blockId = item.has("block_id") ? item.get("block_id").asText() : "";
                                images.add(new ImageInfo(token, blockId, imageIndex));
                                // 在内容中插入图片占位符
                                contentBuilder.append("{{IMAGE_PLACEHOLDER_").append(imageIndex).append("}}\n");
                                imageIndex++;
                            }
                            break;
                        default:
                            // 其他类型块尝试提取文本（尝试多个字段）
                            String otherText = extractTextFromBlock(item, "text");
                            if (otherText == null || otherText.isEmpty()) {
                                otherText = extractTextFromBlock(item, "paragraph");
                            }
                            if (otherText != null && !otherText.isEmpty()) {
                                contentBuilder.append(otherText).append("\n");
                            }
                            break;
                    }
                }
            }

            logger.info("文档解析完成: {} 个文本块, {} 张图片", 
                contentBuilder.length() > 0 ? "有" : "无", images.size());
            return new ContentWithImages(contentBuilder.toString(), images);

        } catch (IOException e) {
            logger.warn("获取文档块信息失败: {}", e.getMessage());
            // 降级到raw_content API
            String rawContent = getDocxContent(accessToken, documentId);
            return new ContentWithImages(rawContent, images);
        }
    }
    
    /**
     * 从文档块中提取文本内容
     * @param block 文档块节点
     * @param fieldName 要提取文本的字段名（如 text, heading1, bullet 等）
     */
    private String extractTextFromBlock(JsonNode block, String fieldName) {
        // 尝试从指定字段提取
        JsonNode fieldNode = block.get(fieldName);
        if (fieldNode != null && fieldNode.has("elements")) {
            StringBuilder sb = new StringBuilder();
            JsonNode elements = fieldNode.get("elements");
            for (JsonNode element : elements) {
                if (element.has("text_run")) {
                    JsonNode textRun = element.get("text_run");
                    if (textRun.has("content")) {
                        sb.append(textRun.get("content").asText());
                    }
                } else if (element.has("mention_user")) {
                    // 处理@用户
                    JsonNode mentionUser = element.get("mention_user");
                    if (mentionUser.has("user_id")) {
                        sb.append("@用户");
                    }
                } else if (element.has("mention_doc")) {
                    // 处理文档引用
                    JsonNode mentionDoc = element.get("mention_doc");
                    if (mentionDoc.has("title")) {
                        sb.append("[").append(mentionDoc.get("title").asText()).append("]");
                    }
                }
            }
            return sb.toString();
        }
            
        // 如果指定字段不存在，尝试直接从elements提取（某些块类型）
        if (fieldNode != null && fieldNode.has("content")) {
            return fieldNode.get("content").asText();
        }
            
        return null;
    }
    
    /**
     * 内容与图片信息的临时容器
     */
    private static class ContentWithImages {
        final String content;
        final List<ImageInfo> images;
        
        ContentWithImages(String content, List<ImageInfo> images) {
            this.content = content;
            this.images = images;
        }
    }

    /**
     * 获取旧版文档(doc)内容
     */
    private String getDocContent(String accessToken, String documentId) throws FeishuException {
        String url = FeishuConfig.API_BASE_URL + "/doc/v2/" + documentId + "/raw_content";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            JsonNode json = objectMapper.readTree(responseBody);
            int code = json.has("code") ? json.get("code").asInt() : -1;

            if (code != 0) {
                String msg = json.has("msg") ? json.get("msg").asText() : "未知错误";
                throw new FeishuException("获取文档内容失败: " + msg + " (code: " + code + ")");
            }

            JsonNode data = json.get("data");
            if (data != null && data.has("content")) {
                return data.get("content").asText();
            }

            return "";

        } catch (IOException e) {
            throw new FeishuException("获取文档内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载图片并转换为Base64
     * 飞书docx文档图片需要正确的API调用方式
     */
    public String downloadImageAsBase64(String accessToken, String fileToken, String documentId)
            throws FeishuException {

        // 方法1: 直接使用 file_token 下载（标准方式）
        String baseUrl = FeishuConfig.API_BASE_URL + "/drive/v1/medias/" + fileToken + "/download";

        logger.info("尝试下载图片, token: {}", fileToken);

        // 首先尝试不带extra参数
        byte[] imageData = tryDownload(accessToken, baseUrl);

        // 如果失败，尝试带extra参数
        if (imageData == null && documentId != null && !documentId.isEmpty()) {
            try {
                // 尝试格式1: 使用 obj_type 和 obj_token
                String extra1 = String.format("{\"obj_type\":\"docx\",\"obj_token\":\"%s\"}", documentId);
                String url1 = baseUrl + "?extra=" + java.net.URLEncoder.encode(extra1, "UTF-8");
                logger.info("尝试带extra参数下载(格式1)");
                imageData = tryDownload(accessToken, url1);

                // 如果格式1失败，尝试格式2
                if (imageData == null) {
                    String extra2 = String.format("{\"doc_token\":\"%s\"}", documentId);
                    String url2 = baseUrl + "?extra=" + java.net.URLEncoder.encode(extra2, "UTF-8");
                    logger.info("尝试带extra参数下载(格式2)");
                    imageData = tryDownload(accessToken, url2);
                }
            } catch (Exception e) {
                logger.warn("构建extra参数失败: {}", e.getMessage());
            }
        }

        if (imageData != null && imageData.length > 0) {
            logger.info("图片下载成功，大小: {} bytes", imageData.length);
            return Base64.getEncoder().encodeToString(imageData);
        }

        throw new FeishuException("下载图片失败: 所有尝试均失败");
    }

    /**
     * 尝试下载图片，包含重试机制和频率控制
     * 
     * @return 图片数据，失败返回null
     */
    private byte[] tryDownload(String accessToken, String url) {
        int maxRetries = 3;
        int retryDelayMs = 1000; // 初始延迟1秒
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    byte[] data = response.body() != null ? response.body().bytes() : new byte[0];
                    if (data.length > 0) {
                        return data;
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.debug("下载失败 HTTP {}: {}",
                            response.code(),
                            errorBody.length() > 100 ? errorBody.substring(0, 100) : errorBody);
                    
                    // 检查是否是频率限制错误
                    if (response.code() == 429 || (response.code() == 400 && errorBody.contains("frequency limit"))) {
                        if (attempt < maxRetries) {
                            // 指数退避
                            long sleepTime = retryDelayMs * (long)Math.pow(2, attempt - 1);
                            logger.warn("请求频率受限，{}秒后重试 (第{}次尝试)", sleepTime / 1000.0, attempt + 1);
                            Thread.sleep(sleepTime);
                        }
                    }
                }
            } catch (IOException e) {
                logger.debug("下载请求异常: {}", e.getMessage());
                if (attempt < maxRetries) {
                    long sleepTime = retryDelayMs * (long)Math.pow(2, attempt - 1);
                    logger.warn("网络异常，{}秒后重试 (第{}次尝试)", sleepTime / 1000.0, attempt + 1);
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    // ========== 内部类 ==========

    /**
     * 文档信息
     */
    public static class DocumentInfo {
        public final String documentId;
        public final String type; // docx, doc, wiki

        public DocumentInfo(String documentId, String type) {
            this.documentId = documentId;
            this.type = type;
        }
    }

    /**
     * 图片信息
     */
    public static class ImageInfo {
        public final String token;
        public final String blockId;
        public final int position; // 图片在文档中的位置索引
        public String base64Data;
        public String description;

        public ImageInfo(String token, String blockId, int position) {
            this.token = token;
            this.blockId = blockId;
            this.position = position;
        }
    }

    /**
     * 文档内容
     */
    public static class DocumentContent {
        public final String textContent;
        public final List<ImageInfo> images;
        // 图片占位符映射：占位符 -> 图片索引
        private final Map<String, Integer> imagePlaceholders;

        public DocumentContent(String textContent, List<ImageInfo> images) {
            this.textContent = textContent;
            this.images = images;
            this.imagePlaceholders = new HashMap<>();
            // 构建占位符映射
            for (int i = 0; i < images.size(); i++) {
                imagePlaceholders.put("{{IMAGE_PLACEHOLDER_" + i + "}}", i);
            }
        }

        /**
         * 获取完整内容（包含图片描述）
         * 图片描述会被插入到PRD原文中对应的位置
         */
        public String getFullContent() {
            String result = textContent;
            
            // 替换所有图片占位符为实际的图片描述
            for (int i = 0; i < images.size(); i++) {
                ImageInfo img = images.get(i);
                String placeholder = "{{IMAGE_PLACEHOLDER_" + i + "}}";
                String imageContent;
                if (img.description != null && !img.description.isEmpty()) {
                    imageContent = String.format("\n\n--- 图片内容 %d ---\n%s\n--- 图片内容结束 ---\n\n", i + 1, img.description);
                } else {
                    imageContent = String.format("\n\n[图片 %d]: (图片内容待识别)\n\n", i + 1);
                }
                result = result.replace(placeholder, imageContent);
            }

            return result;
        }
    }
}
