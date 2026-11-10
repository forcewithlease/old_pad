# 把开源小游戏放进这个目录（离线）

OldPad 会自动扫描 `assets/games/` 下的每个子目录，只要里面有：

```
assets/games/你的游戏/
├── game.json
└── index.html
```

`game.json` 示例：

```json
{
  "title": "五子棋",
  "subtitle": "点格子下棋",
  "emoji": "⚫"
}
```

要求：

- 纯前端、离线可运行：HTML / CSS / JS / 图片都放在同一目录（可以再建子目录）。
- 不要依赖 CDN 或任何网络请求：应用里 WebView 已禁止联网。
- 入口文件必须叫 `index.html`。
- 建议支持触摸、按钮大、字号大，适合老人。
- 重新打包 APK 后，游戏会自动出现在首页和管理页，不用改任何 Kotlin 代码。

## 原生游戏（需要更好性能时）

1. 实现 `com.oldpad.app.game.GameModule`，返回一个自定义 View；
2. 实现 `com.oldpad.app.game.GameProvider`；
3. 在 `GameRegistry.nativeProviders` 里加一行。

参考 `game/gomoku/` 里的五子棋实现。
