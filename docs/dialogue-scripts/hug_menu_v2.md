# maidmarriage:hug_menu_v2 话剧式台本

> 拥抱互动主台本。用于成年女仆的亲密互动界面，包含：主菜单、拥抱、亲吻、摸头、聊天分支，以及按天气/时间/好感阶段变化的对白。

## 基本信息
- 源文件：`src/main/resources/assets/maidmarriage/dialogue/scenarios/hug_menu_v2.json`
- 起始场景：`idle`
- UI 主题：`maidmarriage:hug_default`

## 写作说明
- `${maid}`：当前女仆名字
- `${player}`：玩家名字
- `${mood_idle_text}`：按当前心情生成的入口第一句
- `旁白`：speaker 为空的叙述句，游戏里不会显示姓名栏
- `跳转`：这句/这个选项结束后进入的下一个场景节点

## 分区说明：台本分区：idle 是主菜单；kiss/hug/pet_intro 是动作开场对白；chat_topic_router 负责把聊天分到不同主题；chat_stage_* 是按好感阶段拆开的专属对白。

## 场景：`idle`
- 场景说明：主交互入口。进入界面后先显示这里。第一句是当前心情下的开场白，然后给出 聊天 / 亲吻 / 拥抱 / 放开女仆 / 摸头 等主选项。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：${mood_idle_text}
- 玩家选项：
  - 【聊天】
    - 选项说明：聊聊今天的心情、天气和她想说的话。
    - 跳转：`chat_topic_router`
  - 【亲吻】
    - 选项说明：轻轻亲近一下，让气氛再靠近一点。
    - 出现条件：`!bloodline_kiss_blocked && favor_kiss_unlocked`
    - 触发动作：`maidmarriage:kiss`
    - 跳转：`kiss_intro`
  - 【拥抱】
    - 选项说明：轻轻把她抱进怀里，让这一刻更贴近一些。
    - 出现条件：`!hugging && favor_hug_unlocked`
    - 触发动作：`maidmarriage:hug_toggle`
    - 跳转：`hug_intro`
  - 【放开女仆】
    - 选项说明：先松开怀抱，回到面对面站着的距离。
    - 出现条件：`hugging`
    - 触发动作：`maidmarriage:hug_end`
    - 跳转：`idle`
  - 【摸头】
    - 选项说明：抬手摸摸她的头，给她一点温柔反馈。
    - 出现条件：`favor_pet_unlocked`
    - 触发动作：`maidmarriage:pet_head`
    - 跳转：`pet_intro`

## 场景：`kiss_intro`
- 场景说明：亲吻开场对白。用于玩家点下“亲吻”后先铺垫气氛，让正式亲吻前有一小段害羞和升温的文本。
- 场景类型：连续对白
1. 旁白：距离被悄悄拉近，连空气都像是跟着安静了半拍。（表情：shy；动画：slide_up）
2. 旁白：她的睫毛轻轻颤了一下，耳尖也不受控制地泛起了热意。（表情：hot_smile）
3. ${maid}：要是再靠近一点点的话……我、我可不保证自己还能好好看着你哦。（表情：hot_smile）
4. ${maid}：不过……如果对象是你的话，稍微任性一点，好像也不是不可以。（表情：shy；动画：soft_bounce）
- 下一幕：`idle`

## 场景：`hug_intro`
- 场景说明：拥抱开场对白。用于玩家点下“拥抱”后，先把气氛从站立锁定推进到贴近拥抱的状态。
- 场景类型：连续对白
1. 旁白：掌心传来温柔的触感，怀里的温度也跟着一点点贴近过来。（表情：soft_smile；动画：fade_in）
2. 旁白：短暂的沉默没有让气氛变冷，反而把呼吸声衬得格外清晰。（表情：soft_smile）
3. ${maid}：就这样再抱一会儿吧……现在什么都不用急，能这样靠着你，我就已经很安心了。（表情：soft_smile）
4. ${maid}：如果时间能稍微慢一点就好了，我还想把这一刻多记住一点。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`pet_intro`
- 场景说明：摸头入口。这里按当前好感阶段分流，不同阶段的反应不一样：第一阶段不解，第二阶段害羞，第三阶段喜欢，第四阶段习惯并主动撒娇。
- 场景类型：条件分流
- 条件分流：
  - 如果 `favor_stage_marriage` → `pet_intro_marriage`
  - 如果 `favor_stage_dating` → `pet_intro_dating`
  - 如果 `favor_stage_close` → `pet_intro_close`
