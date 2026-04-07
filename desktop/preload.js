const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("desktopApi", {
  settings: {
    load: () => ipcRenderer.invoke("settings:load"),
    save: (payload) => ipcRenderer.invoke("settings:save", payload)
  },
  dialog: {
    selectImportFolders: () => ipcRenderer.invoke("dialog:selectImportFolders"),
    selectExportDirectory: () => ipcRenderer.invoke("dialog:selectExportDirectory")
  },
  tasks: {
    prepareImportFolders: (payload) => ipcRenderer.invoke("tasks:prepareImportFolders", payload),
    startImport: (payload) => ipcRenderer.invoke("tasks:startImport", payload),
    startExport: (payload) => ipcRenderer.invoke("tasks:startExport", payload)
  },
  http: {
    request: (payload) => ipcRenderer.invoke("http:request", payload)
  },
  shell: {
    openPath: (payload) => ipcRenderer.invoke("shell:openPath", payload),
    openExternal: (payload) => ipcRenderer.invoke("shell:openExternal", payload)
  },
  app: {
    getInfo: () => ipcRenderer.invoke("app:getInfo")
  },
  events: {
    onTaskProgress: (listener) => {
      ipcRenderer.on("task:progress", (_, payload) => listener(payload));
    },
    onTaskCompleted: (listener) => {
      ipcRenderer.on("task:completed", (_, payload) => listener(payload));
    },
    onTaskFailed: (listener) => {
      ipcRenderer.on("task:failed", (_, payload) => listener(payload));
    }
  }
});
