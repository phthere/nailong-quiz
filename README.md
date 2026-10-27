# 奶龙刷题

Android 离线刷题应用，内置党章、党史和湘潭大学原题库。顶部可以切换题库，各题库的错题、收藏、答题记录、考试记录和顺序进度分别保存。

| 题库 | 题目数 | 数据文件 |
| --- | ---: | --- |
| 党章 | 357 | `app/src/main/assets/questions_dangzhang.json` |
| 党史 | 728 | `app/src/main/assets/questions_dangshi.json` |
| 湘潭大学原题库 | 1038 | `app/src/main/assets/questions.json` |

支持顺序练习、随机练习、模拟考试、错题复习、收藏和搜索。PDF 中没有参考答案的 4 道论述题保留题干，并在应用内注明。

## 直接用 GitHub 打包

每次推送到 `main` 或创建 Pull Request，GitHub Actions 会运行测试并构建开发版 APK。也可以进入仓库 **Actions → Android build → Run workflow** 手动运行。

运行成功后，在该次运行页面的 **Artifacts** 中下载 `nailong-quiz-dev-apk`，解压后安装 `app-debug.apk`。测试报告保存在 `android-test-reports`。

开发版名称是“奶龙刷题·开发版”，应用 ID 为 `com.nailong.quiz.study.dev`，可与原来的内置题库版同时安装。开发版使用构建环境的调试签名；不同环境生成的开发版若无法相互覆盖，需要卸载旧开发版，或先配置固定的开发签名。

## 在电脑上开发

安装 Android Studio，打开本仓库根目录。项目使用 JDK 17、Gradle 8.2.1、Android SDK 34、Build Tools 34.0.0，最低支持 Android 8.0。

```bash
git clone https://github.com/phthere/nailong-quiz.git
cd nailong-quiz
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows 使用 `gradlew.bat`。构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 从哪里修改

| 目标 | 位置 |
| --- | --- |
| 题库内容 | `app/src/main/assets/` |
| 题库名称、文件名和数量 | `app/src/main/java/com/nailong/quiz/data/BuiltInBank.kt` |
| 首页、练习、设置等界面 | `app/src/main/java/com/nailong/quiz/ui/` |
| 判题逻辑 | `app/src/main/java/com/nailong/quiz/domain/Grader.kt` |
| 数据库和数据读取 | `app/src/main/java/com/nailong/quiz/data/` |
| 自动测试与构建 | `.github/workflows/android.yml` |

修改题库时保留稳定的题目 ID，并同步更新 JSON 的 `count`、`BuiltInBank.kt` 的数量和相关完整性测试。仅修改题目文字或答案时，不要重排已有题目 ID，以免已有学习记录对应错误。

## 正式版签名

正式版应用 ID 仍为 `com.nailong.quiz.study`。签名密钥和密码未上传到仓库，原来的签名文件保存在你单独保留的源码备份中。继续覆盖更新已安装的正式版，必须使用同一份签名密钥。

在本机将 `keystore.properties.example` 复制为 `keystore.properties`，填入密钥路径、别名和密码。也可以设置环境变量 `NAILONG_KEYSTORE`、`NAILONG_STORE_PASSWORD`、`NAILONG_KEY_ALIAS`、`NAILONG_KEY_PASSWORD`。

```bash
./gradlew :app:assembleRelease
```

有完整签名配置时会生成可安装的 `app/build/outputs/apk/release/app-release.apk`；没有配置时只生成未签名的 Release APK，不能直接安装。日常修改可以先使用开发版。

`signing/`、`keystore.properties`、本机 SDK 路径、编译输出及密钥文件已加入忽略规则。不要将包含签名文件的原始 ZIP 直接提交到公开仓库。

## 验证

项目包含判题、题库完整性，以及 Robolectric 模拟 Android 环境下的题库切换和学习记录隔离测试。CI 状态以 Actions 页面为准。

[原始内置版的构建验证记录](docs/原始内置版验证报告.txt)对应首次生成的安装包，不代表后续每一次提交都已完成实体手机测试。
