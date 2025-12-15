package com.testgen.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testgen.llm.LLMService;
import com.testgen.llm.LLMFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * PRD解析器
 * 使用LLM从产品需求文档中提取结构化信息
 */
public class PRDParser {
    private static final Logger logger = LoggerFactory.getLogger(PRDParser.class);

    private ObjectMapper objectMapper = new ObjectMapper();
    private LLMService llmService;

    /**
     * 默认构造函数，自动初始化LLM服务
     */
    public PRDParser() {
        try {
            this.llmService = LLMFactory.createDefaultService();
            logger.info("LLM服务初始化成功，提供商: {}", llmService.getProviderName());
            System.out.println("[PRDParser] LLM服务初始化成功，提供商: " + llmService.getProviderName());
        } catch (Exception e) {
            logger.error("LLM服务初始化失败: {}", e.getMessage());
            throw new RuntimeException("LLM服务初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造函数，兼容旧代码（忽略useLLM参数，始终使用LLM）
     * 
     * @param useLLM 已弃用，始终使用LLM模式
     */
    public PRDParser(boolean useLLM) {
        this();
    }

    /**
     * 构造函数，使用指定LLM服务
     * 
     * @param llmService LLM服务实例
     */
    public PRDParser(LLMService llmService) {
        if (llmService == null) {
            throw new IllegalArgumentException("LLM服务不能为空");
        }
        this.llmService = llmService;
        logger.info("使用指定LLM服务，提供商: {}", llmService.getProviderName());
    }

    /**
     * 解析PRD文本，使用LLM提取结构化信息
     * 
     * @param prdText PRD纯文本内容
     * @return 包含前后端模块、接口定义、协同逻辑的结构化数据
     */
    public Map<String, Object> parsePRD(String prdText) {
        logger.info("开始解析PRD文档，文本长度: {}", prdText.length());
        System.out.println("[PRDParser] 使用LLM解析PRD...");

        try {
            logger.info("调用LLM解析PRD，提供商: {}", llmService.getProviderName());
            System.out.println("[解析] 正在调用火山引擎大模型...");

            // 调用LLM服务
            String llmResponse = llmService.parsePRD(prdText);

            // 清理LLM返回的内容（移除可能的Markdown代码块标记）
            String cleanedResponse = cleanLLMResponse(llmResponse);

            // 解析LLM返回的JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleanedResponse, Map.class);

            // 验证结果结构
            validateResult(result);

            // 输出解析后的PRD结构化数据到日志
            logParsedPRDData(result);

            logger.info("LLM解析完成");
            System.out.println("[解析] LLM解析成功 ✓");
            return result;

        } catch (Exception e) {
            logger.error("LLM解析失败: {}", e.getMessage());

            System.out.println("\n[ERROR] LLM解析失败");
            System.out.println("失败原因: " + e.getMessage());

            if (e.getMessage() != null && e.getMessage().contains("401")) {
                System.out.println("\n解决方案：");
                System.out.println("1. 访问 https://console.volcengine.com/ark 获取API Key");
                System.out.println("2. 运行: set ARK_API_KEY=你的API_KEY");
                System.out.println("3. 重启命令行窗口\n");
            }

            throw new RuntimeException("PRD解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理LLM返回的响应内容
     * 移除Markdown代码块标记和多余空白，并检测/修复截断的JSON
     */
    private String cleanLLMResponse(String response) {
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("LLM返回内容为空");
        }

        String cleaned = response.trim();

        // 移除Markdown代码块标记
        cleaned = cleaned.replaceAll("```json\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*", "");
        cleaned = cleaned.trim();

        // 尝试提取JSON对象（从第一个{到最后一个}）
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        } else if (firstBrace != -1) {
            // 只找到开始的{，JSON被截断
            cleaned = cleaned.substring(firstBrace);
        }

        // 检测JSON是否被截断（检查括号匹配）
        int openBraces = 0, closeBraces = 0;
        int openBrackets = 0, closeBrackets = 0;
        for (char c : cleaned.toCharArray()) {
            if (c == '{')
                openBraces++;
            else if (c == '}')
                closeBraces++;
            else if (c == '[')
                openBrackets++;
            else if (c == ']')
                closeBrackets++;
        }

        if (openBraces != closeBraces || openBrackets != closeBrackets) {
            logger.warn("检测到JSON被截断: 大括号 {}/{}, 方括号 {}/{}",
                    openBraces, closeBraces, openBrackets, closeBrackets);
            System.out.println("[警告] LLM输出被截断，正在尝试修复...");

            // 尝试修复：移除最后一个不完整的元素（找到最后一个完整的逗号或括号）
            // 然后补全缺失的括号
            StringBuilder fixed = new StringBuilder(cleaned);

            // 移除末尾不完整的内容（到最后一个逗号或有效括号）
            String trimmed = cleaned;
            int lastValidPos = Math.max(
                    Math.max(trimmed.lastIndexOf(","), trimmed.lastIndexOf("]")),
                    trimmed.lastIndexOf("}"));
            if (lastValidPos > 0 && lastValidPos < trimmed.length() - 1) {
                trimmed = trimmed.substring(0, lastValidPos + 1);
                // 如果以逗号结尾，移除它
                if (trimmed.endsWith(",")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
            }

            // 重新计算括号
            openBraces = 0;
            closeBraces = 0;
            openBrackets = 0;
            closeBrackets = 0;
            for (char c : trimmed.toCharArray()) {
                if (c == '{')
                    openBraces++;
                else if (c == '}')
                    closeBraces++;
                else if (c == '[')
                    openBrackets++;
                else if (c == ']')
                    closeBrackets++;
            }

            // 补全缺失的括号
            fixed = new StringBuilder(trimmed);
            while (openBrackets > closeBrackets) {
                fixed.append("]");
                closeBrackets++;
            }
            while (openBraces > closeBraces) {
                fixed.append("}");
                closeBraces++;
            }
            cleaned = fixed.toString();
            logger.info("已修复截断的JSON，补全了 {} 个括号",
                    (openBraces - closeBraces) + (openBrackets - closeBrackets));
        }

        logger.debug("清理后的LLM响应长度: {}", cleaned.length());
        return cleaned;
    }

    /**
     * 验证解析结果的结构完整性
     */
    private void validateResult(Map<String, Object> result) {
        // 确保必要的字段存在
        if (!result.containsKey("frontendModules")) {
            result.put("frontendModules", new ArrayList<>());
            logger.warn("LLM返回结果缺少frontendModules字段，已添加空列表");
        }
        if (!result.containsKey("backendModules")) {
            result.put("backendModules", new ArrayList<>());
            logger.warn("LLM返回结果缺少backendModules字段，已添加空列表");
        }
        if (!result.containsKey("crossModuleLogics")) {
            result.put("crossModuleLogics", new ArrayList<>());
            logger.warn("LLM返回结果缺少crossModuleLogics字段，已添加空列表");
        }
    }

    /**
     * 输出PRD解析结果到日志
     */
    @SuppressWarnings("unchecked")
    private void logParsedPRDData(Map<String, Object> result) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========== LLM解析后的PRD结构化数据 ==========\n");

            // 1. 前端模块
            List<Map<String, Object>> frontendModules = 
                (List<Map<String, Object>>) result.getOrDefault("frontendModules", new ArrayList<>());
            sb.append("\n[前端模块] 共 ").append(frontendModules.size()).append(" 个\n");
            for (int i = 0; i < frontendModules.size(); i++) {
                Map<String, Object> module = frontendModules.get(i);
                sb.append("  ").append(i + 1).append(". ").append(module.getOrDefault("name", "未命名"));
                if (module.containsKey("pages")) {
                    List<?> pages = (List<?>) module.get("pages");
                    sb.append(" (页面数: ").append(pages.size()).append(")");
                }
                sb.append("\n");
            }

            // 2. 后端模块
            List<Map<String, Object>> backendModules = 
                (List<Map<String, Object>>) result.getOrDefault("backendModules", new ArrayList<>());
            sb.append("\n[后端模块] 共 ").append(backendModules.size()).append(" 个\n");
            for (int i = 0; i < backendModules.size(); i++) {
                Map<String, Object> module = backendModules.get(i);
                sb.append("  ").append(i + 1).append(". ").append(module.getOrDefault("name", "未命名"));
                if (module.containsKey("apis")) {
                    List<?> apis = (List<?>) module.get("apis");
                    sb.append(" (接口数: ").append(apis.size()).append(")");
                }
                sb.append("\n");
            }

            // 3. 跨模块逻辑
            List<Map<String, Object>> crossModuleLogics = 
                (List<Map<String, Object>>) result.getOrDefault("crossModuleLogics", new ArrayList<>());
            sb.append("\n[跨模块逻辑] 共 ").append(crossModuleLogics.size()).append(" 个\n");
            for (int i = 0; i < crossModuleLogics.size(); i++) {
                Map<String, Object> logic = crossModuleLogics.get(i);
                sb.append("  ").append(i + 1).append(". ").append(logic.getOrDefault("name", "未命名")).append("\n");
            }

            // 4. 输出完整JSON（使用debug级别）
            sb.append("\n[完整JSON数据]\n");
            String jsonStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            sb.append(jsonStr);
            sb.append("\n========== PRD解析结果输出完成 ==========\n");

            // 输出到日志和控制台
            logger.info(sb.toString());
            System.out.println(sb.toString());

        } catch (Exception e) {
            logger.warn("输出PRD解析结果时出错: {}", e.getMessage());
        }
    }

    /**
     * 保存解析结果为JSON文件
     */
    public void saveToJson(String outputPath) throws IOException {
        throw new UnsupportedOperationException("请先调用parsePRD()获取结果后再保存");
    }

    /**
     * 保存指定结果为JSON文件
     */
    public void saveToJson(Map<String, Object> result, String outputPath) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(outputPath), result);
        logger.info("PRD解析结果已保存到: {}", outputPath);
    }
}
