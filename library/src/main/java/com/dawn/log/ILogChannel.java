package com.dawn.log;

/**
 * 日志通道接口
 * <p>
 * 所有日志输出通道（文件写入、远程上传等）均实现此接口，
 * 通过 {@link LLog#addChannel(ILogChannel)} 注册到日志系统中。
 * </p>
 */
public interface ILogChannel {

    /**
     * 初始化通道
     *
     * @param context Android 上下文
     */
    void init(android.content.Context context);

    /**
     * 写入一条日志
     *
     * @param level      日志级别字符，V/D/I/W/E
     * @param tag        日志标签
     * @param message    日志消息体
     * @param callerInfo 调用者信息，格式 [(类名.java:行号)#方法名]
     * @param timestamp  日志时间戳（毫秒）
     */
    void write(char level, String tag, String message, String callerInfo, long timestamp);

    /**
     * 刷新缓冲区，确保所有缓存的日志被持久化/发送
     */
    void flush();

    /**
     * 释放资源
     */
    void release();
}
