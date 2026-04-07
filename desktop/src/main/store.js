const fs = require("fs/promises");
const path = require("path");

function getDefaultApiBaseUrl() {
  const candidates = [
    process.env.DESKTOP_API_BASE_URL,
    process.env.IMAGE_COLLECTION_API_BASE_URL,
    process.env.APP_DESKTOP_API_BASE_URL,
    "https://api.yuntuxia.top"
  ];
  const matched = candidates
    .map((item) => String(item || "").trim())
    .find(Boolean);
  return matched ? matched.replace(/\/+$/, "") : "https://api.yuntuxia.top";
}

const DEFAULT_SETTINGS = {
  apiBaseUrl: getDefaultApiBaseUrl(),
  sessionToken: "",
  importConcurrency: 3,
  exportConcurrency: 3,
  lastExportDirectory: "",
  lastImportDirectories: []
};

function resolveSettingsPath(app) {
  return path.join(app.getPath("userData"), "desktop-settings.json");
}

async function loadSettings(app) {
  const filePath = resolveSettingsPath(app);
  try {
    const content = await fs.readFile(filePath, "utf8");
    return { ...DEFAULT_SETTINGS, ...JSON.parse(content) };
  } catch (error) {
    return { ...DEFAULT_SETTINGS };
  }
}

async function saveSettings(app, patch) {
  const current = await loadSettings(app);
  const next = { ...current, ...patch };
  const filePath = resolveSettingsPath(app);
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, JSON.stringify(next, null, 2), "utf8");
  return next;
}

module.exports = {
  DEFAULT_SETTINGS,
  getDefaultApiBaseUrl,
  loadSettings,
  saveSettings
};
