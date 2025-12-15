package com.testgen.llm;

/**
 * LLM服务工厂
 * 使用火山引擎大模型服务
 */
public class LLMFactory {
    
    /**
     * 创建火山引擎LLM服务实例
     * 
     * @param apiKey API密钥（如为null则使用默认凭证）
     * @return LLM服务实例
     */
    public static LLMService createService(String apiKey) {
        return new VolcanoService(apiKey);
    }
    
    /**
     * 使用默认凭证创建服务
     */
    public static LLMService createService() {
        return new VolcanoService();
    }
    
    /**
     * 创建火山引擎LLM服务实例（自定义配置）
     * 
     * @param apiKey API密钥
     * @param apiUrl API地址
     * @param endpointId 推理接入点ID
     * @return LLM服务实例
     */
    public static LLMService createService(String apiKey, String apiUrl, String endpointId) {
        return new VolcanoService(apiKey, apiUrl, endpointId);
    }
    
    /**
     * 从环境变量创建默认服务
     * 如果未配置环境变量，则使用内置的默认凭证
     */
    public static LLMService createDefaultService() {
        // 从环境变量读取配置
        String apiKey = System.getenv("ARK_API_KEY");
        String apiUrl = System.getenv("VOLCANO_API_URL");
        String endpointId = System.getenv("VOLCANO_ENDPOINT_ID");
        
        // 如果环境变量未设置，使用默认凭证
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("使用内置的火山引擎默认凭证");
            if (apiUrl != null && !apiUrl.trim().isEmpty() && endpointId != null && !endpointId.trim().isEmpty()) {
                return createService(null, apiUrl, endpointId);
            } else {
                return createService();
            }
        }
        
        // 使用环境变量配置
        if (apiUrl != null && !apiUrl.trim().isEmpty() && endpointId != null && !endpointId.trim().isEmpty()) {
            return createService(apiKey, apiUrl, endpointId);
        } else {
            return createService(apiKey);
        }
    }
}
