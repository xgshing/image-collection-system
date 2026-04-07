const state = {
  settings: {
    apiBaseUrl: "https://api.yuntuxia.top",
    sessionToken: "",
    importConcurrency: 3,
    exportConcurrency: 3,
    lastExportDirectory: "",
    lastImportDirectories: []
  },
  profile: null,
  availableTags: [],
  selectedExportTags: new Set(),
  exportDirectory: "",
  importDraft: {
    sessionId: "",
    folders: []
  },
  login: {
    token: "",
    qrImageUrl: "",
    statusMessage: "",
    pollTimer: null
  },
  timeline: []
};

const dom = {};

function bindDom() {
  dom.navLinks = Array.from(document.querySelectorAll(".nav-link"));
  dom.panels = Array.from(document.querySelectorAll(".panel"));
  dom.topAccountBlock = document.getElementById("topAccountBlock");
  dom.logoutButton = document.getElementById("logoutButton");
  dom.createLoginCard = document.getElementById("createLoginCard");
  dom.loginQrImage = document.getElementById("loginQrImage");
  dom.loginStatus = document.getElementById("loginStatus");
  dom.metricTags = document.getElementById("metricTags");
  dom.metricImportFolders = document.getElementById("metricImportFolders");
  dom.metricExportTags = document.getElementById("metricExportTags");
  dom.importEmptyState = document.getElementById("importEmptyState");
  dom.importFolderList = document.getElementById("importFolderList");
  dom.chooseImportFoldersButton = document.getElementById("chooseImportFoldersButton");
  dom.startImportButton = document.getElementById("startImportButton");
  dom.tagSelector = document.getElementById("tagSelector");
  dom.exportDirectoryLabel = document.getElementById("exportDirectoryLabel");
  dom.chooseExportDirectoryButton = document.getElementById("chooseExportDirectoryButton");
  dom.startExportButton = document.getElementById("startExportButton");
  dom.taskTimeline = document.getElementById("taskTimeline");
  dom.importProgressCard = document.getElementById("importProgressCard");
  dom.importProgressTitle = document.getElementById("importProgressTitle");
  dom.importProgressPercent = document.getElementById("importProgressPercent");
  dom.importProgressBar = document.getElementById("importProgressBar");
  dom.importProgressMeta = document.getElementById("importProgressMeta");
  dom.exportProgressCard = document.getElementById("exportProgressCard");
  dom.exportProgressTitle = document.getElementById("exportProgressTitle");
  dom.exportProgressPercent = document.getElementById("exportProgressPercent");
  dom.exportProgressBar = document.getElementById("exportProgressBar");
  dom.exportProgressMeta = document.getElementById("exportProgressMeta");
}

