# Dialogue Studio

这是给 `maid-marriage-forge-1.20.1` 配的前端剧情编辑器原型。

## 当前能力

- 编辑基础场景信息：`scene id`、说话者、头像路径、提示文案、缩放值、正文
- 编辑选项列表：标题、描述、动作类型、按键/指令
- 编辑动作步骤：类型、目标、持续时间、参数 JSON
- 右侧实时预览：基础对话框、选项区、右下角缩放角标、动作步骤标签
- 支持本地自动保存、导入 JSON、导出 JSON

## 运行方式

直接双击打开 `index.html` 即可使用。

如果想避免浏览器对本地文件的一些限制，也可以在当前目录启动一个最简单的静态服务器：

```powershell
cd tools/dialogue-studio
python -m http.server 8080
```

然后浏览器访问：

```text
http://localhost:8080
```

## 数据格式

当前工程文件是一个“场景式” JSON，大致结构如下：

```json
{
  "meta": {
    "title": "工程名",
    "version": 1
  },
  "scenes": [
    {
      "id": "hug.default",
      "speaker": "酒尾狐",
      "portrait": "textures/gui/smile.png",
      "hint": "提示文案",
      "zoom": 104,
      "dialogue": "正文",
      "options": [],
      "actions": []
    }
  ]
}
```

## 后续建议

- 增加“导出到模组键值台本”的转换器
- 增加动作模板库（拥抱、摸头、亲吻、别头、镜头缩放）
- 增加时间轴编辑器，而不只是动作列表
- 增加立绘贴图槽位和多表情切换
