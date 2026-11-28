const MENU_IDS = new Set(["saveCoverImage", "yuntuxiaSaveImage"]);
const lastTargetByTab = new Map();

const installMenu = () => {
    chrome.contextMenus.create({
        id: "saveCoverImage",
        title: "收藏图片",
        contexts: ["all"]
    }, () => {
        void chrome.runtime.lastError;
    });
};

chrome.runtime.onInstalled.addListener(() => {
    installMenu();
    chrome.tabs.query({ url: ["http://*/*", "https://*/*"] }, (tabs) => {
        tabs.forEach((tab) => {
            if (tab.id == null) {
                return;
            }
            chrome.scripting.executeScript({
                target: { tabId: tab.id, allFrames: true },
                files: ["content.js"]
            }).catch(() => {});
        });
    });
});

chrome.runtime.onStartup.addListener(installMenu);
installMenu();

const storeImage = (imageUrl, pageUrl, sendResponse) => {
    if (!imageUrl) {
        sendResponse({ ok: false });
        return;
    }
    chrome.storage.local.set({ imageUrl, pageUrl }, () => {
        chrome.action.openPopup()
            .then(() => sendResponse({ ok: true, opened: true }))
            .catch(() => sendResponse({ ok: true, opened: false }));
    });
};

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message?.type === "context-image" && sender.tab?.id != null) {
        lastTargetByTab.set(sender.tab.id, {
            imageUrl: message.imageUrl || "",
            pageUrl: message.pageUrl || ""
        });
        return;
    }

    if (message?.type === "collect-image") {
        storeImage(message.imageUrl || "", message.pageUrl || "", sendResponse);
        return true;
    }
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
    if (!MENU_IDS.has(info.menuItemId)) {
        return;
    }

    const stored = tab?.id == null ? null : lastTargetByTab.get(tab.id);
    const imageUrl = info.srcUrl || stored?.imageUrl || "";
    const pageUrl = info.pageUrl || stored?.pageUrl || tab?.url || "";
    if (!imageUrl) {
        return;
    }

    chrome.storage.local.set({ imageUrl, pageUrl });
    chrome.action.openPopup().catch((error) => {
        console.warn("openPopup failed", error);
    });
});