- 否则进入：`pet_intro_warm`

## 场景：`pet_intro_warm`
- 场景说明：摸头第一阶段，好感度 32~63。她还不太理解为什么要摸头，反应偏正经、疑惑，但不会排斥。
- 场景类型：连续对白
1. 旁白：你的手刚落到她头上，她明显愣了一下，像是没反应过来你为什么突然这么做。（表情：shy；动画：soft_bounce）
2. ${maid}：欸？这是……奖励吗？还是说我刚才看起来很累？（表情：shy）
3. ${maid}：我不是小孩子啦。不过……如果只是一下下的话，我可以当作没关系。（表情：soft_smile）
4. ${maid}：下次至少先告诉我一声，不然我会不知道该露出什么表情。（表情：shy）
- 下一幕：`idle`

## 场景：`pet_intro_close`
- 场景说明：摸头第二阶段，好感度 64~127。她已经开始接受摸头，但嘴上还是会害羞、会找理由。
- 场景类型：连续对白
1. 旁白：你的指尖顺着发梢轻轻落下，她下意识缩了缩肩，却没有躲开。（表情：shy；动画：soft_bounce）
2. ${maid}：等、等一下……你这样摸的话，我会很难继续装作没事的。（表情：shy）
3. ${maid}：不是讨厌。只是……被你这样认真看着，会觉得脸有点热。（表情：hot_smile）
4. ${maid}：所以再一下就好。真的只许再一下。（表情：shy）
- 下一幕：`idle`

