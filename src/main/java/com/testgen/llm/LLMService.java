package com.testgen.llm;

import java.util.Map;

/**
 * 大模型服务接口
 * 提供统一的LLM调用抽象，支持多种大模型平台
 */
public interface LLMService {
    
    /**
     * 调用大模型进行PRD解析
     * 
     * @param prdText PRD原始文本
     * @return 解析后的结构化数据（JSON格式字符串）
     */
    String parsePRD(String prdText);
    
    /**
     * 调用大模型生成测试用例
     * 
     * @param prdData PRD解析后的结构化数据
     * @return 测试用例（JSON格式字符串）
     */
    String generateTestCases(Map<String, Object> prdData);
    
    /**
     * 通用的LLM调用方法
     * 
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return LLM响应内容
     */
    String chat(String systemPrompt, String userPrompt);
    
    /**
     * 获取服务提供商名称
     */
    String getProviderName();
}
