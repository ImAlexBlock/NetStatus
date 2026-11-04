# NetStatus

Android 网络诊断工具，查看 WLAN 与移动数据状态。

## 内容

- WLAN：连接信息、诊断、频段频谱图、附近 AP 扫描
- 移动数据：SIM 与注册状态、服务小区与邻区、数据连接

## 环境

- Android 13（API 33）及以上
- JDK 17+、Android SDK Platform 36

## 构建

```bash
./gradlew :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 发布

推送到 `main` 触发 GitHub Actions：运行单元测试、构建 debug APK，并以 `build-<运行号>` 发布到 Releases。

## 权限

精确位置、附近的设备、电话状态。用于读取 Wi-Fi 名称、扫描结果和基站信息。