## 场景：`pet_intro_dating`
- 场景说明：摸头第三阶段，好感度 128~197。交往期，她已经明确喜欢这种亲昵，但还是会害羞。
- 场景类型：连续对白
1. 旁白：她看到你抬手时，眼神先躲了一下，却又悄悄把头往你这边低了低。（表情：hot_smile；动画：soft_bounce）
2. ${maid}：你又想摸头了吗……真是的，明明知道我现在已经很难拒绝你了。（表情：hot_smile）
3. ${maid}：不过，被喜欢的人这样摸头，确实会觉得心里软软的。（表情：shy）
4. ${maid}：这句话我只说一次哦：我很喜欢。所以你要温柔一点。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`pet_intro_marriage`
- 场景说明：摸头第四阶段，好感度 198 及以上。长期关系/婚后语气，她已经习惯并喜欢这种安抚，会主动撒娇一点。
- 场景类型：连续对白
1. 旁白：你还没完全抬起手，她就像猜到了一样，轻轻把头靠近了些。（表情：soft_smile；动画：soft_bounce）
2. ${maid}：今天也要摸摸头吗？嗯……可以哦，我已经等着了。（表情：hot_smile）
3. ${maid}：以前我还会问你为什么这么做，现在好像已经有点习惯被你哄着了。（表情：soft_smile）
4. ${maid}：再多摸一会儿吧。反正是你把我宠成这样的，要负责到底。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_topic_router`
- 场景说明：聊天总入口。这里不直接显示文本，而是决定这次聊天应该落到哪一类话题：低落安慰、天气话题、早晚问候、阶段专属对白，或者普通日常聊天。
- 场景类型：条件分流
- 条件分流：
  - 如果 `mood_negative` → `chat_low_mood`
  - 如果 `weather_thunder` → `chat_thunder`
  - 如果 `weather_rain` → `chat_rain`
  - 如果 `time_morning` → `chat_morning`
  - 如果 `time_night || time_midnight` → `chat_night`
  - 如果 `favor_marriage_unlocked` → `chat_stage_marriage`
  - 如果 `favor_kiss_unlocked` → `chat_stage_dating`
  - 如果 `favor_hug_unlocked` → `chat_stage_close`
  - 如果 `favor_pet_unlocked` → `chat_stage_warm`
- 否则进入：`chat_daily`

## 场景：`chat_low_mood`
- 场景说明：低落心情聊天。适用于女仆今天情绪偏低时的安慰型对话，不强调阶段推进，重点是陪伴和安抚。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：今天有点提不起精神……${player}，可以先陪我安静一会儿吗？
- 玩家选项：
  - 【安静陪着她】
    - 选项说明：不追问，只在身边陪她慢慢缓过来。
    - 跳转：`chat_low_quiet_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=1，moodDelta=3
  - 【轻声安慰她】
    - 选项说明：告诉她今天不用勉强自己。
    - 跳转：`chat_low_comfort_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=0，moodDelta=2
  - 【逗她开心】
    - 选项说明：试着讲个玩笑，但她低落时可能接不住。
    - 跳转：`chat_low_joke_result`
    - 互动结果：positiveFavor=2，neutralFavor=0，negativeFavor=-2，moodDelta=-1

## 场景：`chat_low_quiet_result`
- 场景类型：连续对白
1. ${maid}：谢谢……你没有急着让我开心起来，反而让我觉得安心多了。（表情：soft_smile；动画：fade_in）
2. 旁白：她没有立刻继续说话，只是把呼吸放得很轻。（表情：soft_smile）
3. ${maid}：有时候不用说很多，能被这样陪着，就已经很好了。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_low_comfort_result`
- 场景类型：连续对白
1. ${maid}：嗯，我会慢慢调整的。能听见你这么说，心里就没那么乱了。（表情：shy；动画：fade_in）
2. ${maid}：今天可能没办法马上打起精神，但我会试着不把坏心情藏起来。（表情：soft_smile）
3. ${maid}：因为你会认真听我说，对吧？（表情：shy）
- 下一幕：`idle`

## 场景：`chat_low_joke_result`
- 场景类型：连续对白
1. ${maid}：噗……虽然有点突然，不过你努力的样子还是很可爱。（表情：shy；动画：fade_in）
2. 旁白：她低头笑了一下，原本垂着的肩膀也稍微放松了些。（表情：soft_smile）
3. ${maid}：下次如果想逗我开心，可以再自然一点哦。（表情：soft_smile）
- 下一幕：`idle`

## 分区说明：下面是共享聊天话题：天气与时段。所有阶段都可能进入这些话题，但同一个回答会因为当前好感阶段不同而出现不同语气。

## 场景：`chat_thunder`
- 场景说明：雷雨天聊天。主题是害怕、陪伴和安全感。所有阶段都能触发。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：外面的雷声好响……${player}，你会不会也觉得有点吵？
- 玩家选项：
  - 【我会陪着你】
    - 选项说明：让她知道雷雨过去前你不会离开。
    - 跳转：`chat_thunder_stay_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=0，moodDelta=2
  - 【一起听雨声】
    - 选项说明：把可怕的雷雨变成两个人的背景音。
    - 跳转：`chat_thunder_listen_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=-1，moodDelta=1
  - 【雷声也怕你】
    - 选项说明：用轻松的话题缓和气氛。
    - 跳转：`chat_thunder_tease_result`
    - 互动结果：positiveFavor=2，neutralFavor=0，negativeFavor=-1，moodDelta=0