function normalizeBaseUrl(value) {
  const raw = String(value || "").trim();
  return raw ? raw.replace(/\/+$/, "") : "https://api.yuntuxia.top";
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function setPanel(panelName) {
  dom.navLinks.forEach((node) => node.classList.toggle("active", node.dataset.panel === panelName));
  dom.panels.forEach((node) => node.classList.toggle("active", node.id === `panel-${panelName}`));
}

function getDefaultAvatarHtml() {
  return '<div class="avatar-placeholder avatar-default" aria-hidden="true"></div>';
}

function renderConnectionState() {
  const connected = Boolean(state.profile && state.settings.sessionToken);
  dom.logoutButton.classList.toggle("hidden", !connected);
}

function renderAccount() {
  if (!state.profile) {
    dom.topAccountBlock.innerHTML = `
      ${getDefaultAvatarHtml()}
      <div>
        <div class="account-name">未登录</div>
        <div class="account-meta">请使用微信扫码登录</div>
      </div>
    `;
    return;
  }

  const avatarUrl = String(state.profile.avatarUrl || "").trim();
  const avatarHtml = avatarUrl
    ? `<img class="account-avatar" src="${avatarUrl}" alt="用户头像" />`
    : getDefaultAvatarHtml();

  dom.topAccountBlock.innerHTML = `
    ${avatarHtml}
    <div>
      <div class="account-name">${escapeHtml(state.profile.nickName || "已登录用户")}</div>
      <div class="account-meta">已登录</div>
    </div>
  `;
}

function renderMetrics() {
  dom.metricTags.textContent = String(state.availableTags.length);
  dom.metricImportFolders.textContent = String(state.importDraft.folders.length);
  dom.metricExportTags.textContent = String(state.selectedExportTags.size);
}

function renderImportFolders() {
  const hasFolders = state.importDraft.folders.length > 0;
  dom.importEmptyState.style.display = hasFolders ? "none" : "block";
  dom.importFolderList.innerHTML = state.importDraft.folders
    .map((folder) => `
      <div class="folder-item">
        <div class="folder-item-header">
          <div class="folder-title">${escapeHtml(folder.folderName)}</div>
          <div class="timeline-status ${folder.fileCount ? "running" : "failed"}">${folder.fileCount} 个文件</div>
        </div>
        <div class="folder-meta">${escapeHtml(folder.folderPath)}</div>
        <div class="folder-meta">预览：${escapeHtml(folder.previewFiles.join(", ") || "未发现可导入图片文件")}</div>
        <label class="folder-field">
          <span>标签名</span>
          <input data-folder-id="${folder.folderId}" class="folder-tag-input" value="${escapeHtml(folder.tagName || folder.defaultTag)}" />
        </label>
      </div>
    `)
    .join("");

  dom.startImportButton.disabled = !(hasFolders && state.settings.sessionToken);

  Array.from(document.querySelectorAll(".folder-tag-input")).forEach((input) => {
    input.addEventListener("input", (event) => {
      const folder = state.importDraft.folders.find((item) => item.folderId === event.target.dataset.folderId);
      if (folder) {
        folder.tagName = event.target.value;
      }
    });
  });
}

function renderTagSelectors() {
  const html = state.availableTags
    .map(
      (tag) =>
        `<button class="tag-chip ${state.selectedExportTags.has(tag) ? "selected" : ""}" data-tag="${escapeHtml(tag)}">${escapeHtml(tag)}</button>`
    )
    .join("");

  dom.tagSelector.innerHTML = html || '<div class="empty-state">当前还没有可用标签。</div>';

  Array.from(document.querySelectorAll(".tag-chip")).forEach((node) => {
    node.addEventListener("click", () => {
      const tag = node.dataset.tag;
      if (state.selectedExportTags.has(tag)) {
        state.selectedExportTags.delete(tag);
      } else {
        state.selectedExportTags.add(tag);
      }
      renderTagSelectors();
      renderMetrics();
      updateExportButtons();
    });
  });
}

function renderExportDirectory() {
  dom.exportDirectoryLabel.textContent = state.exportDirectory || state.settings.lastExportDirectory || "尚未选择导出目录。";
  updateExportButtons();
}

function updateExportButtons() {
  const enabled = Boolean(
    state.settings.sessionToken &&
      (state.exportDirectory || state.settings.lastExportDirectory) &&
      state.selectedExportTags.size
  );
  dom.startExportButton.disabled = !enabled;
}

function translateTaskStatus(status) {
  if (status === "running") return "进行中";
  if (status === "completed") return "已完成";
  if (status === "failed") return "失败";
  return status || "未知";
}

function renderTimeline() {
  if (!state.timeline.length) {
    dom.taskTimeline.innerHTML = '<div class="empty-state">当前还没有执行过任务。</div>';
    return;
  }

  dom.taskTimeline.innerHTML = state.timeline
    .map(
      (entry) => `
        <div class="timeline-item">
          <div class="timeline-header">
            <strong>${escapeHtml(entry.title)}</strong>
            <span class="timeline-status ${entry.status}">${escapeHtml(translateTaskStatus(entry.status))}</span>
          </div>
          <div class="timeline-meta">${escapeHtml(entry.message)}</div>
          <div class="timeline-meta">${escapeHtml(entry.meta)}</div>
        </div>
      `
    )
    .join("");
}

function renderLogin() {
  const hasQrImage = Boolean(state.login.qrImageUrl);
  dom.loginQrImage.hidden = !hasQrImage;
  dom.loginQrImage.src = hasQrImage ? state.login.qrImageUrl : "";
  dom.loginStatus.textContent = state.login.statusMessage;
  dom.loginStatus.classList.toggle("hidden", !state.login.statusMessage);
}

function renderAll() {
  renderConnectionState();
  renderAccount();
  renderMetrics();
  renderImportFolders();
  renderTagSelectors();
  renderExportDirectory();
  renderTimeline();
  renderLogin();
}

function pushTimeline(title, message, status, meta = "") {
  state.timeline.unshift({
    title,
    message,
    status,
    meta: meta || new Date().toLocaleString("zh-CN")
  });
  state.timeline = state.timeline.slice(0, 12);
  renderTimeline();
}

async function saveSettings(patch) {
  state.settings = await window.desktopApi.settings.save({
    ...patch,
    apiBaseUrl: normalizeBaseUrl(patch?.apiBaseUrl ?? state.settings.apiBaseUrl)
  });
  renderAll();
}

async function request(url, options = {}) {
  const response = await window.desktopApi.http.request({
    url,
    method: options.method,
    headers: options.headers,
    body: options.body
  });
  const text = response.text;
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch (error) {
    payload = text;
  }
  if (!response.ok) {
    throw new Error(payload?.message || payload || `请求失败：${response.status}`);
  }
  return payload;
}

async function fetchProfile() {
  if (!state.settings.sessionToken) {
    state.profile = null;
    renderAll();
    return;
  }

  try {
    state.profile = await request(`${state.settings.apiBaseUrl}/api/auth/me`, {
      headers: {
        Authorization: `Bearer ${state.settings.sessionToken}`
      }
    });
  } catch (error) {
    state.profile = null;
    pushTimeline("登录状态刷新失败", error.message || "无法加载用户资料。", "failed");
  }
  renderAll();
}

async function fetchTags() {
  if (!state.settings.sessionToken) {
    state.availableTags = [];
    renderAll();
    return;
  }

  try {
    const payload = await request(`${state.settings.apiBaseUrl}/api/image/tags`, {
      headers: {
        Authorization: `Bearer ${state.settings.sessionToken}`
      }
    });
    state.availableTags = Array.isArray(payload) ? payload : [];
  } catch (error) {
    state.availableTags = [];
    pushTimeline("标签刷新失败", error.message || "无法加载标签列表。", "failed");
  }
  renderAll();
}

function stopLoginPolling() {
  if (state.login.pollTimer) {
    clearInterval(state.login.pollTimer);
    state.login.pollTimer = null;
  }
}

async function ensureLoginSession(force = false) {
  if (state.settings.sessionToken || state.profile) {
    return;
  }
  if (!force && state.login.token && state.login.qrImageUrl) {
    return;
  }
  try {
    await createLoginSession();
  } catch (error) {
    console.error(error);
  }
}

async function createLoginSession() {
  stopLoginPolling();
  state.login.qrImageUrl = "";
  state.login.token = "";
  state.login.statusMessage = "";
  renderLogin();

  let payload;
  try {
    payload = await request(`${state.settings.apiBaseUrl}/api/plugin/login/session`, {
      method: "POST"
    });
    state.login.token = payload.token;
    state.login.qrImageUrl = `${state.settings.apiBaseUrl}/api/plugin/login/session/${payload.token}/minicode?ts=${Date.now()}`;
    state.login.statusMessage = "";
    renderLogin();
    pushTimeline("登录会话已创建", "桌面端授权二维码已生成。", "running");
  } catch (error) {
    state.login.qrImageUrl = "";
    state.login.statusMessage = error.message || "后端无法访问";
    renderLogin();
    pushTimeline("登录会话创建失败", error.message || "无法创建二维码会话。", "failed");
    throw error;
  }

  state.login.pollTimer = setInterval(async () => {
    try {
      const session = await request(`${state.settings.apiBaseUrl}/api/plugin/login/session/${payload.token}`);
      if (session.status === "confirmed" && session.sessionToken) {
        stopLoginPolling();
        state.profile = session.profile || null;
        state.login.statusMessage = "";
        await saveSettings({ sessionToken: session.sessionToken });
        await fetchProfile();
        await fetchTags();
        pushTimeline("授权完成", "桌面端已成功登录。", "completed");
      } else if (session.status === "expired") {
        stopLoginPolling();
        state.login.statusMessage = "二维码已过期，正在重新生成。";
        renderLogin();
        pushTimeline("登录会话已过期", "二维码在确认前已超时失效。", "failed");
        await ensureLoginSession(true);
      }
    } catch (error) {
      stopLoginPolling();
      state.login.statusMessage = error.message || "无法轮询登录状态。";
      renderLogin();
      pushTimeline("登录状态轮询失败", error.message || "无法轮询登录状态。", "failed");
    }
  }, 2000);
}

async function chooseImportFolders() {
  const selection = await window.desktopApi.dialog.selectImportFolders();
  if (selection.canceled || !selection.filePaths.length) {
    return;
  }

  const payload = await window.desktopApi.tasks.prepareImportFolders({
    folders: selection.filePaths
  });
  state.importDraft.sessionId = payload.sessionId;
  state.importDraft.folders = (payload.folders || []).map((folder) => ({
    ...folder,
    tagName: folder.defaultTag
  }));
  await saveSettings({ lastImportDirectories: selection.filePaths });
  pushTimeline("导入文件夹已准备", `已准备 ${state.importDraft.folders.length} 个文件夹等待上传。`, "completed");
  renderAll();
}

async function chooseExportDirectory() {
  const selection = await window.desktopApi.dialog.selectExportDirectory();
  if (selection.canceled || !selection.filePath) {
    return;
  }

  state.exportDirectory = selection.filePath;
  await saveSettings({ lastExportDirectory: selection.filePath });
  pushTimeline("导出目录已选择", selection.filePath, "completed");
  renderExportDirectory();
}

async function startImport() {
  if (!state.importDraft.sessionId || !state.settings.sessionToken) {
    return;
  }

  dom.importProgressCard.classList.remove("hidden");
  dom.importProgressTitle.textContent = "导入已开始";
  dom.importProgressPercent.textContent = "0%";
  dom.importProgressBar.style.width = "0%";
  dom.importProgressMeta.textContent = "正在将图片上传到后端。";

  await window.desktopApi.tasks.startImport({
    baseUrl: state.settings.apiBaseUrl,
    sessionToken: state.settings.sessionToken,
    sessionId: state.importDraft.sessionId,
    mappings: state.importDraft.folders.map((folder) => ({
      folderId: folder.folderId,
      tagName: folder.tagName || folder.defaultTag
    })),
    concurrency: state.settings.importConcurrency
  });
}

async function startExport() {
  const selectedTags = Array.from(state.selectedExportTags);
  const outputDir = state.exportDirectory || state.settings.lastExportDirectory;
  if (!selectedTags.length || !outputDir || !state.settings.sessionToken) {
    return;
  }

  dom.exportProgressCard.classList.remove("hidden");
  dom.exportProgressTitle.textContent = "正在构建导出清单";
  dom.exportProgressPercent.textContent = "0%";
  dom.exportProgressBar.style.width = "0%";
  dom.exportProgressMeta.textContent = "正在获取所选标签的图片数据。";

  const manifest = await request(`${state.settings.apiBaseUrl}/api/image/export/manifest`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${state.settings.sessionToken}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      tags: selectedTags
    })
  });

  await window.desktopApi.tasks.startExport({
    baseUrl: state.settings.apiBaseUrl,
    sessionToken: state.settings.sessionToken,
    outputDir,
    items: manifest.items || [],
    concurrency: state.settings.exportConcurrency
  });
}

