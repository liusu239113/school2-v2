#!/bin/bash
# =============================================
# 校长我来当 — 通用打包脚本
# 适用于: 阿里云云效 / 任意Linux服务器 / 本地CI
# 
# 前置条件:
#   - JDK 17 已安装
#   - ANDROID_HOME 环境变量已设置
#   - Gradle Wrapper 可用
#
# 用法:
#   chmod +x build.sh
#   ./build.sh
# =============================================

set -e

echo "======================================="
echo " 校长我来当 APK Build Script"
echo "======================================="

# 1. 环境检查
echo "[1/4] 检查环境..."
if ! command -v java &> /dev/null; then
    echo "❌ 未找到 Java，请安装 JDK 17"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1)
echo "   Java: $JAVA_VER"

if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME 未设置，尝试使用默认路径..."
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/opt/android-sdk" ]; then
        export ANDROID_HOME="/opt/android-sdk"
    elif [ -d "/usr/local/android-sdk" ]; then
        export ANDROID_HOME="/usr/local/android-sdk"
    else
        echo "❌ 未找到 Android SDK，请设置 ANDROID_HOME"
        exit 1
    fi
fi
echo "   ANDROID_HOME: $ANDROID_HOME"

# 2. Gradle 权限
echo "[2/4] 设置 Gradle 权限..."
chmod +x ./gradlew

# 3. 编译
echo "[3/4] 开始编译 Release APK..."
./gradlew assembleRelease --no-daemon --stacktrace 2>&1 | tee build.log

# 4. 输出结果
echo "[4/4] 编译完成!"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo ""
    echo "✅ 构建成功!"
    echo "   APK 路径: $APK_PATH"
    echo "   文件大小: $APK_SIZE"
    echo ""
else
    echo "❌ 未找到 APK 文件，请查看 build.log"
    exit 1
fi
