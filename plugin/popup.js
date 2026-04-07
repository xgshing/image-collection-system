const API_BASE_URL_KEY = "apiBaseUrl";
const DEFAULT_BASE_URL = "https://api.yuntuxia.top";
const defaultAvatar = "data:image/svg+xml;utf8," + encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
  <rect width="160" height="160" rx="80" fill="#E8F0FF"/>
  <circle cx="80" cy="60" r="28" fill="#8BA7E8"/>
  <path d="M38 128c9-24 28-36 42-36s33 12 42 36" fill="#8BA7E8"/>
</svg>
`);

let baseUrl = DEFAULT_BASE_URL;
let imageUrl = "";
let pageUrl = "";
let pollingTimer = null;
let currentToken = "";
let currentSessionToken = "";
let selectedTags = [];
let availableTags = [];
let isSaving = false;

const loginSection = document.getElementById("loginSection");
const collectSection = document.getElementById("collectSection");
const qrImage = document.getElementById("qrImage");
const loginStatus = document.getElementById("loginStatus");
const refreshLoginBtn = document.getElementById("refreshLoginBtn");
const avatarPreview = document.getElementById("avatarPreview");
const nicknameDisplay = document.getElementById("nicknameDisplay");
const preview = document.getElementById("preview");
const previewPlaceholder = document.getElementById("previewPlaceholder");
const previewPlaceholderText = document.getElementById("previewPlaceholderText");
const emptyState = document.getElementById("emptyState");
const tagInput = document.getElementById("tagInput");
const tagSuggestions = document.getElementById("tagSuggestions");
const selectedTagsWrap = document.getElementById("selectedTags");
const saveFeedback = document.getElementById("saveFeedback");
const saveBtn = document.getElementById("saveBtn");
const logoutBtn = document.getElementById("logoutBtn");

const getStoredProfile = () => new Promise((resolve) => {
    chrome.storage.local.get(["userProfile", "sessionToken"], (res) => {
        if (!res.userProfile || !res.sessionToken) {
            resolve(null);
            return;
        }
        resolve({
            profile: res.userProfile,
            sessionToken: res.sessionToken
        });
    });
});

const setStoredProfile = (profile, sessionToken) => new Promise((resolve) => {
    chrome.storage.local.set({ userProfile: profile, sessionToken }, resolve);
});

const clearStoredProfile = () => new Promise((resolve) => {
    chrome.storage.local.remove(["userProfile", "sessionToken"], resolve);
});

const normalizeBaseUrl = (value) => {
    const raw = (value || "").trim();
    if (!raw) {
        return DEFAULT_BASE_URL;
    }
    return raw.endsWith("/") ? raw.slice(0, -1) : raw;
};

const normalizeImageUrl = (value) => {
    const raw = (value || "").trim();
    if (!raw) {
        return "";
    }
    if (raw.startsWith("//")) {
        return `https:${raw}`;
    }
    return raw;
};

const normalizeAvatarUrl = (value) => {
    const raw = (value || "").trim();
    if (!raw || !/^https?:\/\//i.test(raw)) {
        return "";
    }
    return raw;
};

const stopPolling = () => {
    if (pollingTimer) {
        clearInterval(pollingTimer);
        pollingTimer = null;
    }
};

const setFeedback = (message, type = "") => {
    saveFeedback.textContent = message || "";
    saveFeedback.className = "save-feedback" + (type ? ` ${type}` : "");
};

const clearComposer = () => {
    selectedTags = [];
    tagInput.value = "";
    renderSelectedTags();
    renderSuggestions();
};

const mergeAvailableTags = (tags = []) => {
    availableTags = [...new Set([...availableTags, ...tags.filter(Boolean)])].sort((a, b) => a.localeCompare(b, "zh-CN"));
    renderSuggestions();
};

const setPreviewBlocked = (message) => {
    preview.style.display = "none";
    previewPlaceholder.style.display = "flex";
    previewPlaceholderText.textContent = message;
};

const updatePreviewImage = () => {
    const hasImage = Boolean(imageUrl);
    emptyState.style.display = hasImage ? "none" : "block";
    previewPlaceholder.style.display = "none";
    previewPlaceholderText.textContent = "";

    if (!hasImage) {
        preview.style.display = "none";
        preview.removeAttribute("src");
        return;
    }

    preview.style.display = "block";
    preview.src = imageUrl;
    preview.onerror = () => {
        setPreviewBlocked("该网站限制了图片预览，但仍可尝试收藏。");
        setFeedback("该网站限制了原图预览，但仍可尝试收藏。", "info");
    };
};

const updateSaveButton = () => {
    saveBtn.disabled = isSaving || !imageUrl;
    if (isSaving) {
        saveBtn.textContent = "保存中...";
        return;
    }
    saveBtn.textContent = imageUrl ? "收藏图片" : "请先选择图片";
};

const showLoginSection = () => {
    loginSection.style.display = "block";
    collectSection.style.display = "none";
    avatarPreview.src = defaultAvatar;
    nicknameDisplay.textContent = "微信用户";
};

const showCollectSection = (profile) => {
    stopPolling();
    loginSection.style.display = "none";
    collectSection.style.display = "block";
    avatarPreview.onerror = () => {
        avatarPreview.src = defaultAvatar;
    };
    avatarPreview.src = normalizeAvatarUrl(profile.avatarUrl) || defaultAvatar;
    nicknameDisplay.textContent = profile.nickName || "微信用户";
    updatePreviewImage();
    updateSaveButton();
};

const fetchWithAuth = (url, options = {}) => {
    const headers = { ...(options.headers || {}) };
    if (currentSessionToken) {
        headers.Authorization = "Bearer " + currentSessionToken;
    }
    return fetch(url, { ...options, headers });
};

const renderSelectedTags = () => {
    selectedTagsWrap.innerHTML = "";
    selectedTags.forEach((tag) => {
        const item = document.createElement("div");
        item.className = "selected-tag";
        item.textContent = tag;

        const removeBtn = document.createElement("button");
        removeBtn.type = "button";
        removeBtn.textContent = "x";
        removeBtn.addEventListener("click", () => {
            selectedTags = selectedTags.filter((current) => current !== tag);
            renderSelectedTags();
            renderSuggestions();
        });

        item.appendChild(removeBtn);
        selectedTagsWrap.appendChild(item);
    });
};

const getFilteredSuggestions = () => {
    const keyword = tagInput.value.trim().toLowerCase();
    return availableTags
        .filter((tag) => !selectedTags.includes(tag))
        .filter((tag) => !keyword || tag.toLowerCase().includes(keyword))
        .slice(0, 8);
};

const renderSuggestions = () => {
    const suggestions = getFilteredSuggestions();
    tagSuggestions.innerHTML = "";

    if (!suggestions.length || document.activeElement !== tagInput) {
        tagSuggestions.style.display = "none";
        return;
    }

    suggestions.forEach((tag) => {
        const item = document.createElement("div");
        item.className = "tag-suggestion-item";
        item.textContent = tag;
        item.addEventListener("mousedown", (event) => {
            event.preventDefault();
            addTag(tag);
        });
        tagSuggestions.appendChild(item);
    });

    tagSuggestions.style.display = "block";
};

const addTag = (value) => {
    const tag = (value || "").trim();
    if (!tag || selectedTags.includes(tag)) {
        tagInput.value = "";
        renderSuggestions();
        return;
    }

    selectedTags = [...selectedTags, tag];
    mergeAvailableTags([tag]);
    tagInput.value = "";
    renderSelectedTags();
    renderSuggestions();
};

const loadAvailableTags = async () => {
    if (!currentSessionToken) {
        availableTags = [];
        renderSuggestions();
        return;
    }

    try {
        const response = await fetchWithAuth(baseUrl + "/api/image/tags");
        if (!response.ok) {
            return;
        }
        const result = await response.json();
        availableTags = Array.isArray(result) ? result.slice().sort((a, b) => a.localeCompare(b, "zh-CN")) : [];
        renderSuggestions();
    } catch (error) {
        console.error("loadAvailableTags failed", error);
    }
};

const refreshProfile = async () => {
    if (!currentSessionToken) {
        return null;
    }

    try {
        const response = await fetchWithAuth(baseUrl + "/api/auth/me");
        if (!response.ok) {
            return null;
        }
        const profile = await response.json();
        const normalized = {
            userId: profile.userId,
            nickName: profile.nickName || "微信用户",
            avatarUrl: normalizeAvatarUrl(profile.avatarUrl)
        };
        await setStoredProfile(normalized, currentSessionToken);
        showCollectSection(normalized);
        return normalized;
    } catch (error) {
        console.error("refreshProfile failed", error);
        return null;
    }
};

const createLoginSession = async () => {
    stopPolling();
    showLoginSection();
    loginStatus.textContent = "正在生成二维码...";
    qrImage.src = defaultAvatar;

    const response = await fetch(baseUrl + "/api/plugin/login/session", { method: "POST" });
    if (!response.ok) {
        throw new Error("create session failed");
    }

    const session = await response.json();
    currentToken = session.token;
    qrImage.src = baseUrl + "/api/plugin/login/session/" + currentToken + "/minicode";
    qrImage.onerror = () => {
        loginStatus.textContent = "小程序码加载失败。";
        qrImage.src = defaultAvatar;
    };
    loginStatus.textContent = "请用微信扫码，并在小程序中确认登录。";

    pollingTimer = setInterval(checkLoginStatus, 2000);
};

const checkLoginStatus = async () => {
    if (!currentToken) {
        return;
    }

    const response = await fetch(baseUrl + "/api/plugin/login/session/" + currentToken);
    if (!response.ok) {
        throw new Error("check session failed");
    }

    const result = await response.json();
    if (result.status === "confirmed" && result.profile && result.sessionToken) {
        currentSessionToken = result.sessionToken;
        await setStoredProfile(result.profile, result.sessionToken);
        showCollectSection(result.profile);
        await refreshProfile();
        await loadAvailableTags();
        setFeedback("登录成功，现在可以开始收藏图片。", "success");
        return;
    }

    if (result.status === "expired") {
        stopPolling();
        loginStatus.textContent = "二维码已过期，请点击刷新。";
    }
};

const saveImage = async () => {
    const storedAuth = await getStoredProfile();
    if (!storedAuth || !storedAuth.sessionToken) {
        alert("请先登录。");
        return;
    }

    if (!imageUrl) {
        setFeedback("当前没有待收藏图片，请先在网页图片上点击右键。", "info");
        return;
    }

    const pendingTag = tagInput.value.trim();
    if (pendingTag) {
        addTag(pendingTag);
    }
    const requestedTags = [...selectedTags];

    currentSessionToken = storedAuth.sessionToken;
    isSaving = true;
    updateSaveButton();
    setFeedback("正在保存图片...", "info");

    const response = await fetchWithAuth(baseUrl + "/api/image/saveByUrl", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            url: imageUrl,
            tags: requestedTags.join(","),
            referer: pageUrl
        })
    });

    if (!response.ok) {
        let message = "保存失败，请稍后再试。";
        try {
            const payload = await response.json();
            if (payload?.message === "download image failed") {
                message = "该网站限制了原图下载，当前无法直接收藏。";
            } else if (payload?.message) {
                message = payload.message;
            }
        } catch (error) {
            console.error("parse error response failed", error);
        }
        throw new Error(message);
    }

    const result = await response.json();
    const resultTags = Array.isArray(result.tags) ? result.tags : [];
    const mergedNewTags = requestedTags.filter((tag) => resultTags.includes(tag));
    mergeAvailableTags([...resultTags, ...requestedTags]);
    await loadAvailableTags();

    let successMessage = "收藏成功，小程序首页会自动刷新。";
    if (result.status === "exists") {
        successMessage = mergedNewTags.length
            ? "图片已存在，本次新增标签已自动合并。"
            : "图片已存在。";
    }

    setFeedback(successMessage, "success");
    clearComposer();
    imageUrl = "";
    pageUrl = "";
    chrome.storage.local.remove(["imageUrl", "pageUrl"]);
    updatePreviewImage();
    updateSaveButton();
};

const initialize = async () => {
    chrome.storage.local.get(["imageUrl", "pageUrl", API_BASE_URL_KEY], async (res) => {
        baseUrl = normalizeBaseUrl(res[API_BASE_URL_KEY]);
        imageUrl = normalizeImageUrl(res.imageUrl);
        pageUrl = (res.pageUrl || "").trim();
        updatePreviewImage();
        updateSaveButton();

        const storedAuth = await getStoredProfile();
        if (storedAuth?.profile && storedAuth?.sessionToken) {
            currentSessionToken = storedAuth.sessionToken;
            showCollectSection(storedAuth.profile);
            await refreshProfile();
            await loadAvailableTags();
            return;
        }

        await createLoginSession();
    });
};

refreshLoginBtn.addEventListener("click", async () => {
    try {
        await createLoginSession();
    } catch (error) {
        console.error(error);
        loginStatus.textContent = "二维码生成失败。";
    }
});

logoutBtn.addEventListener("click", async () => {
    await clearStoredProfile();
    currentToken = "";
    currentSessionToken = "";
    availableTags = [];
    clearComposer();
    setFeedback("");
    await createLoginSession();
});

saveBtn.addEventListener("click", async () => {
    try {
        await saveImage();
    } catch (error) {
        console.error(error);
        setFeedback(error.message || "保存失败，请稍后再试。");
    } finally {
        isSaving = false;
        updateSaveButton();
    }
});

tagInput.addEventListener("input", () => {
    const raw = tagInput.value || "";
    if (raw.endsWith(",") || raw.endsWith("\uFF0C")) {
        addTag(raw.slice(0, -1));
        return;
    }
    renderSuggestions();
});

tagInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        event.preventDefault();
        addTag(tagInput.value);
    }
    if (event.key === "Backspace" && !tagInput.value && selectedTags.length) {
        selectedTags = selectedTags.slice(0, -1);
        renderSelectedTags();
        renderSuggestions();
    }
});

tagInput.addEventListener("focus", () => {
    renderSuggestions();
});

tagInput.addEventListener("blur", () => {
    setTimeout(() => {
        tagSuggestions.style.display = "none";
    }, 120);
});

chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName !== "local") {
        return;
    }

    if (changes.imageUrl) {
        imageUrl = normalizeImageUrl(changes.imageUrl.newValue);
        updatePreviewImage();
        updateSaveButton();
    }
    if (changes.pageUrl) {
        pageUrl = (changes.pageUrl.newValue || "").trim();
    }
    if (imageUrl) {
        setFeedback("待收藏图片已更新。", "info");
    }
});

initialize().catch((error) => {
    console.error(error);
    showLoginSection();
    loginStatus.textContent = "初始化失败。";
});
