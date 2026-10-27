# 奶龙刷题

Android 离线刷题应用，内置党章、党史和湘潭大学原题库。顶部可以切换题库，各题库的错题、收藏、答题记录、考试记录和顺序进度分别保存。

| 题库 | 题目数 | 数据文件 |
| --- | ---: | --- |
| 党章 | 357 | `app/src/main/assets/questions_dangzhang.json` |
| 党史 | 728 | `app/src/main/assets/questions_dangshi.json` |
| 湘潭大学原题库 | 1038 | `app/src/main/assets/questions.json` |

支持顺序练习、随机练习、模拟考试、错题复习、收藏和搜索。PDF 中没有参考答案的 4 道论述题保留题干，并在应用内注明。

## 直接从 PDF 新建题库

1. 打开 **我的 → 数据管理 → 从 PDF 新建题库**，选择一份 PDF。
2. 等待本机提取文字；扫描页会使用安装包自带的中文模型识别。
3. 对照原文核对总题数、选项和答案，可修改或取消选中待修正题目。
4. 确认保存，然后在顶部“切换”中使用新题库，学习记录独立保存。

不需要手工转换 JSON、配置接口或上传到服务器。缺答案的 PDF 不会自动生成答案，复杂排版和图片题需要人工核对或补充，配图不会导入。具体支持格式与限制见 [PDF 导入说明](docs/PDF导入.md)。

## 直接用 GitHub 打包

每次提交会先运行单元测试、检查 Release 编译，再在 Android 模拟器上测试扫描 PDF 的识别、预览和保存。合并或推送到 `main` 后，全部检查通过才会使用原签名密钥生成更新 APK。

成功后，在运行页面的 **Artifacts** 下载 `nailong-quiz-release-apk`，解压后安装 `app-release.apk`。正式包沿用 `com.nailong.quiz.study` 和原签名；直接在已有内置题库版上选择更新即可。测试报告保存在 `android-test-reports`。

正式签名需要先配置 `NAILONG_KEYSTORE_BASE64` 与 `NAILONG_STORE_PASSWORD` 两个仓库 Secrets；配置完成前不会生成可覆盖安装的正式 APK。详见[正式版升级说明](docs/正式版升级.md)。

内部版本号由工作流运行次数递增，签名、包名与 APK 类型都有自动校验。私钥不会提交到仓库。

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

正式版应用 ID 仍为 `com.nailong.quiz.study`。签名密钥和密码不在公开源码中，自动打包从仓库加密 Secrets 读取；原签名文件也保存在你单独保留的源码备份中。继续覆盖更新已安装的正式版，必须使用同一份签名密钥。

在本机将 `keystore.properties.example` 复制为 `keystore.properties`，填入密钥路径、别名和密码。也可以设置环境变量 `NAILONG_KEYSTORE`、`NAILONG_STORE_PASSWORD`、`NAILONG_KEY_ALIAS`、`NAILONG_KEY_PASSWORD`。

```bash
./gradlew :app:assembleRelease
```

本地打包时可通过 `NAILONG_VERSION_CODE` 指定高于已发布版本的内部版本号。有完整签名配置时会生成可安装的 `app/build/outputs/apk/release/app-release.apk`；没有配置时只生成未签名的 Release APK，不能直接安装。日常修改可以先使用开发版。

`signing/`、`keystore.properties`、本机 SDK 路径、编译输出及密钥文件已加入忽略规则。不要将包含签名文件的原始 ZIP 直接提交到公开仓库。

## 验证

项目包含判题、题库完整性、PDF 解析和实际文字 PDF 读取，以及 Robolectric 环境下的题库切换和学习记录隔离测试。Android 设备测试用图片型中文 PDF 运行原生 OCR，再操作预览与保存界面；截图和报告保存在 `pdf-device-test-reports`。CI 状态以 Actions 页面为准。

[原始内置版的构建验证记录](docs/原始内置版验证报告.txt)对应首次生成的安装包，不代表后续每一次提交都已完成实体手机测试。
