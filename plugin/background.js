chrome.runtime.onInstalled.addListener(() => {
    chrome.contextMenus.create({
        id: "saveImage",
        title: "收藏图片",
        contexts: ["image"]
    });
});

chrome.contextMenus.onClicked.addListener((info) => {
    if (info.menuItemId === "saveImage") {
        chrome.storage.local.set({ imageUrl: info.srcUrl });
        chrome.action.openPopup();
    }
});