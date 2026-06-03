# 带我回家

带我回家是一个本地优先的 Android 心理自助练习 App。它把一段 25 分钟的温和练习拆成可执行的阶段：身体落地、正念观察、内在安抚、信念重构和今日行动承诺。

这个项目的定位是自我觉察、情绪陪伴和练习辅助，不是心理治疗、诊断工具或紧急干预服务。

## 功能

- 25 分钟分阶段引导练习
- Android TextToSpeech 本地朗读
- 可选本地背景音，缺失音频时自动静音
- 暂停、继续、重新开始和提前结束记录
- 练习后填写三项反思
- Room 本地数据库保存历史记录
- DataStore 保存本地设置
- 不需要账号，不上传隐私数据，不依赖服务器

## 心理安全边界

本项目不能替代专业心理咨询、精神科诊疗或紧急求助。如果你出现强烈自伤、伤人冲动，或感觉无法控制自己的行为，请立即联系身边可信任的人、当地紧急服务或专业人员。

练习脚本采用通用、温和的表达，避免把个人隐私、创伤细节或高风险触发内容写进公开仓库。贡献者也请遵守这一原则。

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- DataStore
- Android TextToSpeech
- 本地 `MediaPlayer`

## 构建

需要 Android SDK 和 Gradle。

```powershell
gradle :app:assembleDebug
```

安装到已连接的 Android 设备：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

## 音频资源

公开仓库默认不包含 MP3 文件。原因是音频文件常常涉及授权、配音人隐私或第三方素材来源。

如果你想在本地加入音频，可以把文件放在：

```text
app/src/main/res/raw/
```

命名示例：

- `cue_001.mp3`
- `tide_soft.mp3`

没有这些文件时，App 会回退到系统 TTS 或静音背景音。

## 开源与商业边界

这个仓库开放的是通用公益核心：本地练习播放器、隐私优先的记录方式、安全边界和基础脚本。未来可以在不破坏开源精神的前提下，围绕定制脚本审核、专业陪伴流程、授权音频包、机构部署或课程服务建立可持续现金流。

善意需要结构、边界和现金流，才更容易长期帮助人。

## 许可证

Apache-2.0。详见 [LICENSE](LICENSE)。
