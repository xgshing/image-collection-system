const path = require("path");
const { app, BrowserWindow } = require("electron");
const { registerIpc } = require("./src/main/ipc");

let mainWindow;

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 1240,
    minHeight: 800,
    icon: path.join(__dirname, "assets", "icon.png"),
    backgroundColor: "#f4f7fb",
    title: "Image Collection Desktop",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, "src", "renderer", "index.html"));

  mainWindow.webContents.setWindowOpenHandler(({ url }) => ({
    action: url.startsWith("http://") || url.startsWith("https://") ? "allow" : "deny"
  }));

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  registerIpc({
    app,
    getMainWindow: () => mainWindow
  });
  createMainWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createMainWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
