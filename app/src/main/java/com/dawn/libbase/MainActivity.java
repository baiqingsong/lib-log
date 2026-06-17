package com.dawn.libbase;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.dawn.log.CLSConfig;
import com.dawn.log.LLog;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ==================== 腾讯云 CLS 测试配置 ====================
        CLSConfig clsConfig = new CLSConfig.Builder()
                .secretId("")
                .secretKey("")
                .topicId("")
                .endpoint("ap-guangzhou.cls.tencentcs.com")  // 广州接入点，可按需修改
                .build();

        // 初始化日志：Logcat + 本地文件 + 腾讯云 CLS
        LLog.init(this, true, "LibBase", clsConfig);

        // ==================== 测试日志输出 ====================
        LLog.d("CLS Test", "腾讯云日志服务测试 - Debug");
        LLog.i("CLS Test", "腾讯云日志服务测试 - Info");
        LLog.w("CLS Test", "腾讯云日志服务测试 - Warn");
        LLog.e("CLS Test", "腾讯云日志服务测试 - Error");
    }
}