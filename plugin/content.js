if (!globalThis.__yuntuxiaCollector) {
globalThis.__yuntuxiaCollector = true;

const MIN_IMAGE_EDGE = 48;

const toAbsoluteUrl = (value) => {
    const raw = (value || "").trim();
    if (!raw || raw.startsWith("data:") || raw.startsWith("blob:")) {
        return "";
    }
    try {
        const url = new URL(raw, location.href);
        if (url.protocol !== "http:" && url.protocol !== "https:") {
            return "";
        }
        return url.href;
    } catch (error) {
        return "";
    }
};

const urlsFromCss = (value) => {
    if (!value || value === "none") {
        return [];
    }
    return [...value.matchAll(/url\(\s*(['"]?)(.*?)\1\s*\)/g)]
        .map((match) => toAbsoluteUrl(match[2]))
        .filter(Boolean);
};

const elementArea = (element) => {
    const rect = element.getBoundingClientRect();
    return Math.max(0, rect.width) * Math.max(0, rect.height);
};

const imageFromElement = (element) => {
    if (!element || element.nodeType !== 1) {
        return "";
    }
    const rect = element.getBoundingClientRect();
    if (rect.width < MIN_IMAGE_EDGE || rect.height < MIN_IMAGE_EDGE) {
        return "";
    }

    if (element.tagName === "IMG") {
        return toAbsoluteUrl(element.currentSrc || element.src);
    }
    if (element.tagName === "IMAGE") {
        return toAbsoluteUrl(element.href?.baseVal || element.getAttribute("href") || "");
    }

    const backgroundUrls = urlsFromCss(getComputedStyle(element).backgroundImage);
    return backgroundUrls[0] || "";
};

const largestImageWithin = (root) => {
    if (!root || root.nodeType !== 1) {
        return "";
    }

    let bestUrl = imageFromElement(root);
    let bestArea = bestUrl ? elementArea(root) : 0;
    root.querySelectorAll("img, image").forEach((element) => {
        const url = imageFromElement(element);
        const area = elementArea(element);
        if (url && area > bestArea) {
            bestUrl = url;
            bestArea = area;
        }
    });
    return bestUrl;
};

const findImageUrl = (event) => {
    const stack = document.elementsFromPoint(event.clientX, event.clientY);
    let bestUrl = "";
    let bestArea = 0;
    stack.forEach((element) => {
        const url = imageFromElement(element);
        const area = elementArea(element);
        if (url && area >= bestArea) {
            bestUrl = url;
            bestArea = area;
        }
    });
    if (bestUrl) {
        return bestUrl;
    }

    let node = event.target instanceof Element ? event.target : event.target?.parentElement;
    for (let depth = 0; node && node !== document.body && depth < 6; depth += 1) {
        const url = largestImageWithin(node);
        if (url) {
            return url;
        }
        node = node.parentElement;
    }
    return "";
};

const host = document.createElement("div");
host.style.position = "fixed";
host.style.zIndex = "2147483647";
host.style.left = "0";
host.style.right = "0";
host.style.bottom = "24px";
host.style.display = "none";
host.style.justifyContent = "center";
host.style.pointerEvents = "none";
const shadow = host.attachShadow({ mode: "open" });
shadow.innerHTML = `
<style>
  .bar {
    display: flex;
    align-items: center;
    gap: 12px;
    max-width: calc(100vw - 32px);
    padding: 10px 12px 10px 16px;
    border-radius: 999px;
    background: #ffffff;
    color: #1f2329;
    box-shadow: 0 12px 32px rgba(15, 23, 42, 0.18);
    font: 14px/1.4 "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
    pointer-events: auto;
  }
  button {
    border: 0;
    border-radius: 999px;
    padding: 8px 14px;
    background: #7965d8;
    color: #ffffff;
    font: inherit;
    cursor: pointer;
  }
  .close {
    width: 28px;
    height: 28px;
    padding: 0;
    background: transparent;
    color: #667085;
  }
</style>
<div class="bar">
  <span class="text">已识别图片</span>
  <button type="button" class="save">收藏到云图匣</button>
  <button type="button" class="close" aria-label="关闭">×</button>
</div>
`;
const text = shadow.querySelector(".text");
const saveButton = shadow.querySelector(".save");
let currentImageUrl = "";
let hideTimer = 0;

const showCollector = (imageUrl) => {
    currentImageUrl = imageUrl;
    text.textContent = "已识别图片";
    saveButton.disabled = false;
    saveButton.textContent = "收藏到云图匣";
    host.style.display = "flex";
    if (document.documentElement) {
        document.documentElement.appendChild(host);
    }
    clearTimeout(hideTimer);
    hideTimer = setTimeout(hideCollector, 8000);
};

const hideCollector = () => {
    clearTimeout(hideTimer);
    host.style.display = "none";
    currentImageUrl = "";
};

const rememberImage = (imageUrl, onDone) => {
    chrome.storage.local.set({ imageUrl, pageUrl: location.href }, () => {
        if (chrome.runtime.lastError) {
            onDone({ ok: false, opened: false });
            return;
        }
        chrome.runtime.sendMessage({
            type: "collect-image",
            imageUrl,
            pageUrl: location.href
        }, (response) => {
            onDone({
                ok: !chrome.runtime.lastError,
                opened: Boolean(response?.opened)
            });
        });
    });
};

const finishCollector = (result) => {
    if (!result.ok) {
        text.textContent = "收藏失败，请稍后重试";
        saveButton.disabled = false;
        return;
    }
    text.textContent = result.opened ? "已打开云图匣" : "已放入云图匣，请点击工具栏图标";
    saveButton.textContent = "完成";
    clearTimeout(hideTimer);
    hideTimer = setTimeout(hideCollector, 4000);
};

saveButton.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (!currentImageUrl) {
        return;
    }
    saveButton.disabled = true;
    rememberImage(currentImageUrl, finishCollector);
});

shadow.querySelector(".close").addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    hideCollector();
});

const SITE_MENU_PATTERN = /copy png|download png|copy image|download image|复制图片|下载图片/i;

const scanMenus = (root, found) => {
    if (!root?.querySelectorAll) {
        return;
    }
    root.querySelectorAll("div, ul, nav, section, menu").forEach((element) => {
        if (element.querySelector("[data-yuntuxia-menu]")) {
            return;
        }
        const label = element.innerText || "";
        if (label.length > 500 || !SITE_MENU_PATTERN.test(label)) {
            return;
        }
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        if (rect.width < 100 || rect.width > 360 || rect.height < 60 || rect.height > 420) {
            return;
        }
        if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity) === 0) {
            return;
        }
        found.push(element);
        if (element.shadowRoot) {
            scanMenus(element.shadowRoot, found);
        }
    });
};

