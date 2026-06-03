# 冥想引导语音测试路线

目标：为 `带我回家` 找到温柔、缓慢、带呼吸感、留白充足的引导声音，并最终导出本地 MP3 文件放入 App。

当前 App 已支持：

- 优先播放本地引导音频：`app/src/main/res/raw/cue_001.mp3` 到 `cue_032.mp3`
- 本地引导音频缺失时，回退到系统 TTS
- 默认背景音：`app/src/main/res/raw/tide_soft.mp3`

## 第一轮测试

只测试 Cue 1，同一段文本分别用 3 条路线生成：

1. ElevenLabs Speech-to-Speech
   - 你先用手机非常慢、非常轻地读一遍 `cue_001_plain.txt`
   - 上传到 ElevenLabs Voice Changer / Speech-to-Speech
   - 选 Meditation / ASMR / Warm / Deep / Comforting 一类音色
   - 导出为 `cue_001.mp3`

2. MiniMax / 豆包 / 火山引擎直接 TTS
   - 使用 `cue_001_prompt.md` 里的提示词
   - 尝试成熟、温暖、低亮度、非播音腔音色
   - 导出为 `cue_001.mp3`

3. Azure SSML
   - 使用 `cue_001_azure.ssml`
   - 重点测试 SSML 停顿是否自然
   - 导出为 `cue_001.mp3`

## 评分标准

每个版本按 1-5 分评分：

- 安全感：听起来是否让身体放松
- 慈悲感：是否像在陪伴，而不是指导
- 呼吸感：是否有气声、柔和边缘
- 停顿：是否给人足够时间做动作
- 不机器：是否避免播音腔、客服腔、短视频口播腔
- 长听疲劳：听 3 分钟后是否烦躁

## 导入 App

最终选中的文件命名为：

```text
cue_001.mp3
```

放入：

```text
app/src/main/res/raw/
```

然后重新编译安装：

```powershell
gradle :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```
