const { dialog, ipcMain, shell } = require("electron");
const { loadSettings, saveSettings } = require("./store");
const { prepareImportFolders, startImportTask } = require("./import-manager");
const { startExportTask } = require("./export-manager");

function sendToWindow(getMainWindow, channel, payload) {
  const mainWindow = getMainWindow();
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
  }
}

function registerIpc({ app, getMainWindow }) {
  ipcMain.handle("app:getInfo", async () => ({
    name: app.getName(),
    version: app.getVersion()
  }));

  ipcMain.handle("settings:load", async () => loadSettings(app));
  ipcMain.handle("settings:save", async (_, payload) => saveSettings(app, payload || {}));

  ipcMain.handle("dialog:selectImportFolders", async () => {
    const result = await dialog.showOpenDialog({
      title: "Select image folders",
      properties: ["openDirectory", "multiSelections"]
    });
    return {
      canceled: result.canceled,
      filePaths: result.filePaths || []
    };
  });

  ipcMain.handle("dialog:selectExportDirectory", async () => {
    const result = await dialog.showOpenDialog({
      title: "Select export directory",
      properties: ["openDirectory", "createDirectory"]
    });
    return {
      canceled: result.canceled,
      filePath: result.filePaths?.[0] || ""
    };
  });

  ipcMain.handle("tasks:prepareImportFolders", async (_, payload) =>
    prepareImportFolders(Array.isArray(payload?.folders) ? payload.folders : [])
  );

  ipcMain.handle("tasks:startImport", async (_, payload) =>
    startImportTask({
      ...(payload || {}),
      sendProgress: (eventPayload) => sendToWindow(getMainWindow, "task:progress", eventPayload),
      sendCompleted: (eventPayload) => sendToWindow(getMainWindow, "task:completed", eventPayload),
      sendFailed: (eventPayload) => sendToWindow(getMainWindow, "task:failed", eventPayload)
    })
  );

  ipcMain.handle("tasks:startExport", async (_, payload) =>
    startExportTask({
      ...(payload || {}),
      sendProgress: (eventPayload) => sendToWindow(getMainWindow, "task:progress", eventPayload),
      sendCompleted: (eventPayload) => sendToWindow(getMainWindow, "task:completed", eventPayload),
      sendFailed: (eventPayload) => sendToWindow(getMainWindow, "task:failed", eventPayload)
    })
  );

  ipcMain.handle("http:request", async (_, payload) => {
    try {
      const response = await fetch(String(payload?.url || ""), {
        method: String(payload?.method || "GET"),
        headers: payload?.headers || {},
        body: payload?.body
      });
      const text = await response.text();
      return {
        ok: response.ok,
        status: response.status,
        text
      };
    } catch (error) {
      const message = error?.cause?.code === "ECONNREFUSED"
        ? `后端无法访问：${error.cause.address}:${error.cause.port}`
        : `后端无法访问：${error.message || "网络请求失败"}`;
      return {
        ok: false,
        status: 0,
        text: JSON.stringify({ message })
      };
    }
  });

  ipcMain.handle("shell:openPath", async (_, payload) => {
    const result = await shell.openPath(String(payload?.path || "").trim());
    return { ok: !result, message: result || "" };
  });

  ipcMain.handle("shell:openExternal", async (_, payload) => {
    await shell.openExternal(String(payload?.url || "").trim());
    return { ok: true };
  });
}

module.exports = {
  registerIpc
};
