const STORAGE_KEY = "maidmarriage-dialogue-studio";

/**
 * 对话编辑器默认工程。
 *
 * 这里使用场景式结构，而不是直接把所有文本平铺成键值表。
 * 这样做有两个好处：
 * 1. 前端可以稳定预览“谁说话、显示什么头像、当前有哪些选项、会触发哪些动作”；
 * 2. 后续若要导出成模组实际使用的键值文本或脚本格式，只需要再加一层导出器即可。
 */
const DEFAULT_PROJECT = {
  meta: {
    title: "默认拥抱剧情工程",
    version: 1,
    lastEdited: new Date().toISOString(),
  },
  scenes: [
    {
      id: "hug.default",
      speaker: "酒尾狐",
      portrait: "textures/gui/smile.png",
      hint: "单击对话框可立即显示全部文本，滚轮仍可调整缩放。",
      zoom: 104,
      dialogue: "就这样安静地抱一会儿吧……现在不用说太多话，也已经很安心了。",
      options: [
        {
          id: "hug",
          label: "拥抱",
          desc: "继续抱着她，不打断此刻的安静。",
          action: "hug",
          key: "J",
        },
        {
          id: "kiss",
          label: "亲吻",
          desc: "轻轻亲近一下，让气氛再靠近一点。",
          action: "kiss",
          key: "K",
        },
        {
          id: "pet",
          label: "摸头",
          desc: "抬手摸摸头，给她一点温柔反馈。",
          action: "pet",
          key: "L",
        },
      ],
      actions: [
        {
          id: "look",
          type: "camera",
          target: "maid",
          duration: 350,
          payload: {
            mode: "lock_look",
            keepEyeContact: true,
          },
        },
        {
          id: "pose",
          type: "animation",
          target: "maid",
          duration: 800,
          payload: {
            clip: "hug_idle",
            layer: "body",
          },
        },
      ],
    },
  ],
};

let project = loadProject();
let currentSceneIndex = 0;

const sceneListEl = document.getElementById("scene-list");
const optionsEditorEl = document.getElementById("options-editor");
const actionsEditorEl = document.getElementById("actions-editor");

const fields = {
  sceneId: document.getElementById("scene-id"),
  speaker: document.getElementById("speaker"),
  portrait: document.getElementById("portrait"),
  hint: document.getElementById("hint"),
  zoom: document.getElementById("zoom"),
  dialogue: document.getElementById("dialogue"),
};

const preview = {
  speaker: document.getElementById("preview-speaker"),
  portrait: document.getElementById("preview-portrait"),
  text: document.getElementById("preview-text"),
  hint: document.getElementById("preview-hint"),
  zoom: document.getElementById("preview-zoom"),
  options: document.getElementById("preview-options"),
  actions: document.getElementById("preview-actions"),
};

bindGlobalEvents();
renderAll();

function loadProject() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return structuredClone(DEFAULT_PROJECT);
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed.scenes) || parsed.scenes.length === 0) {
      return structuredClone(DEFAULT_PROJECT);
    }
    return sanitizeProject(parsed);
  } catch (error) {
    console.warn("读取本地工程失败，已回退到默认工程。", error);
    return structuredClone(DEFAULT_PROJECT);
  }
}

function saveProject() {
  project.meta.lastEdited = new Date().toISOString();
  localStorage.setItem(STORAGE_KEY, JSON.stringify(project, null, 2));
}

function currentScene() {
  if (!project.scenes.length) {
    project.scenes.push(createScene());
  }
  currentSceneIndex = Math.max(0, Math.min(currentSceneIndex, project.scenes.length - 1));
  return project.scenes[currentSceneIndex];
}

function renderAll() {
  renderSceneList();
  renderSceneFields();
  renderOptionsEditor();
  renderActionsEditor();
  renderPreview();
  saveProject();
}

function renderSceneList() {
  sceneListEl.innerHTML = "";
  project.scenes.forEach((scene, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `scene-item ${index === currentSceneIndex ? "active" : ""}`;
    button.innerHTML = `
      <strong>${escapeHtml(scene.speaker || "未命名角色")}</strong>
      <small>${escapeHtml(scene.id || "scene.unnamed")}</small>
    `;
    button.addEventListener("click", () => {
      currentSceneIndex = index;
      renderAll();
    });
    sceneListEl.appendChild(button);
  });
}

function renderSceneFields() {
  const scene = currentScene();
  fields.sceneId.value = scene.id;
  fields.speaker.value = scene.speaker;
  fields.portrait.value = scene.portrait;
  fields.hint.value = scene.hint;
  fields.zoom.value = scene.zoom;
  fields.dialogue.value = scene.dialogue;
}

