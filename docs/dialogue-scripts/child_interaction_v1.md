# maidmarriage:child_interaction_v1 话剧式台本

## 基本信息
- 源文件：`src/main/resources/assets/maidmarriage/dialogue/scenarios/child_interaction_v1.json`
- 起始场景：`idle`
- UI 主题：`maidmarriage:hug_default`

## 写作说明
- `${maid}`：当前女仆名字
- `${player}`：玩家名字
- `${mood_idle_text}`：按当前心情生成的入口第一句
- `旁白`：speaker 为空的叙述句，游戏里不会显示姓名栏
- `跳转`：这句/这个选项结束后进入的下一个场景节点

## 场景：`idle`
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：{player}，今天也要陪我玩吗？我会乖乖听话的！
- 玩家选项：
  - 【摸摸头】
    - 选项说明：轻轻摸摸她的头，让她安心下来。
    - 触发动作：`maidmarriage:pet_head`
    - 跳转：`pet_head_intro`
  - 【举高高】
    - 选项说明：把小女仆举起来，已经举起时会放回地上。
    - 触发动作：`maidmarriage:lift_child`
    - 跳转：`lift_intro`
  - 【让妈妈抱抱】
    - 选项说明：让附近的大女仆把她抱进怀里，已经抱起时会放下。
    - 触发动作：`maidmarriage:carry_child`
    - 跳转：`carry_intro`
  - 【陪她说话】
    - 选项说明：蹲下来陪她聊一小会儿。
    - 跳转：`comfort_intro`

## 场景：`pet_head_intro`
- 场景类型：连续对白
1. 旁白：你的手掌轻轻落在她的发顶，她像小动物一样眯起眼睛。（表情：happy；动画：soft_bounce）
2. ${maid}：嘿嘿……再摸一下也可以哦。（表情：happy）
- 下一幕：`idle`

## 场景：`lift_intro`
- 场景类型：连续对白
1. 旁白：她下意识抓住你的袖口，随后又开心地晃了晃脚。（表情：happy；动画：slide_up）
2. ${maid}：哇，好高！我能看到好远好远的地方！（表情：happy）
- 下一幕：`idle`

## 场景：`carry_intro`
- 场景类型：连续对白
1. 旁白：附近的大女仆轻轻靠近，把她稳稳抱在怀里。（表情：soft_smile；动画：fade_in）
2. ${maid}：被抱着的时候，感觉整个人都暖洋洋的。（表情：happy）
- 下一幕：`idle`

## 场景：`comfort_intro`
- 场景类型：连续对白
1. 旁白：你蹲下来和她保持同样的视线高度，她认真地看着你。（表情：soft_smile；动画：fade_in）
2. ${maid}：只要你在旁边，我就觉得今天也没什么可害怕的。（表情：shy）
- 下一幕：`idle`