## 场景：`chat_thunder_stay_result`
- 场景类型：连续对白
1. ${maid}：那我就稍微靠近一点点……只是一点点哦。（表情：shy；动画：fade_in）
2. 旁白：窗外的雷声滚过去时，她下意识看了你一眼。（表情：soft_smile）
3. ${maid}：有你在旁边的话，好像连雷雨都不会那么吓人了。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`chat_thunder_listen_result`
- 场景类型：连续对白
1. ${maid}：这样听起来，好像也没那么可怕了。因为旁边有你的声音。（表情：soft_smile；动画：fade_in）
2. ${maid}：雨声、雷声，还有你的呼吸声……混在一起反而有点安心。（表情：shy）
3. 旁白：她小心地把视线移向窗外，像是在重新认识这场雷雨。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`chat_thunder_tease_result`
- 场景类型：连续对白
1. ${maid}：哪有雷声会怕我的啦……不过被你这么一说，确实没那么紧张了。（表情：soft_smile；动画：fade_in）
2. ${maid}：你总是能把奇怪的话说得很认真。（表情：shy）
3. ${maid}：但是……这种认真，我并不讨厌。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_rain`
- 场景说明：下雨天聊天。主题偏温柔、照顾和雨天回忆。所有阶段都能触发。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：下雨的时候，屋檐和窗边都会变得很安静呢。
- 玩家选项：
  - 【给她递热饮】
    - 选项说明：雨天就该让手心和心情都暖起来。
    - 跳转：`chat_rain_warm_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=0，moodDelta=2
  - 【聊雨天回忆】
    - 选项说明：问问她有没有喜欢的雨天记忆。
    - 跳转：`chat_rain_memory_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=-1，moodDelta=1
  - 【提醒别淋雨】
    - 选项说明：认真叮嘱她今天不要在外面逞强。
    - 跳转：`chat_rain_work_result`
    - 互动结果：positiveFavor=1，neutralFavor=1，negativeFavor=0，moodDelta=0

## 场景：`chat_rain_warm_result`
- 场景类型：连续对白
1. ${maid}：热热的……谢谢。雨天收到这种照顾，会让人记很久的。（表情：hot_smile；动画：fade_in）
2. 旁白：她捧着热饮，指尖慢慢暖了起来。（表情：soft_smile）
3. ${maid}：如果以后每个雨天都能这样，也许我会开始期待下雨。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`chat_rain_memory_result`
- 场景类型：连续对白
1. ${maid}：以前只是觉得雨声很远，现在会觉得它像是在提醒我，有人陪着我。（表情：soft_smile；动画：fade_in）
2. ${maid}：有些记忆不是因为事情特别大才重要。（表情：soft_smile）
3. ${maid}：而是因为那时候身边刚好有想记住的人。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_rain_work_result`
- 场景类型：连续对白
1. ${maid}：知道啦，我会小心的。你这么认真叮嘱，我也不好意思不听。（表情：soft_smile；动画：fade_in）
2. ${maid}：不过你也一样，别只顾着提醒我，自己却淋得湿漉漉的。（表情：shy）
3. ${maid}：要是感冒了，我可是会生气的。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_morning`
- 场景说明：早晨聊天。主题是认真说早安、问今天计划、提醒不要太累。所有阶段都能触发。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：早上好，${player}。今天也要一起慢慢开始吗？
- 玩家选项：
  - 【认真说早安】
    - 选项说明：用最普通但最稳定的方式开始一天。
    - 跳转：`chat_morning_greet_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=0，moodDelta=1
  - 【问今天计划】
    - 选项说明：让她说说今天想做的事情。
    - 跳转：`chat_morning_plan_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=-1，moodDelta=1
  - 【提醒别太累】
    - 选项说明：如果今天要忙，也希望她照顾好自己。
    - 跳转：`chat_morning_busy_result`
    - 互动结果：positiveFavor=1，neutralFavor=1，negativeFavor=0，moodDelta=0

## 场景：`chat_morning_greet_result`
- 场景类型：连续对白
1. ${maid}：早安。明明只是两个字，从你嘴里说出来就会变得很可靠。（表情：soft_smile；动画：fade_in）
2. 旁白：清晨的光落在她脸侧，她的语气也比平时轻快一些。（表情：soft_smile）
3. ${maid}：那今天也请多关照啦，${player}。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_morning_plan_result`
- 场景类型：连续对白
1. ${maid}：如果可以的话，我想先把房间整理好，然后……再和你多待一会儿。（表情：shy；动画：fade_in）
2. ${maid}：不是偷懒哦，只是如果能提前把事情做好，就能留下更多时间。（表情：soft_smile）
3. ${maid}：更多和你一起的时间。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_morning_busy_result`
- 场景类型：连续对白
1. ${maid}：嗯，我不会勉强自己的。你也是，不许偷偷把疲惫藏起来。（表情：soft_smile；动画：fade_in）
2. ${maid}：如果今天会很忙，那我们就约好晚上再好好说话。（表情：soft_smile）
3. ${maid}：不许忘记哦，我会记着的。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_night`
- 场景说明：夜晚聊天。主题是晚安、陪伴和更容易害羞的夜间气氛。所有阶段都能触发。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：已经这么晚了……${player}，你还不准备休息吗？
- 玩家选项：
  - 【劝她早点休息】
    - 选项说明：夜晚最适合把疲惫交给睡意。
    - 跳转：`chat_night_rest_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=0，moodDelta=1
  - 【再陪她一会儿】
    - 选项说明：如果她还睡不着，就多留一会儿。
    - 跳转：`chat_night_company_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=-1，moodDelta=1
  - 【说晚安前想你】
    - 选项说明：把晚安说得稍微直白一点。
    - 跳转：`chat_night_tease_result`
    - 互动结果：positiveFavor=2，neutralFavor=0，negativeFavor=-1，moodDelta=0