function attachTaskEvents() {
  window.desktopApi.events.onTaskProgress((payload) => {
    if (payload.type === "import") {
      dom.importProgressCard.classList.remove("hidden");
      dom.importProgressTitle.textContent = payload.currentLabel || "导入进行中";
      dom.importProgressPercent.textContent = `${payload.percent || 0}%`;
      dom.importProgressBar.style.width = `${payload.percent || 0}%`;
      dom.importProgressMeta.textContent = `已完成 ${payload.completed}/${payload.total}，成功 ${payload.successCount}，失败 ${payload.failedCount}${payload.lastError ? `，最近错误：${payload.lastError}` : ""}`;
    }

    if (payload.type === "export") {
      dom.exportProgressCard.classList.remove("hidden");
      dom.exportProgressTitle.textContent = payload.currentLabel || "导出进行中";
      dom.exportProgressPercent.textContent = `${payload.percent || 0}%`;
      dom.exportProgressBar.style.width = `${payload.percent || 0}%`;
      dom.exportProgressMeta.textContent = `已完成 ${payload.completed}/${payload.total}，已写出 ${payload.writtenCount || 0} 个文件，失败 ${payload.failedCount}${payload.lastError ? `，最近错误：${payload.lastError}` : ""}`;
    }
  });

  window.desktopApi.events.onTaskCompleted(async (payload) => {
    pushTimeline(
      payload.type === "import" ? "导入完成" : "导出完成",
      payload.message || "任务已完成。",
      "completed",
      `成功 ${payload.successCount || 0}，失败 ${payload.failedCount || 0}`
    );

    if (payload.type === "import") {
      await fetchTags();
    }
  });

  window.desktopApi.events.onTaskFailed((payload) => {
    pushTimeline(
      payload.type === "import" ? "导入失败" : "导出失败",
      payload.message || "任务执行失败。",
      "failed"
    );
  });
}

