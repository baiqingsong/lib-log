# lib-log

Android 日志工具库

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

- Logcat 打印 + 文件写入双通道
- 文件写入使用后台线程队列，不阻塞主线程
- 日志文件按天生成，自动清理超过 7 天的文件
- 支持超长日志自动分段打印
- 支持 JSON / XML 格式化输出
- 提供日志文件列表接口，方便上传到服务器

## 初始化

在 `Application.onCreate()` 中初始化：

```java
// 基础初始化
LLog.init(this, BuildConfig.DEBUG, "MyApp");

// 自定义日志路径
LLog.init(this, BuildConfig.DEBUG, "MyApp", "/sdcard/MyApp/logs/");
```

在应用退出时释放资源：

```java
LLog.release();
```

## 类说明

`com.dawn.log.LLog` 日志工具类，所有方法均为静态方法。

| 方法 | 说明 |
|------|------|
| `init(Context, boolean, String)` | 初始化日志（指定开关和 TAG） |
| `init(Context, boolean, String, String)` | 初始化日志（指定自定义路径） |
| `release()` | 释放资源（应用退出时调用） |
| `v(String)` | VERBOSE 日志 |
| `v(String, String)` | 带 TAG 的 VERBOSE 日志 |
| `d(String)` | DEBUG 日志 |
| `d(String, String)` | 带 TAG 的 DEBUG 日志 |
| `i(Object...)` | INFO 日志（支持多参数） |
| `w(String)` | WARN 日志 |
| `w(String, String)` | 带 TAG 的 WARN 日志 |
| `e(String)` | ERROR 日志 |
| `e(String, String)` | 带 TAG 的 ERROR 日志 |
| `e(String, Throwable)` | ERROR 日志（带异常） |
| `e(String, String, Throwable)` | 带 TAG 的 ERROR 日志（带异常） |
| `json(String)` | 格式化输出 JSON |
| `json(String, String)` | 带 TAG 格式化输出 JSON |
| `xml(String)` | 格式化输出 XML |
| `xml(String, String)` | 带 TAG 格式化输出 XML |
| `getLogPath()` | 获取日志文件存储目录 |
| `getLogFiles()` | 获取所有日志文件列表（按时间排序） |
| `cleanExpiredLogs()` | 手动清理超过 7 天的日志文件 |
| `clearAllLogs()` | 清除所有日志文件 |
