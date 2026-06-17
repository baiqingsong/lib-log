package com.dawn.log;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.tencentcloudapi.cls.android.producer.AsyncProducerClient;
import com.tencentcloudapi.cls.android.producer.AsyncProducerConfig;
import com.tencentcloudapi.cls.android.producer.common.LogContent;
import com.tencentcloudapi.cls.android.producer.common.LogItem;
import com.tencentcloudapi.cls.android.producer.util.NetworkUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 腾讯云日志服务（CLS）通道
 * <p>
 * 将日志实时上传到腾讯云 CLS，基于官方 tencentcloud-cls-producer SDK。
 * 使用异步 Producer，内置批量聚合、重试机制，不阻塞调用线程。
 * </p>
 *
 * <h3>异常隔离</h3>
 * <ul>
 *   <li>单条日志发送失败不影响后续日志</li>
 *   <li>网络恢复后自动继续上传</li>
 *   <li>失败日志由 FileLogChannel 本地兜底</li>
 * </ul>
 */
public class CLSLogChannel implements ILogChannel {

    private static final String TAG = "CLSLogChannel";
    private static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** release() 时等待 flush 完成的超时 */
    private static final long SHUTDOWN_TIMEOUT_MS = 5000;

    // ==================== ThreadLocal 解决 SimpleDateFormat 线程安全问题 ====================

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat(PATTERN_DATETIME, Locale.getDefault());
                }
            };

    // ==================== 实例字段 ====================

    private final CLSConfig config;
    private volatile AsyncProducerClient client;
    private String topicId;
    private String source;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** 成功发送计数 */
    private final AtomicLong successCount = new AtomicLong(0);
    /** 发送失败计数 */
    private final AtomicLong failCount = new AtomicLong(0);
    /** 因缓冲区满丢弃计数 */
    private final AtomicLong droppedCount = new AtomicLong(0);
    /** 上次打印丢弃告警时的总计数 */
    private volatile long lastWarnTotal = 0;

    public CLSLogChannel(CLSConfig config) {
        this.config = config;
    }

    // ==================== ILogChannel 实现 ====================

    @Override
    public void init(Context context) {
        if (!config.isValid()) {
            Log.w(TAG, "CLSConfig is invalid, CLSLogChannel will be disabled.");
            return;
        }

        if (initialized.getAndSet(true)) return;

        this.topicId = config.getTopicId();
        this.source = TextUtils.isEmpty(config.getSource()) ? getDeviceId(context) : config.getSource();

        try {
            // 先尝试获取本机 IP，模拟器上 getNetworkInterfaces() 可能返回 null
            String localIp;
            try {
                localIp = NetworkUtils.getLocalMachineIP();
            } catch (Exception e) {
                // 模拟器环境 NetworkInterface.getNetworkInterfaces() 返回 null 导致 NPE
                Log.w(TAG, "getLocalMachineIP failed (likely emulator), using fallback: " + e.getMessage());
                localIp = "10.0.2.15"; // Android 模拟器默认网关 IP
            }

            AsyncProducerConfig producerConfig = new AsyncProducerConfig(
                    context,
                    config.getEndpoint(),
                    config.getSecretId(),
                    config.getSecretKey(),
                    "",
                    localIp
            );

            // 注意：AsyncProducerClient 构造函数内部也会调用 getLocalMachineIP()，
            // 在模拟器上可能仍然失败，这是 CLS SDK 的已知限制
            client = new AsyncProducerClient(producerConfig);
            Log.i(TAG, "CLSLogChannel initialized: endpoint=" + config.getEndpoint()
                    + ", topicId=" + topicId);
        } catch (NullPointerException e) {
            // 模拟器上 NetworkInterface.getNetworkInterfaces() 返回 null，
            // 这是 CLS SDK 内部调用 getLocalMachineIP() 导致的已知问题
            Log.e(TAG, "CLS init failed: NetworkInterface unavailable (emulator?). "
                    + "CLS will be disabled, local logging still works.", e);
            client = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CLSLogChannel", e);
            client = null;
        }
    }

    @Override
    public void write(char level, String tag, String message, String callerInfo, long timestamp) {
        AsyncProducerClient c = client; // volatile read 一次
        if (c == null || topicId == null) return;

        try {
            List<LogItem> logItems = new ArrayList<>(1);
            int ts = (int) (timestamp / 1000);
            LogItem item = new LogItem(ts);

            String readableTime = DATE_FORMAT.get().format(new Date(timestamp));

            item.PushBack(new LogContent("level", String.valueOf(level)));
            item.PushBack(new LogContent("tag", tag));
            item.PushBack(new LogContent("message", message));
            item.PushBack(new LogContent("caller", callerInfo));
            item.PushBack(new LogContent("timestamp", readableTime));
            item.PushBack(new LogContent("device_id", source));

            logItems.add(item);

            c.putLogs(topicId, logItems, result -> {
                if (result != null && result.isSuccessful()) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                    if (failCount.get() <= 3 || failCount.get() % 100 == 0) {
                        String errMsg = result != null ? result.getErrorMessage() : "unknown";
                        Log.w(TAG, "CLS send failed (count=" + failCount.get()
                                + "): " + errMsg);
                    }
                }
            });
        } catch (IllegalStateException e) {
            // CLS SDK 内部队列满时抛出（取决于 SDK 版本）
            droppedCount.incrementAndGet();
            checkDropWarning();
        } catch (RuntimeException e) {
            // 其他 CLS SDK 运行时异常（如序列化错误、网络异常等）
            failCount.incrementAndGet();
            if (failCount.get() <= 3) {
                Log.e(TAG, "CLS runtime error (count=" + failCount.get() + ")", e);
            }
        } catch (Exception e) {
            failCount.incrementAndGet();
            if (failCount.get() <= 3) {
                Log.e(TAG, "Unexpected error sending log to CLS", e);
            }
        }
    }

    @Override
    public void flush() {
        // CLS SDK AsyncProducerClient 内部自动批量发送，无需显式 flush
        // 如果 SDK 版本支持 flush，在此调用
    }

    @Override
    public void release() {
        AsyncProducerClient c = client;
        if (c == null) return;

        client = null; // 先置空，防止 write() 继续提交
        initialized.set(false);

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            new Thread("CLS-shutdown") {
                @Override
                public void run() {
                    try {
                        // CLS SDK 的 close() 内部会等待积压日志发送完毕
                        c.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error during CLS close", e);
                    } finally {
                        latch.countDown();
                    }
                }
            }.start();

            boolean finished = latch.await(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                Log.w(TAG, "CLS shutdown timed out after " + SHUTDOWN_TIMEOUT_MS + "ms, forcing close");
                try {
                    c.close();
                } catch (Exception ignored) { }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "CLS shutdown interrupted");
            try {
                c.close();
            } catch (Exception ignored) { }
        }

        Log.i(TAG, "CLSLogChannel released. stats: success=" + successCount.get()
                + ", failed=" + failCount.get()
                + ", dropped=" + droppedCount.get());
    }

    // ==================== 监控接口 ====================

    /**
     * 获取成功发送计数
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取发送失败计数
     */
    public long getFailCount() {
        return failCount.get();
    }

    /**
     * 获取因缓冲区满丢弃的日志数
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 重置统计计数器
     */
    public void resetStats() {
        successCount.set(0);
        failCount.set(0);
        droppedCount.set(0);
        lastWarnTotal = 0;
    }

    // ==================== 内部方法 ====================

    /**
     * 检查丢弃比例，超过阈值时打印告警
     */
    private void checkDropWarning() {
        long total = successCount.get() + droppedCount.get() + failCount.get();
        if (total == 0 || total - lastWarnTotal < 100) return;

        long drops = droppedCount.get();
        double ratio = (double) drops / total;
        if (ratio > 0.01) { // 1%
            Log.w(TAG, "CLS drop rate high: " + String.format("%.1f%%", ratio * 100)
                    + " (" + drops + "/" + total + "). "
                    + "Network may be unstable or log volume is too high.");
        }
        lastWarnTotal = total;
    }

    private String getDeviceId(Context context) {
        try {
            String androidId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);
            return androidId != null ? androidId : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