function attachEvents() {
  dom.navLinks.forEach((node) => node.addEventListener("click", () => setPanel(node.dataset.panel)));
  dom.createLoginCard.addEventListener("click", async () => {
    try {
      await createLoginSession();
    } catch (error) {
      pushTimeline("登录会话创建失败", error.message || "无法创建二维码会话。", "failed");
    }
  });
  dom.chooseImportFoldersButton.addEventListener("click", chooseImportFolders);
  dom.chooseExportDirectoryButton.addEventListener("click", chooseExportDirectory);
  dom.startImportButton.addEventListener("click", startImport);
  dom.startExportButton.addEventListener("click", startExport);
  dom.logoutButton.addEventListener("click", async () => {
    stopLoginPolling();
    state.profile = null;
    state.availableTags = [];
    state.selectedExportTags.clear();
    state.login.qrImageUrl = "";
    state.login.token = "";
    state.login.statusMessage = "已退出登录，请重新扫码登录。";
    await saveSettings({ sessionToken: "" });
    renderAll();
    pushTimeline("已退出登录", "桌面端会话令牌已清除。", "completed");
    await ensureLoginSession(true);
  });
}

async function bootstrap() {
  bindDom();
  attachEvents();
  attachTaskEvents();
  state.settings = await window.desktopApi.settings.load();
  state.settings.apiBaseUrl = normalizeBaseUrl(state.settings.apiBaseUrl);
  state.exportDirectory = state.settings.lastExportDirectory || "";
  renderAll();
  await fetchProfile();
  await fetchTags();
  await ensureLoginSession();
}

bootstrap().catch((error) => {
  console.error(error);
});