function renderOptionsEditor() {
  const scene = currentScene();
  optionsEditorEl.innerHTML = "";
  scene.options.forEach((option, index) => {
    const node = document.getElementById("option-template").content.firstElementChild.cloneNode(true);
    node.querySelector("[data-field='label']").value = option.label;
    node.querySelector("[data-field='desc']").value = option.desc;
    node.querySelector("[data-field='action']").value = option.action;
    node.querySelector("[data-field='key']").value = option.key;

    bindItemCard(node, index, scene.options, () => renderAll());
    optionsEditorEl.appendChild(node);
  });
}

function renderActionsEditor() {
  const scene = currentScene();
  actionsEditorEl.innerHTML = "";
  scene.actions.forEach((action, index) => {
    const node = document.getElementById("action-template").content.firstElementChild.cloneNode(true);
    node.querySelector("[data-field='type']").value = action.type;
    node.querySelector("[data-field='target']").value = action.target;
    node.querySelector("[data-field='duration']").value = action.duration;
    node.querySelector("[data-field='payload']").value = JSON.stringify(action.payload, null, 2);

    bindItemCard(node, index, scene.actions, () => renderAll(), {
      payloadAsJson: true,
    });
    actionsEditorEl.appendChild(node);
  });
}

function renderPreview() {
  const scene = currentScene();
  preview.speaker.textContent = scene.speaker || "未命名角色";
  preview.text.textContent = scene.dialogue || "这里会显示当前场景的对话正文。";
  preview.hint.textContent = scene.hint || "";
  preview.zoom.textContent = `缩放：${scene.zoom || 100}%`;
  preview.portrait.title = scene.portrait || "未设置头像";

  /**
   * 前端预览阶段先用简单渐变块代表头像。
   * 这里不强行读取本地 PNG 路径，避免浏览器因 file:// 跨域或绝对路径差异无法显示。
   * 真正落地到模组时，资源路径还是会完整保存在工程 JSON 中。
   */
  preview.portrait.style.backgroundImage =
    "linear-gradient(180deg, rgba(255,255,255,0.14), rgba(255,255,255,0.04)), linear-gradient(180deg, #7e3560 0%, #50203d 100%)";

  preview.options.innerHTML = "";
  scene.options.forEach((option) => {
    const item = document.createElement("article");
    item.className = "preview-option";
    item.innerHTML = `
      <strong>${escapeHtml(option.label || "未命名选项")}</strong>
      <small>${escapeHtml(option.desc || "")}</small>
    `;
    preview.options.appendChild(item);
  });

  preview.actions.innerHTML = "";
  scene.actions.forEach((action) => {
    const chip = document.createElement("span");
    chip.className = "action-chip";
    chip.textContent = `${action.type} · ${action.target || "未指定"} · ${action.duration || 0}ms`;
    preview.actions.appendChild(chip);
  });
}

function bindGlobalEvents() {
  fields.sceneId.addEventListener("input", (event) => updateSceneField("id", event.target.value));
  fields.speaker.addEventListener("input", (event) => updateSceneField("speaker", event.target.value));
  fields.portrait.addEventListener("input", (event) => updateSceneField("portrait", event.target.value));
  fields.hint.addEventListener("input", (event) => updateSceneField("hint", event.target.value));
  fields.zoom.addEventListener("input", (event) => updateSceneField("zoom", clampNumber(event.target.value, 50, 200, 100)));
  fields.dialogue.addEventListener("input", (event) => updateSceneField("dialogue", event.target.value));

  document.getElementById("new-scene-btn").addEventListener("click", () => {
    project.scenes.push(createScene());
    currentSceneIndex = project.scenes.length - 1;
    renderAll();
  });

  document.getElementById("duplicate-scene-btn").addEventListener("click", () => {
    const copy = structuredClone(currentScene());
    copy.id = `${copy.id || "scene"}.copy`;
    project.scenes.splice(currentSceneIndex + 1, 0, copy);
    currentSceneIndex += 1;
    renderAll();
  });

  document.getElementById("delete-scene-btn").addEventListener("click", () => {
    if (project.scenes.length <= 1) {
      project.scenes[0] = createScene();
      currentSceneIndex = 0;
    } else {
      project.scenes.splice(currentSceneIndex, 1);
      currentSceneIndex = Math.max(0, currentSceneIndex - 1);
    }
    renderAll();
  });

  document.getElementById("add-option-btn").addEventListener("click", () => {
    currentScene().options.push(createOption());
    renderAll();
  });

  document.getElementById("add-action-btn").addEventListener("click", () => {
    currentScene().actions.push(createAction());
    renderAll();
  });

  document.getElementById("export-btn").addEventListener("click", exportProject);
  document.getElementById("import-btn").addEventListener("click", () => document.getElementById("import-input").click());
  document.getElementById("import-input").addEventListener("change", importProject);
  document.getElementById("reset-btn").addEventListener("click", () => {
    project = structuredClone(DEFAULT_PROJECT);
    currentSceneIndex = 0;
    renderAll();
  });
}

