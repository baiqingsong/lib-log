package com.dawn.log;

import android.text.TextUtils;

/**
 * 腾讯云日志服务（CLS）配置
 * <p>
 * 使用 Builder 模式构建，所有参数通过腾讯云 CLS 控制台获取。
 * </p>
 *
 * <pre>
 * CLSConfig config = new CLSConfig.Builder()
 *     .endpoint("ap-guangzhou.cls.tencentcs.com")
 *     .secretId("your-secret-id")
 *     .secretKey("your-secret-key")
 *     .topicId("your-topic-id")
 *     .build();
 * </pre>
 */
public class CLSConfig {

    private final String endpoint;
    private final String secretId;
    private final String secretKey;
    private final String topicId;
    private final String source;

    private CLSConfig(Builder builder) {
        this.endpoint = builder.endpoint;
        this.secretId = builder.secretId;
        this.secretKey = builder.secretKey;
        this.topicId = builder.topicId;
        this.source = builder.source;
    }

    // ==================== Getters ====================

    public String getEndpoint() { return endpoint; }
    public String getSecretId() { return secretId; }
    public String getSecretKey() { return secretKey; }
    public String getTopicId() { return topicId; }
    public String getSource() { return source; }

    /**
     * 校验必填参数
     */
    public boolean isValid() {
        return !TextUtils.isEmpty(secretId)
                && !TextUtils.isEmpty(secretKey)
                && !TextUtils.isEmpty(topicId);
    }

    // ==================== Builder ====================

    public static class Builder {
        // 必填
        private String secretId;
        private String secretKey;
        private String topicId;

        // 可选
        private String endpoint = "ap-guangzhou.cls.tencentcs.com";
        private String source = "";

        /**
         * @param secretId 腾讯云 API 密钥 SecretId
         */
        public Builder secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        /**
         * @param secretKey 腾讯云 API 密钥 SecretKey
         */
        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        /**
         * @param topicId CLS 日志主题 ID
         */
        public Builder topicId(String topicId) {
            this.topicId = topicId;
            return this;
        }

        /**
         * @param endpoint CLS 服务接入点，默认 "ap-guangzhou.cls.tencentcs.com"
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * @param source 日志来源标识，如设备 ID
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public CLSConfig build() {
            if (TextUtils.isEmpty(secretId)) throw new IllegalArgumentException("secretId is required");
            if (TextUtils.isEmpty(secretKey)) throw new IllegalArgumentException("secretKey is required");
            if (TextUtils.isEmpty(topicId)) throw new IllegalArgumentException("topicId is required");
            return new CLSConfig(this);
        }
    }
}