## 场景：`chat_night_rest_result`
- 场景类型：连续对白
1. ${maid}：那你也要早点睡。晚安的时候，要记得把我也放进梦里一点点。（表情：hot_smile；动画：fade_in）
2. 旁白：她说完后像是自己也觉得害羞，轻轻移开了视线。（表情：shy）
3. ${maid}：只是一点点就好……太多的话，我会不好意思的。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_night_company_result`
- 场景类型：连续对白
1. ${maid}：再一会儿就好。只要你在旁边，夜晚就不会显得太长。（表情：shy；动画：fade_in）
2. ${maid}：我不会一直任性的，真的。（表情：soft_smile）
3. ${maid}：只是现在，还想多听你说几句话。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_night_tease_result`
- 场景类型：连续对白
1. ${maid}：突然说这种话太犯规了……我会真的睡不着的。（表情：hot_smile；动画：fade_in）
2. ${maid}：你明明知道我晚上比较容易胡思乱想。（表情：shy）
3. ${maid}：要负责哦，至少要认真跟我说晚安。（表情：hot_smile）
- 下一幕：`idle`

## 分区说明：下面是按好感阶段拆开的专属聊天。只有到达对应好感度后，聊天才会进入这些阶段特化对白。

## 场景：`chat_stage_warm`
- 场景说明：第一阶段专属聊天，好感度 32~63。这个阶段已经能摸头，关系开始升温，但整体还是慢热、试探和一点点靠近。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：最近和你说话的时候，好像没有以前那么紧张了。
- 玩家选项：
  - 【夸她很努力】
    - 选项说明：认可她日常里的小小进步。
    - 跳转：`chat_stage_warm_pet_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=0，moodDelta=2
  - 【给她一点空间】
    - 选项说明：关系刚变近时，也尊重她的节奏。
    - 跳转：`chat_stage_warm_space_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=1，moodDelta=1
  - 【说想更了解她】
    - 选项说明：坦率表达想继续靠近。
    - 跳转：`chat_stage_warm_direct_result`
    - 互动结果：positiveFavor=2，neutralFavor=0，negativeFavor=-1，moodDelta=0

