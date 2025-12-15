package com.testgen.feishu;

/**
 * 飞书开放平台配置
 * 支持从环境变量或直接设置获取凭证
 */
public class FeishuConfig {

    // 飞书开放平台API基础URL
    public static final String API_BASE_URL = "https://open.feishu.cn/open-apis";

    // 默认凭证（请通过环境变量配置）
    private static String appId = ""; // 请设置环境变量FEISHU_APP_ID
    private static String appSecret = ""; // 请设置环境变量FEISHU_APP_SECRET

    // 缓存的访问令牌
    private static String tenantAccessToken = null;
    private static long tokenExpireTime = 0;

    /**
     * 获取App ID
     * 优先从环境变量读取，否则使用默认值
     */
    public static String getAppId() {
        String envAppId = System.getenv("FEISHU_APP_ID");
        return envAppId != null && !envAppId.isEmpty() ? envAppId : appId;
    }

    /**
     * 获取App Secret
     * 优先从环境变量读取，否则使用默认值
     */
    public static String getAppSecret() {
        String envAppSecret = System.getenv("FEISHU_APP_SECRET");
        return envAppSecret != null && !envAppSecret.isEmpty() ? envAppSecret : appSecret;
    }

    /**
     * 动态设置App ID
     */
    public static void setAppId(String id) {
        appId = id;
        // 清除token缓存
        tenantAccessToken = null;
        tokenExpireTime = 0;
    }

    /**
     * 动态设置App Secret
     */
    public static void setAppSecret(String secret) {
        appSecret = secret;
        // 清除token缓存
        tenantAccessToken = null;
        tokenExpireTime = 0;
    }

    /**
     * 检查配置是否完整
     */
    public static boolean isConfigured() {
        String id = getAppId();
        String secret = getAppSecret();
        return id != null && !id.isEmpty() && secret != null && !secret.isEmpty();
    }

    /**
     * 获取缓存的访问令牌
     */
    public static String getCachedToken() {
        if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return tenantAccessToken;
        }
        return null;
    }

    /**
     * 缓存访问令牌
     * 
     * @param token         访问令牌
     * @param expireSeconds 过期时间（秒）
     */
    public static void cacheToken(String token, int expireSeconds) {
        tenantAccessToken = token;
        // 提前5分钟过期，避免边界情况
        tokenExpireTime = System.currentTimeMillis() + (expireSeconds - 300) * 1000L;
    }

    /**
     * 清除令牌缓存
     */
    public static void clearTokenCache() {
        tenantAccessToken = null;
        tokenExpireTime = 0;
    }
}
