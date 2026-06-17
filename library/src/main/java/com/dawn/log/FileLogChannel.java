package com.dawn.log;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 文件日志通道
 * <p>
 * 使用后台线程队列将日志异步写入本地文件，不阻塞主线程。
 * 日志文件按天生成，超过 10MB 自动截断，自动清理超过 7 天的文件。
 * </p>
 */
public class FileLogChannel implements ILogChannel {

    private static final String TAG = "FileLogChannel";

    private static final String PATTERN_DATE = "yyyy-MM-dd";
    private static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String LOG_FILE_PREFIX = "log_";
    private static final String LOG_FILE_SUFFIX = ".txt";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int RETENTION_DAYS = 7;

    private static final int MSG_WRITE = 1;

    private String logPath;
    private String customLogPath;

    private HandlerThread writerThread;
    private Handler writerHandler;

    private BufferedWriter currentWriter;
    private String currentFileName;

    /**
     * 使用默认路径
     */
    public FileLogChannel() { }

    /**
     * 使用自定义路径
     *
     * @param customLogPath 自定义日志目录路径，为空则使用默认路径
     */
    public FileLogChannel(String customLogPath) {
        this.customLogPath = customLogPath;
    }

    @Override
    public void init(Context context) {
        if (!TextUtils.isEmpty(customLogPath)) {
            logPath = customLogPath;
            if (!logPath.endsWith("/")) {
                logPath += "/";
            }
        } else {
            logPath = getDefaultLogPath(context);
        }

        File dir = new File(logPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        startWriterThread();
        cleanExpiredLogs();
    }

    @Override
    public void write(char level, String tag, String message, String callerInfo, long timestamp) {
        if (logPath == null || writerHandler == null) return;

        String timeStr = getDateTimeFormat().format(new Date(timestamp));
        String logLine = timeStr + " " + level + "/" + tag + " " + callerInfo + " " + message + "\n";

        Message msg = writerHandler.obtainMessage(MSG_WRITE, logLine);
        writerHandler.sendMessage(msg);
    }

    @Override
    public void flush() {
        if (writerHandler != null) {
            writerHandler.post(new Runnable() {
                @Override
                public void run() {
                    closeCurrentWriter();
                }
            });
        }
    }

    @Override
    public void release() {
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

    // ==================== 公开的文件管理方法 ====================

    /**
     * 获取日志文件存储目录路径
     */
    public String getLogPath() {
        return logPath;
    }

    /**
     * 获取所有日志文件列表（按时间从旧到新排序）
     */
    public List<File> getLogFiles() {
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
     * 清理超过 7 天的过期日志文件
     */
    public void cleanExpiredLogs() {
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
     * 删除所有日志文件
     */
    public void clearAllLogs() {
        if (logPath == null || writerHandler == null) return;

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

    // ==================== 内部实现 ====================

    private void startWriterThread() {
        if (writerThread != null && writerThread.isAlive()) return;

        writerThread = new HandlerThread("LLog-FileWriter");
        writerThread.start();
        writerHandler = new Handler(writerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_WRITE && msg.obj instanceof String) {
                    doWrite((String) msg.obj);
                }
            }
        };
    }

    private void doWrite(String logLine) {
        String fileName = logPath + LOG_FILE_PREFIX + getDateFormat().format(new Date()) + LOG_FILE_SUFFIX;

        try {
            if (currentWriter == null || !fileName.equals(currentFileName)) {
                closeCurrentWriter();
                currentFileName = fileName;

                File file = new File(fileName);
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

    private void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.flush();
                currentWriter.close();
            } catch (IOException ignored) { }
            currentWriter = null;
            currentFileName = null;
        }
    }

    private String getDefaultLogPath(Context context) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                return externalDir.getAbsolutePath() + "/Logs/";
            }
        }
        return context.getFilesDir().getAbsolutePath() + "/Logs/";
    }

    private SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat(PATTERN_DATE, Locale.getDefault());
    }

    private SimpleDateFormat getDateTimeFormat() {
        return new SimpleDateFormat(PATTERN_DATETIME, Locale.getDefault());
    }
}