## 场景：`chat_stage_warm_pet_result`
- 场景说明：第一阶段 32~63：被夸努力。她会认真接受夸奖，但还不太会撒娇，反应偏克制和一点点不知所措。
- 场景类型：连续对白
1. ${maid}：努力是应该的吧。被你专门说出来，反而让我有点不知道该怎么回答。（表情：soft_smile；动画：fade_in）
2. ${maid}：不过……谢谢。我会把这句话当成今天的奖励。（表情：shy）
3. ${maid}：下次如果我做得更好，你也可以再这样告诉我。只、只是鼓励而已哦。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_warm_space_result`
- 场景说明：第一阶段 32~63：给她空间。她会觉得被尊重，比直接靠近更容易放松。
- 场景类型：连续对白
1. ${maid}：你愿意等我慢慢适应吗？这样的话……我会轻松很多。（表情：soft_smile；动画：fade_in）
2. ${maid}：我不是讨厌靠近，只是有时候不知道该怎么回应你的好意。（表情：shy）
3. ${maid}：所以先这样就好。等我准备好了，我会自己往前走一点的。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`chat_stage_warm_direct_result`
- 场景说明：第一阶段 32~63：想更了解她。她会把这句话听得很认真，但还会有点防备和害羞。
- 场景类型：连续对白
1. ${maid}：想了解我吗？这个说法……听起来很正式。（表情：shy；动画：fade_in）
2. ${maid}：我可能不是很会讲自己的事，也不一定每次都能说得很有趣。（表情：soft_smile）
3. ${maid}：但如果是你问的话，我可以试着一点点告诉你。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_close`
- 场景说明：第二阶段专属聊天，好感度 64~127。这个阶段已经能拥抱，关系明显更亲近，台词会更依赖、更想靠近。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：拥抱之后才发现，原来人的心跳真的会互相传过去。
- 玩家选项：
  - 【说她可以依靠你】
    - 选项说明：让她知道拥抱不是一时兴起。
    - 跳转：`chat_stage_close_hold_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=0，moodDelta=2
  - 【问她会不会害羞】
    - 选项说明：轻轻逗她一下，看她的反应。
    - 跳转：`chat_stage_close_shy_result`
    - 互动结果：positiveFavor=2，neutralFavor=0，negativeFavor=-1，moodDelta=0
  - 【约定下次也抱抱】
    - 选项说明：把这份亲近变成两个人的小约定。
    - 跳转：`chat_stage_close_future_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=0，moodDelta=1

## 场景：`chat_stage_close_hold_result`
- 场景说明：第二阶段 64~127：可以依靠你。已经能拥抱，但她还会嘴硬，害羞里带一点依赖。
- 场景类型：连续对白
1. ${maid}：可以依靠你……这种话不要说得太突然，我会当真的。（表情：shy；动画：fade_in）
2. ${maid}：累的时候想靠近你，开心的时候也想先告诉你。这样会不会太任性？（表情：hot_smile）
3. ${maid}：如果你说不介意，那我可能真的会越来越依赖你。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_close_shy_result`
- 场景说明：第二阶段 64~127：问她会不会害羞。她已经很明显会害羞，但仍然想维持一点正经。
- 场景类型：连续对白
1. ${maid}：当、当然会害羞啊。你为什么能这么自然地问出来？（表情：shy；动画：fade_in）
2. ${maid}：被你看着的时候，我连手该放哪里都要想一下。（表情：hot_smile）
3. ${maid}：不过你要是笑得太开心，我就真的不理你了。大概。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_close_future_result`
- 场景说明：第二阶段 64~127：约定下次也抱抱。她开始期待下次，但表达方式还比较含蓄。
- 场景类型：连续对白
1. ${maid}：下次也可以吗？我是说……如果你也愿意的话。（表情：shy；动画：fade_in）
2. ${maid}：我不会每次都主动说想要拥抱，所以你要稍微懂一点。（表情：hot_smile）
3. ${maid}：约好了哦。不要让我一个人记得。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`chat_stage_dating`
- 场景说明：第三阶段专属聊天，好感度 128~197。这个阶段进入交往期，已经能亲吻，台词会更直球、更害羞，也更像恋人之间的对话。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：交往之后，好像连普通的对话都会变得不太普通。
- 玩家选项：
  - 【约一次散步】
    - 选项说明：不一定要远行，只要两个人一起走。
    - 跳转：`chat_stage_dating_date_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=0，moodDelta=2
  - 【叫她的名字】
    - 选项说明：用更亲近的语气认真叫她。
    - 跳转：`chat_stage_dating_call_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=-1，moodDelta=1
  - 【说会认真对待她】
    - 选项说明：把关系说清楚，让她安心。
    - 跳转：`chat_stage_dating_promise_result`
    - 互动结果：positiveFavor=4，neutralFavor=2，negativeFavor=0，moodDelta=2

