package com.dawn.log;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * 日志工具类
 * <p>
 * 支持 Logcat 打印和文件写入，文件写入使用后台线程队列，不会阻塞主线程。
 * 日志文件按天生成，自动清理超过 7 天的日志文件。
 * </p>
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
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 单个文件最大 10MB
    private static final int RETENTION_DAYS = 7; // 日志保留天数

    private static final String PATTERN_DATE = "yyyy-MM-dd";
    private static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String LOG_FILE_PREFIX = "log_";
    private static final String LOG_FILE_SUFFIX = ".txt";

    private static String logPath = null;

    // 后台写入线程
    private static HandlerThread writerThread;
    private static Handler writerHandler;

    private static final int MSG_WRITE = 1;
    private static final int MSG_FLUSH = 2;

    // 当前打开的写入流（复用，避免频繁开关文件）
    private static BufferedWriter currentWriter;
    private static String currentFileName;

    private LLog() { }

    // ==================== 初始化 ====================

    /**
     * 初始化日志
     *
     * @param context 上下文
     * @param isDebug 是否打印 Logcat 日志
     * @param tag     默认 TAG
     */
    public static void init(Context context, boolean isDebug, String tag) {
        init(context, isDebug, tag, null);
    }

    /**
     * 初始化日志
     *
     * @param context 上下文
     * @param isDebug 是否打印 Logcat 日志
     * @param tag     默认 TAG
     * @param customLogPath 自定义日志路径，为空则使用默认路径
     */
    public static void init(Context context, boolean isDebug, String tag, String customLogPath) {
        TAG = tag;
        LOG_DEBUG = isDebug;

        if (TextUtils.isEmpty(customLogPath)) {
            logPath = getDefaultLogPath(context);
        } else {
            logPath = customLogPath;
            if (!logPath.endsWith("/")) {
                logPath += "/";
            }
        }

        // 确保目录存在
        File dir = new File(logPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 启动后台写入线程
        startWriterThread();

        // 在后台线程清理过期日志
        if (writerHandler != null) {
            writerHandler.post(new Runnable() {
                @Override
                public void run() {
                    cleanExpiredLogs();
                }
            });
        }
    }

    /**
     * 释放资源（应用退出时调用）
     */
    public static void release() {
        if (writerHandler != null) {
            writerHandler.post(new Runnable() {
                @Override
                public void run() {
                    closeCurrentWriter();
                }
            });
            writerHandler.removeCallbacksAndMessages(null);
            writerHandler = null;
        }
        if (writerThread != null) {
            writerThread.quitSafely();
            writerThread = null;
        }
    }

    // ==================== 日志打印接口 ====================

    public static void v(String msg) {
        printLog(VERBOSE, TAG, msg);
        writeLog(CHAR_VERBOSE, TAG, msg);
    }

    public static void v(String tag, String msg) {
        printLog(VERBOSE, tag, msg);
        writeLog(CHAR_VERBOSE, tag, msg);
    }

    public static void d(String msg) {
        printLog(DEBUG, TAG, msg);
        writeLog(CHAR_DEBUG, TAG, msg);
    }

    public static void d(String tag, String msg) {
        printLog(DEBUG, tag, msg);
        writeLog(CHAR_DEBUG, tag, msg);
    }

    public static void i(Object... msg) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msg.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(msg[i]);
        }
        String str = sb.toString();
        printLog(INFO, TAG, str);
        writeLog(CHAR_INFO, TAG, str);
    }

    public static void w(String msg) {
        printLog(WARN, TAG, msg);
        writeLog(CHAR_WARN, TAG, msg);
    }

    public static void w(String tag, String msg) {
        printLog(WARN, tag, msg);
        writeLog(CHAR_WARN, tag, msg);
    }

    public static void e(String msg) {
        printLog(ERROR, TAG, msg);
        writeLog(CHAR_ERROR, TAG, msg);
    }

    public static void e(String tag, String msg) {
        printLog(ERROR, tag, msg);
        writeLog(CHAR_ERROR, tag, msg);
    }

    public static void e(String msg, Throwable tr) {
        String errorStr = buildErrorMsg(msg, tr);
        printLog(ERROR, TAG, errorStr);
        writeLog(CHAR_ERROR, TAG, errorStr);
    }

    public static void e(String tag, String msg, Throwable tr) {
        String errorStr = buildErrorMsg(msg, tr);
        printLog(ERROR, tag, errorStr);
        writeLog(CHAR_ERROR, tag, errorStr);
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
        return logPath;
    }

    /**
     * 获取所有日志文件列表（按时间从旧到新排序）
     *
     * @return 日志文件列表，如果没有日志文件则返回空列表
     */
    public static List<File> getLogFiles() {
        List<File> result = new ArrayList<>();
        if (logPath == null) return result;

        File dir = new File(logPath);
        if (!dir.exists() || !dir.isDirectory()) return result;

        File[] files = dir.listFiles(file ->
                file.isFile()
                        && file.getName().startsWith(LOG_FILE_PREFIX)
                        && file.getName().endsWith(LOG_FILE_SUFFIX));

        if (files == null || files.length == 0) return result;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        result.addAll(Arrays.asList(files));
        return result;
    }

    /**
     * 手动清理过期日志文件
     */
    public static void cleanExpiredLogs() {
        if (logPath == null) return;

        File dir = new File(logPath);
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        long expireTime = System.currentTimeMillis() - (long) RETENTION_DAYS * 24 * 60 * 60 * 1000;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < expireTime) {
                file.delete();
            }
        }
    }

    /**
     * 清除所有日志文件
     */
    public static void clearAllLogs() {
        if (logPath == null) return;

        // 先关闭当前写入流
        if (writerHandler != null) {
            writerHandler.post(new Runnable() {
                @Override
                public void run() {
                    closeCurrentWriter();
                    File dir = new File(logPath);
                    if (dir.exists() && dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                file.delete();
                            }
                        }
                    }
                }
            });
        }
    }

    // ==================== 后台写入实现 ====================

    private static void startWriterThread() {
        if (writerThread != null && writerThread.isAlive()) return;

        writerThread = new HandlerThread("LLog-Writer");
        writerThread.start();
        writerHandler = new Handler(writerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_WRITE && msg.obj instanceof String) {
                    doWriteToFile((String) msg.obj);
                }
            }
        };
    }

    private static void writeLog(char level, String tag, String msg) {
        if (logPath == null || writerHandler == null) return;

        // 在调用线程组装日志行，减少后台线程工作
        String caller = getCallerInfo();
        String timestamp = getDateTimeFormat().format(new Date());
        String logLine = timestamp + " " + level + "/" + tag + " " + caller + " " + msg + "\n";

        Message message = writerHandler.obtainMessage(MSG_WRITE, logLine);
        writerHandler.sendMessage(message);
    }

    private static void doWriteToFile(String logLine) {
        String fileName = getFileName(new Date());

        try {
            // 如果文件名变了（跨天）或者写入流未打开，重新打开
            if (currentWriter == null || !fileName.equals(currentFileName)) {
                closeCurrentWriter();
                currentFileName = fileName;

                File file = new File(fileName);
                // 文件超过上限则截断重建
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    file.delete();
                }

                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(fileName, true);
                currentWriter = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
            }

            currentWriter.write(logLine);
            currentWriter.flush();

        } catch (IOException e) {
            Log.e(TAG, "Failed to write log to file", e);
            closeCurrentWriter();
        }
    }

    private static void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.flush();
                currentWriter.close();
            } catch (IOException ignored) {
            }
            currentWriter = null;
            currentFileName = null;
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

    private static String getDefaultLogPath(Context context) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                return externalDir.getAbsolutePath() + "/Logs/";
            }
        }
        return context.getFilesDir().getAbsolutePath() + "/Logs/";
    }

    private static String getFileName(Date date) {
        return logPath + LOG_FILE_PREFIX + getDateFormat().format(date) + LOG_FILE_SUFFIX;
    }

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

    private static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat(PATTERN_DATE, Locale.getDefault());
    }

    private static SimpleDateFormat getDateTimeFormat() {
        return new SimpleDateFormat(PATTERN_DATETIME, Locale.getDefault());
    }
}