function bindItemCard(node, index, targetArray, rerender, options = {}) {
  const entries = node.querySelectorAll("[data-field]");
  entries.forEach((input) => {
    input.addEventListener("input", () => {
      const field = input.dataset.field;
      if (options.payloadAsJson && field === "payload") {
        targetArray[index][field] = tryParseJson(input.value);
      } else if (field === "duration") {
        targetArray[index][field] = clampNumber(input.value, 0, 60000, 0);
      } else {
        targetArray[index][field] = input.value;
      }
      rerender();
    });
  });

  node.querySelector("[data-action='remove']").addEventListener("click", () => {
    targetArray.splice(index, 1);
    rerender();
  });
}

function updateSceneField(field, value) {
  currentScene()[field] = value;
  renderAll();
}

function exportProject() {
  const blob = new Blob([JSON.stringify(project, null, 2)], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${sanitizeFileName(project.meta.title || "maidmarriage-dialogue-project")}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

async function importProject(event) {
  const file = event.target.files?.[0];
  if (!file) {
    return;
  }
  const text = await file.text();
  project = sanitizeProject(JSON.parse(text));
  currentSceneIndex = 0;
  renderAll();
  event.target.value = "";
}

function sanitizeProject(input) {
  return {
    meta: {
      title: input?.meta?.title || "未命名工程",
      version: Number(input?.meta?.version || 1),
      lastEdited: input?.meta?.lastEdited || new Date().toISOString(),
    },
    scenes: (Array.isArray(input?.scenes) ? input.scenes : [createScene()]).map((scene) => ({
      id: scene?.id || "scene.unnamed",
      speaker: scene?.speaker || "未命名角色",
      portrait: scene?.portrait || "textures/gui/smile.png",
      hint: scene?.hint || "",
      zoom: clampNumber(scene?.zoom, 50, 200, 100),
      dialogue: scene?.dialogue || "",
      options: Array.isArray(scene?.options) ? scene.options.map((option) => ({
        id: option?.id || crypto.randomUUID(),
        label: option?.label || "未命名选项",
        desc: option?.desc || "",
        action: option?.action || "hug",
        key: option?.key || "",
      })) : [],
      actions: Array.isArray(scene?.actions) ? scene.actions.map((action) => ({
        id: action?.id || crypto.randomUUID(),
        type: action?.type || "animation",
        target: action?.target || "",
        duration: clampNumber(action?.duration, 0, 60000, 0),
        payload: typeof action?.payload === "object" && action.payload !== null ? action.payload : {},
      })) : [],
    })),
  };
}

function createScene() {
  return {
    id: `scene.${crypto.randomUUID().slice(0, 8)}`,
    speaker: "新角色",
    portrait: "textures/gui/smile.png",
    hint: "单击对话框可立即显示全部文本。",
    zoom: 100,
    dialogue: "这里是新的剧情内容。",
    options: [createOption()],
    actions: [],
  };
}

function createOption() {
  return {
    id: crypto.randomUUID(),
    label: "新选项",
    desc: "这里填写这个选项的说明。",
    action: "hug",
    key: "",
  };
}

function createAction() {
  return {
    id: crypto.randomUUID(),
    type: "animation",
    target: "maid",
    duration: 400,
    payload: {
      clip: "custom_clip",
    },
  };
}

function clampNumber(value, min, max, fallback) {
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, numeric));
}

function tryParseJson(text) {
  try {
    return text?.trim() ? JSON.parse(text) : {};
  } catch {
    /**
     * 动作参数编辑时允许用户先输入半成品 JSON。
     * 这里不要直接抛错中断，而是把原始字符串包起来，避免编辑体验被打断。
     */
    return {
      __raw: text,
    };
  }
}

function sanitizeFileName(name) {
  return name.replace(/[\\\\/:*?\"<>|]/g, "_");
}

function escapeHtml(text) {
  return String(text)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}