## 场景：`chat_stage_dating_date_result`
- 场景说明：第三阶段 128~197：交往期约散步。她已经明确期待约会，会主动提出小小亲密。
- 场景类型：连续对白
1. ${maid}：散步吗？如果是约会的话，我会想提前整理一下头发。（表情：hot_smile；动画：fade_in）
2. ${maid}：不用去很远的地方，只要能慢慢走、慢慢说话就好。（表情：soft_smile）
3. ${maid}：不过……牵手这件事，你可以主动一点。我会假装只是顺路。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_dating_call_result`
- 场景说明：第三阶段 128~197：叫她名字。她会因为恋人语气而害羞，也会想听更多。
- 场景类型：连续对白
1. ${maid}：你刚才叫我的方式……和平时有点不一样。（表情：shy；动画：fade_in）
2. ${maid}：我明明听过很多次自己的名字，可从你嘴里说出来就会变得很奇怪。（表情：hot_smile）
3. ${maid}：不是讨厌。是……还想再听一次。（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_dating_promise_result`
- 场景说明：第三阶段 128~197：认真承诺。她已经喜欢玩家，所以会认真回应，但仍然带害羞。
- 场景类型：连续对白
1. ${maid}：你突然这么认真，我会不知道该把眼睛放在哪里。（表情：shy；动画：fade_in）
2. ${maid}：可是我听见了，也记住了。你说会认真对待我。（表情：soft_smile）
3. ${maid}：那我也会认真喜欢你。说出来还是很害羞，但这是我的回答。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_stage_marriage`
- 场景说明：第四阶段专属聊天，好感度 198 及以上。这个阶段偏婚后/长期关系语气，重点是共同生活、承诺和稳定的亲密感。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：走到现在这一步，回头看好像每一天都变得很珍贵。
- 玩家选项：
  - 【聊未来的家】
    - 选项说明：想象以后一起生活的细节。
    - 跳转：`chat_stage_marriage_home_result`
    - 互动结果：positiveFavor=3，neutralFavor=2，negativeFavor=0，moodDelta=2
  - 【提到重要承诺】
    - 选项说明：把快要说出口的话先认真铺垫。
    - 跳转：`chat_stage_marriage_ring_result`
    - 互动结果：positiveFavor=4，neutralFavor=2，negativeFavor=0，moodDelta=2
  - 【珍惜今天】
    - 选项说明：不急着跳到未来，先把今天过好。
    - 跳转：`chat_stage_marriage_daily_result`
    - 互动结果：positiveFavor=3，neutralFavor=1，negativeFavor=1，moodDelta=1

## 场景：`chat_stage_marriage_home_result`
- 场景说明：第四阶段 198+：聊未来的家。婚后/长期关系语气，重点是安心、日常和共同生活。
- 场景类型：连续对白
1. ${maid}：如果是和你一起生活，我想把每个角落都收拾得暖暖的。（表情：soft_smile；动画：fade_in）
2. ${maid}：早晨有你，晚上也有你，中间那些琐碎的小事就不会只是琐碎了。（表情：soft_smile）
3. ${maid}：不过打扫高处的时候还是要你来。谁让这是我们的家呢。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_stage_marriage_ring_result`
- 场景说明：第四阶段 198+：重要承诺。她不再只是害羞，而是会认真把未来放进话里。
- 场景类型：连续对白
1. ${maid}：承诺这种东西，我以前会觉得离自己很远。（表情：soft_smile；动画：fade_in）
2. ${maid}：可是如果对象是你，我就会想把它放在每天都能看见的地方。（表情：hot_smile）
3. ${maid}：所以不要只说一次。以后也要一直让我听见，好吗？（表情：shy）
- 下一幕：`idle`

