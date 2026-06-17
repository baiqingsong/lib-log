# lib-log

Android 日志工具库，支持本地文件 + 云端实时上传的多通道架构。

## 引用

Step 1. Add the JitPack repository to your build file

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Step 2. Add the dependency

```groovy
dependencies {
    implementation 'com.github.baiqingsong:lib-log:Tag'
}
```

## 特性

- ✅ **多通道架构**：日志同时输出到本地文件 + 阿里云 SLS / 腾讯云 CLS，可自由组合
- ✅ **本地文件写入**：后台线程异步写入，不阻塞主线程；按天分文件，10MB 自动截断，7 天自动清理
- ✅ **阿里云 SLS**：基于 `aliyun-log-producer`，批量聚合、压缩、重试，`maxBlockingMs=0` 不阻塞主线程
- ✅ **腾讯云 CLS**：基于 `tencentcloud-cls-sdk-android`，异步发送，自动降级
- ✅ **通道隔离**：某个通道异常不影响其他通道，单通道崩溃不会导致日志丢失
- ✅ **超长日志分段**：超过 4000 字符自动分段打印
- ✅ **JSON / XML 格式化**：自动缩进 + 边框美化输出
- ✅ **可观测性**：提供 `getSuccessCount()` / `getDroppedCount()` / `getFailCount()` 监控接口

## 快速开始

### 基础初始化（仅本地文件）

```java
// Application.onCreate()
LLog.init(this, BuildConfig.DEBUG, "MyApp");

// 或指定自定义日志路径
LLog.init(this, BuildConfig.DEBUG, "MyApp", "/sdcard/MyApp/logs/");
```

### 本地文件 + 阿里云 SLS

```java
SLSConfig slsConfig = new SLSConfig.Builder()
    .endpoint("cn-hangzhou.log.aliyuncs.com")
    .project("my-project")
    .logstore("app-logs")
    .accessKeyId("your-access-key-id")
    .accessKeySecret("your-access-key-secret")
    .build();

LLog.init(this, BuildConfig.DEBUG, "MyApp", slsConfig);
```

### 本地文件 + 腾讯云 CLS

```java
CLSConfig clsConfig = new CLSConfig.Builder()
    .endpoint("ap-guangzhou.cls.tencentcs.com")
    .secretId("your-secret-id")
    .secretKey("your-secret-key")
    .topicId("your-topic-id")
    .build();

LLog.init(this, BuildConfig.DEBUG, "MyApp", clsConfig);
```

### 本地文件 + 阿里云 SLS + 腾讯云 CLS（三通道）

```java
LLog.init(this, BuildConfig.DEBUG, "MyApp", slsConfig, clsConfig);
```

### 释放资源

```java
// 应用退出时
LLog.release();
```

## 使用日志

```java
LLog.v("详细日志");
LLog.d("调试信息");
LLog.i("用户操作", "参数2", "参数3");
LLog.w("警告信息");
LLog.e("错误信息");
LLog.e("网络错误", exception);
LLog.json("{\"key\": \"value\"}");
LLog.xml("<root><item>value</item></root>");
```

## 自定义通道

```java
// 实现 ILogChannel 接口
public class MyChannel implements ILogChannel {
    @Override public void init(Context ctx) { /* 初始化 */ }
    @Override public void write(char level, String tag, String msg, String caller, long ts) { /* 写入 */ }
    @Override public void flush() { /* 刷新 */ }
    @Override public void release() { /* 释放 */ }
}

// 注册到 LLog
LLog.addChannel(new MyChannel());
```

## 文件管理

```java
LLog.getLogPath();        // 获取日志目录
LLog.getLogFiles();       // 获取日志文件列表（时间排序）
LLog.cleanExpiredLogs();  // 清理超过7天的文件
LLog.clearAllLogs();      // 删除所有日志文件
```

## 监控

