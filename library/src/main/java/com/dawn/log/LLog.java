package com.dawn.log;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * 日志工具类
 * <p>
 * 支持 Logcat 打印 + 多通道日志输出（文件写入、腾讯云 CLS 上传等）。
 * 通过 {@link #addChannel(ILogChannel)} 可注册自定义通道，所有日志自动分发到各通道。
 * </p>
 *
 * <pre>
 * // 仅本地文件（向后兼容）
 * LLog.init(context, true, "MyApp");
 *
 * // 本地文件 + 腾讯云 CLS 双通道
 * CLSConfig clsConfig = new CLSConfig.Builder()
 *     .endpoint("ap-guangzhou.cls.tencentcs.com")
 *     .secretId("your-secret-id")
 *     .secretKey("your-secret-key")
 *     .topicId("your-topic-id")
 *     .build();
 * LLog.init(context, true, "MyApp", clsConfig);
 * </pre>
 */
@SuppressWarnings("unused")
public class LLog {

    private static String TAG = "LLog";
    private static boolean LOG_DEBUG = true;

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final int VERBOSE = 2;
    private static final int DEBUG = 3;
    private static final int INFO = 4;
    private static final int WARN = 5;
    private static final int ERROR = 6;
    private static final int ASSERT = 7;
    private static final int JSON = 8;
    private static final int XML = 9;

    private static final char CHAR_VERBOSE = 'V';
    private static final char CHAR_DEBUG = 'D';
    private static final char CHAR_INFO = 'I';
    private static final char CHAR_WARN = 'W';
    private static final char CHAR_ERROR = 'E';

    private static final int JSON_INDENT = 4;
    private static final int MAX_LOG_LENGTH = 4000;

    // ==================== 通道管理 ====================

    /** 已注册的日志输出通道（线程安全） */
    private static final CopyOnWriteArrayList<ILogChannel> channels = new CopyOnWriteArrayList<>();

    /** 文件通道引用，用于文件管理操作 */
    private static FileLogChannel fileChannel;

    /** 是否已初始化 */
    private static boolean initialized = false;

    private static final SimpleDateFormat dateTimeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    private LLog() { }

    // ==================== 初始化 ====================

    /**
     * 初始化日志（仅本地文件通道，向后兼容）
     *
     * @param context 上下文
     * @param isDebug 是否打印 Logcat 日志
     * @param tag     默认 TAG
     */
    public static void init(Context context, boolean isDebug, String tag) {
        init(context, isDebug, tag, (String) null);
    }

    /**
     * 初始化日志（本地文件通道 + 自定义路径）
     *
     * @param context       上下文
     * @param isDebug       是否打印 Logcat 日志
     * @param tag           默认 TAG
     * @param customLogPath 自定义日志路径，为空则使用默认路径
     */
    public static void init(Context context, boolean isDebug, String tag, String customLogPath) {
        if (initialized) return;

        TAG = tag;
        LOG_DEBUG = isDebug;

        // 注册文件通道
        fileChannel = new FileLogChannel(customLogPath);
        fileChannel.init(context);
        channels.add(fileChannel);

        initialized = true;
    }

    // ==================== 腾讯云 CLS 初始化 ====================

    /**
     * 初始化日志（本地文件 + 腾讯云 CLS 双通道）
     *
     * @param context   上下文
     * @param isDebug   是否打印 Logcat 日志
     * @param tag       默认 TAG
     * @param clsConfig 腾讯云 CLS 配置，为空则不启用 CLS
     */
    public static void init(Context context, boolean isDebug, String tag, CLSConfig clsConfig) {
        init(context, isDebug, tag, (String) null);

        if (clsConfig != null && clsConfig.isValid()) {
            ILogChannel clsChannel = new CLSLogChannel(clsConfig);
            clsChannel.init(context);
            channels.add(clsChannel);
        }
    }

    // ==================== 注册/移除通道 ====================

    /**
     * 注册自定义日志通道
     *
     * @param channel 自定义通道实现
     */
    public static void addChannel(ILogChannel channel) {
        if (channel != null && !channels.contains(channel)) {
            channels.add(channel);
        }
    }

    /**
     * 移除日志通道
     *
     * @param channel 要移除的通道
     */
    public static void removeChannel(ILogChannel channel) {
        if (channel != null) {
            channel.release();
            channels.remove(channel);
        }
    }

    /**
     * 释放所有资源（应用退出时调用）
     */
    public static void release() {
        for (ILogChannel channel : channels) {
            channel.release();
        }
        channels.clear();
        fileChannel = null;
        initialized = false;
    }

    // ==================== 日志打印接口 ====================

    public static void v(String msg) {
        printLog(VERBOSE, TAG, msg);
        dispatchToChannels(CHAR_VERBOSE, TAG, msg);
    }

    public static void v(String tag, String msg) {
        printLog(VERBOSE, tag, msg);
        dispatchToChannels(CHAR_VERBOSE, tag, msg);
    }

    public static void d(String msg) {
        printLog(DEBUG, TAG, msg);
        dispatchToChannels(CHAR_DEBUG, TAG, msg);
    }

    public static void d(String tag, String msg) {
        printLog(DEBUG, tag, msg);
        dispatchToChannels(CHAR_DEBUG, tag, msg);
    }

    public static void i(Object... msg) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msg.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(msg[i]);
        }
        String str = sb.toString();
        printLog(INFO, TAG, str);
        dispatchToChannels(CHAR_INFO, TAG, str);
    }

    public static void w(String msg) {
        printLog(WARN, TAG, msg);
        dispatchToChannels(CHAR_WARN, TAG, msg);
    }

    public static void w(String tag, String msg) {
        printLog(WARN, tag, msg);
        dispatchToChannels(CHAR_WARN, tag, msg);
    }

    public static void e(String msg) {
        printLog(ERROR, TAG, msg);
        dispatchToChannels(CHAR_ERROR, TAG, msg);
    }

    public static void e(String tag, String msg) {
        printLog(ERROR, tag, msg);
        dispatchToChannels(CHAR_ERROR, tag, msg);
    }

    public static void e(String msg, Throwable tr) {
        String errorStr = buildErrorMsg(msg, tr);
        printLog(ERROR, TAG, errorStr);
        dispatchToChannels(CHAR_ERROR, TAG, errorStr);
    }

    public static void e(String tag, String msg, Throwable tr) {
        String errorStr = buildErrorMsg(msg, tr);
        printLog(ERROR, tag, errorStr);
        dispatchToChannels(CHAR_ERROR, tag, errorStr);
    }

    public static void json(String json) {
        printLog(JSON, TAG, json);
    }

    public static void json(String tag, String json) {
        printLog(JSON, tag, json);
    }

    public static void xml(String xml) {
        printLog(XML, TAG, xml);
    }

    public static void xml(String tag, String xml) {
        printLog(XML, tag, xml);
    }

    // ==================== 文件管理接口 ====================

    /**
     * 获取日志文件存储目录
     */
    public static String getLogPath() {
        return fileChannel != null ? fileChannel.getLogPath() : null;
    }

    /**
     * 获取所有日志文件列表（按时间从旧到新排序）
     *
     * @return 日志文件列表，如果没有日志文件则返回空列表
     */
    public static List<File> getLogFiles() {
        return fileChannel != null ? fileChannel.getLogFiles() : new ArrayList<File>();
    }

    /**
     * 手动清理过期日志文件
     */
    public static void cleanExpiredLogs() {
        if (fileChannel != null) {
            fileChannel.cleanExpiredLogs();
        }
    }

    /**
     * 清除所有日志文件
     */
    public static void clearAllLogs() {
        if (fileChannel != null) {
            fileChannel.clearAllLogs();
        }
    }

    // ==================== 通道分发 ====================

    /**
     * 将日志分发到所有已注册的通道
     */
    private static void dispatchToChannels(char level, String tag, String msg) {
        if (channels.isEmpty()) return;

        String caller = getCallerInfo();
        long timestamp = System.currentTimeMillis();

        for (ILogChannel channel : channels) {
            try {
                channel.write(level, tag, msg, caller, timestamp);
            } catch (Exception e) {
                Log.e(TAG, "Error dispatching log to channel: " + channel.getClass().getSimpleName(), e);
            }
        }
    }

    // ==================== Logcat 打印实现 ====================

    private static void printLog(int logType, String tag, Object content) {
        if (!LOG_DEBUG) return;

        if (TextUtils.isEmpty(tag)) tag = TAG;

        String caller = getCallerInfo();
        String msg = (content == null) ? "null" : content.toString();

        switch (logType) {
            case VERBOSE:
            case DEBUG:
            case INFO:
            case WARN:
            case ERROR:
            case ASSERT:
                printDefault(logType, tag, caller + " " + msg);
                break;
            case JSON:
                printJson(tag, msg, caller);
                break;
            case XML:
                printXml(tag, msg, caller);
                break;
        }
    }

    private static void printDefault(int type, String tag, String msg) {
        // 超长日志分段打印
        int index = 0;
        int length = msg.length();
        while (index < length) {
            int end = Math.min(index + MAX_LOG_LENGTH, length);
            String sub = msg.substring(index, end);
            printByLevel(type, tag, sub);
            index = end;
        }
    }

    private static void printByLevel(int type, String tag, String msg) {
        switch (type) {
            case VERBOSE: Log.v(tag, msg); break;
            case DEBUG:   Log.d(tag, msg); break;
            case INFO:    Log.i(tag, msg); break;
            case WARN:    Log.w(tag, msg); break;
            case ERROR:   Log.e(tag, msg); break;
            case ASSERT:  Log.wtf(tag, msg); break;
        }
    }

    private static void printJson(String tag, String json, String caller) {
        if (TextUtils.isEmpty(json)) {
            Log.d(tag, caller + " Empty/Null json content");
            return;
        }

        String message;
        try {
            if (json.startsWith("{")) {
                message = new JSONObject(json).toString(JSON_INDENT);
            } else if (json.startsWith("[")) {
                message = new JSONArray(json).toString(JSON_INDENT);
            } else {
                message = json;
            }
        } catch (JSONException e) {
            message = json;
        }

        Log.d(tag, "╔═══════════════════════════════════════════════════════════════");
        String fullMsg = caller + LINE_SEPARATOR + message;
        String[] lines = fullMsg.split(LINE_SEPARATOR);
        for (String line : lines) {
            Log.d(tag, "║ " + line);
        }
        Log.d(tag, "╚═══════════════════════════════════════════════════════════════");
    }

    private static void printXml(String tag, String xml, String caller) {
        if (xml != null) {
            try {
                Source xmlInput = new StreamSource(new StringReader(xml));
                StreamResult xmlOutput = new StreamResult(new StringWriter());
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(xmlInput, xmlOutput);
                xml = xmlOutput.getWriter().toString().replaceFirst(">", ">\n");
            } catch (Exception e) {
                // 格式化失败，使用原始 xml
            }
        } else {
            xml = "null";
        }

        Log.d(tag, "╔═══════════════════════════════════════════════════════════════");
        String fullMsg = caller + "\n" + xml;
        String[] lines = fullMsg.split(LINE_SEPARATOR);
        for (String line : lines) {
            if (!TextUtils.isEmpty(line)) {
                Log.d(tag, "║ " + line);
            }
        }
        Log.d(tag, "╚═══════════════════════════════════════════════════════════════");
    }

    // ==================== 工具方法 ====================

    private static String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // 回溯到调用方（跳过 getCallerInfo → writeLog/printLog → v/d/i/w/e → 用户调用）
        int targetIndex = -1;
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i].getClassName().equals(LLog.class.getName())) {
                targetIndex = i;
            }
        }
        targetIndex++;
        if (targetIndex >= stackTrace.length) {
            targetIndex = stackTrace.length - 1;
        }

        StackTraceElement element = stackTrace[targetIndex];
        String className = element.getClassName();
        String[] parts = className.split("\\.");
        className = parts[parts.length - 1].replaceAll("\\$\\d+", "");
        return "[(" + className + ".java:" + element.getLineNumber() + ")#" + element.getMethodName() + "]";
    }

    private static String buildErrorMsg(String msg, Throwable tr) {
        if (tr == null) return msg;
        return msg + "\n" + Log.getStackTraceString(tr);
    }
}
