package com.testgen.feishu;

/**
 * 飞书API异常
 */
public class FeishuException extends Exception {

    public FeishuException(String message) {
        super(message);
    }

    public FeishuException(String message, Throwable cause) {
        super(message, cause);
    }
}