```java
SLSLogChannel sls = ...;
sls.getSuccessCount();    // 成功发送数
sls.getDroppedCount();    // 缓冲区满丢弃数
sls.getFailCount();       // 重试耗尽失败数
sls.resetStats();         // 重置计数器

CLSLogChannel cls = ...;
cls.getSuccessCount();
cls.getDroppedCount();
cls.getFailCount();
cls.resetStats();
```

## SLSConfig 配置项

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `endpoint` | 必填 | SLS 接入点，如 `cn-hangzhou.log.aliyuncs.com` |
| `project` | 必填 | SLS 项目名称 |
| `logstore` | 必填 | 日志库名称 |
| `accessKeyId` | 必填 | 阿里云 AccessKey ID |
| `accessKeySecret` | 必填 | 阿里云 AccessKey Secret |
| `stsToken` | null | STS 临时凭证（可选） |
| `topic` | `"android"` | 日志主题 |
| `source` | 自动获取 | 来源标识 |
| `maxBlockingMs` | `0` | send() 最大阻塞时间，0=不阻塞 |
| `ioThreadCount` | `1` | Producer I/O 线程数 |
| `packageTimeoutMs` | `3000` | 批量发送超时（毫秒） |
| `packageCount` | `4096` | 单批次最大条数 |
| `packageSizeBytes` | `4MB` | 单批次最大字节数 |
| `retryTimes` | `3` | 失败重试次数 |
| `maxMemoryBytes` | `100MB` | 内存缓冲区上限 |

## CLSConfig 配置项

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `secretId` | 必填 | 腾讯云 SecretId |
| `secretKey` | 必填 | 腾讯云 SecretKey |
| `topicId` | 必填 | CLS 日志主题 ID |
| `endpoint` | `ap-guangzhou.cls.tencentcs.com` | CLS 接入点 |
| `source` | 自动获取 | 来源标识 |

## 类说明

| 类 | 说明 |
|------|------|
| `LLog` | 主入口，静态方法，管理多通道 |
| `ILogChannel` | 通道接口，实现自定义通道需实现此接口 |
| `FileLogChannel` | 本地文件通道，后台线程异步写入 |
| `SLSLogChannel` | 阿里云 SLS 通道，基于 aliyun-log-producer |
| `CLSLogChannel` | 腾讯云 CLS 通道，基于 tencentcloud-cls-sdk |
| `SLSConfig` | 阿里云 SLS 配置 Builder |
| `CLSConfig` | 腾讯云 CLS 配置 Builder |

## API 参考

| 方法 | 说明 |
|------|------|
| `init(Context, boolean, String)` | 初始化（仅本地文件） |
| `init(Context, boolean, String, String)` | 初始化（自定义路径） |
| `init(Context, boolean, String, SLSConfig)` | 初始化（+阿里云 SLS） |
| `init(Context, boolean, String, CLSConfig)` | 初始化（+腾讯云 CLS） |
| `init(Context, boolean, String, SLSConfig, CLSConfig)` | 初始化（+SLS+CLS 三通道） |
| `init(Context, boolean, String, String, SLSConfig, CLSConfig)` | 初始化（自定义路径+SLS+CLS） |
| `addChannel(ILogChannel)` | 注册自定义通道 |
| `removeChannel(ILogChannel)` | 移除通道 |
| `release()` | 释放所有资源 |
| `v/d/i/w/e(String)` | 各级别日志 |
| `v/d/w/e(String, String)` | 带 TAG 日志 |
| `e(String, Throwable)` | 带异常日志 |
| `i(Object...)` | INFO 多参数拼接 |
| `json(String)` | JSON 格式化输出 |
| `xml(String)` | XML 格式化输出 |
| `getLogPath()` | 获取日志目录 |
| `getLogFiles()` | 获取日志文件列表 |
| `cleanExpiredLogs()` | 清理过期日志 |
| `clearAllLogs()` | 清除所有日志 |