const attachToSiteMenu = (imageUrl) => {
    if (document.querySelector("[data-yuntuxia-menu]")) {
        return true;
    }
    const found = [];
    scanMenus(document, found);
    if (!found.length) {
        return false;
    }
    found.sort((left, right) => elementArea(left) - elementArea(right));
    const menu = found[0];
    const row = document.createElement("button");
    row.type = "button";
    row.dataset.yuntuxiaMenu = "1";
    row.textContent = "收藏到云图匣";
    row.style.cssText = [
        "display:flex",
        "width:100%",
        "box-sizing:border-box",
        "align-items:center",
        "padding:10px 14px",
        "border:0",
        "border-top:1px solid rgba(127,127,127,.35)",
        "background:transparent",
        "color:inherit",
        "font:inherit",
        "cursor:pointer",
        "text-align:left"
    ].join(";");
    const keepMenuOpen = (event) => {
        event.stopPropagation();
    };
    row.addEventListener("pointerdown", keepMenuOpen, true);
    row.addEventListener("mousedown", keepMenuOpen, true);
    row.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        row.disabled = true;
        rememberImage(imageUrl, (result) => {
            row.textContent = result.ok ? "已放入云图匣" : "收藏失败";
        });
    }, true);
    menu.appendChild(row);
    return true;
};

const watchSiteMenu = (imageUrl) => {
    let attempts = 0;
    const timer = setInterval(() => {
        attempts += 1;
        attachToSiteMenu(imageUrl);
        if (attempts >= 12) {
            clearInterval(timer);
        }
    }, 50);
};

document.addEventListener("contextmenu", (event) => {
    const imageUrl = findImageUrl(event);
    chrome.runtime.sendMessage({
        type: "context-image",
        imageUrl,
        pageUrl: location.href
    });
    if (!imageUrl) {
        return;
    }
    showCollector(imageUrl);
    watchSiteMenu(imageUrl);
}, true);
}
