# 对话 / 动作 / UI 重构基础层

本次提交**先搭底座，不接旧逻辑**。

## 这次新增了什么

- `client.dialoguesystem.DialogueScenario`
  - 统一描述剧情场景、节点、连续文本、选项、分支、事件、头像表情表
- `client.dialoguesystem.DialogueUiAnimationLibrary`
  - 统一描述 UI 动画关键帧
- `client.dialoguesystem.DialogueScenarioLoader`
  - 从资源包读取剧情 JSON
- `client.dialoguesystem.DialogueUiAnimationLoader`
  - 从资源包读取动画 JSON
- `client.dialoguesystem.runtime.DialogueSessionController`
  - 负责节点推进、连续文本翻页、选项跳转、事件触发、动作请求排队
- `client.dialoguesystem.runtime.DialogueEventRegistry`
  - 白名单事件系统，只做安全动作，不允许任意执行脚本/命令
- 示例资源
  - `assets/maidmarriage/dialogue/scenarios/hug_menu_v2.json`
  - `assets/maidmarriage/dialogue_ui/animations/default.json`

## 为什么先不接旧逻辑

因为现在旧的 `HugActionScreen` 已经把：

- UI
- 文案
- 状态
- 动作触发
- 调试逻辑

都塞在一起了。

如果边拆边继续接老逻辑，很容易把新框架也拖成旧结构。

所以这一步的目标是先把这些边界立住：

1. **剧情层**：只描述节点、文本、选项、事件
2. **运行时层**：只负责推进剧情，不直接画 UI
3. **动作层**：先发出语义动作请求，不直接执行旧包逻辑
4. **UI 层**：后续只吃 `DialogueFrameView`

## 后续接线顺序建议

1. 新增通用 `DialogueScreen`
2. 让 `DialogueScreen` 直接吃 `DialogueSessionController.currentFrame()`
3. 把旧 `HugActionScreen` 的文案、按钮、分页逐步迁到 `hug_menu_v2.json`
4. 再把 `maidmarriage:kiss / pet_head / hug_start` 这些动作请求接到现有网络包
5. 最后再把头像/按钮/文本框的关键帧动画播放器接到现有 `dialogueui` 组件

## 当前事件白名单

- `set_portrait`
- `set_expression`
- `play_ui_animation`
- `set_variable`
- `emit_action`
- `goto_node`

## 当前默认条件

- 空条件 / `always`
- `key`
- `!key`
- `key=value`

后续再扩展为：

- 好感度
- 女仆年龄
- 是否正在拥抱
- 是否为主人
- 是否 YSM / GeckoLib

## 设计原则

1. **动作不是剧情本身，只是剧情中的事件**
2. **一段剧情可以有多条连续文本**
3. **每条文本都可以切表情和 UI 动画**
4. **JSON 只描述语义，不直接执行危险逻辑**
5. **旧业务逻辑后接，不能反过来污染新框架**