## 场景：`chat_stage_marriage_daily_result`
- 场景说明：第四阶段 198+：珍惜今天。长期关系的可爱日常，不是轰轰烈烈，而是稳定地互相偏爱。
- 场景类型：连续对白
1. ${maid}：今天也很普通，但如果和你一起过，就会变得很重要。（表情：soft_smile；动画：fade_in）
2. ${maid}：我想把这种普通的日子攒起来，攒到以后回头看时全都是你。（表情：soft_smile）
3. ${maid}：所以今天也请多陪我一点。不是请求，是家人的优先事项。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_daily`
- 场景说明：普通日常聊天兜底。当前面没有命中特殊天气、特殊时段或阶段专属话题时，就会走这里。
- 场景类型：选项场景
- 舞台提示：
  - ${maid}：今天想聊什么？如果只是闲聊，我也很愿意听你说。
- 玩家选项：
  - 【夸她今天很可爱】
    - 选项说明：直接但有效的夸奖。
    - 跳转：`chat_daily_praise_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=-1，moodDelta=1
  - 【问她累不累】
    - 选项说明：关心她今天有没有好好休息。
    - 跳转：`chat_daily_work_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=0，moodDelta=1
  - 【一起发呆】
    - 选项说明：什么都不做，只共享一小段时间。
    - 跳转：`chat_daily_quiet_result`
    - 互动结果：positiveFavor=2，neutralFavor=1，negativeFavor=1，moodDelta=1
  - 【轻轻逗她】
    - 选项说明：气氛好时可以让她害羞一下。
    - 跳转：`chat_daily_tease_result`
    - 互动结果：positiveFavor=2，neutralFavor=0，negativeFavor=-1，moodDelta=0

## 场景：`chat_daily_praise_result`
- 场景类型：连续对白
1. ${maid}：突、突然这么说……我会不好意思的。不过，可以再说一次吗？（表情：hot_smile；动画：fade_in）
2. 旁白：她像是想装作平静，却忍不住偷偷看你的反应。（表情：shy）
3. ${maid}：如果你是真心这么觉得的话，我会努力记住今天的。（表情：soft_smile）
- 下一幕：`idle`

## 场景：`chat_daily_work_result`
- 场景类型：连续对白
1. ${maid}：有一点点累，但听见你问我，就觉得今天也被好好放在心上了。（表情：soft_smile；动画：fade_in）
2. ${maid}：其实只要有人注意到“我累了”，疲惫就会轻很多。（表情：soft_smile）
3. ${maid}：所以谢谢你，${player}。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_daily_quiet_result`
- 场景类型：连续对白
1. ${maid}：这样安静待着也很好。不是每句话都要说出口，心情也能传过去。（表情：soft_smile；动画：fade_in）
2. 旁白：两个人之间短暂地安静下来，只剩下很轻的呼吸声。（表情：soft_smile）
3. ${maid}：如果是和你一起发呆，我好像也不会觉得浪费时间。（表情：hot_smile）
- 下一幕：`idle`

## 场景：`chat_daily_tease_result`
- 场景类型：连续对白
1. ${maid}：你又故意这样……再这样我就真的不理你三秒钟。（表情：shy；动画：fade_in）
2. 旁白：她认真地移开视线，却在第二秒就偷偷看了回来。（表情：soft_smile）
3. ${maid}：……三秒太久了，算了，这次先原谅你。（表情：hot_smile）
- 下一幕：`idle`
