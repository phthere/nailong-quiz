# 奶龙刷题·内置题库版

基于用户提供的 NailongQuiz 源码加入两套 PDF 题库。首次打开即可使用，默认选择党章题库，顶部“切换”可选择其他题库。

| 题库 | 题目数 |
| --- | ---: |
| 党章题库 | 357 |
| 党史题库 | 728 |
| 湘潭大学原题库 | 1038 |

每套题库使用独立数据库，分别保存错题、收藏、答题记录及考试记录；顺序刷题的位置按题库分别保存。原资料缺少参考答案的 4 道论述题保留题干，并显示无参考答案的提示。

本版应用名称为“奶龙刷题·内置题库”，应用 ID 为 `com.nailong.quiz.study`，可与原版同时安装。最低 Android 版本为 Android 8.0（API 26）。

## 构建

使用 JDK 17、Android SDK Platform 34 和 Build Tools 34.0.0。在 Android Studio 打开本目录，或设置 `ANDROID_HOME` 后运行：

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

APK 位于 `app/build/outputs/apk/release/app-release.apk`。首次运行 Gradle Wrapper 会下载已校验的 Gradle 8.2.1。

## 验证

保留原有判题与题库完整性测试，新增三套题库数据检查，以及 Robolectric 下的题库切换、错题、收藏、顺序进度隔离测试。

源代码压缩包不包含本机 SDK 路径、下载缓存或编译输出。

## 保留签名

本个人安装版使用项目内的 `signing/study-keystore.p12`。签名别名为 `nailongstudy`，存储与密钥密码均为 `android`。签名文件随本私有源码备份保留，便于以后生成可以覆盖更新本独立版的 APK。
