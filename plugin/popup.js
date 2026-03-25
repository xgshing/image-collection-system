const API_BASE_URL_KEY = "apiBaseUrl";
const DEFAULT_BASE_URL = "http://139.199.19.101";
let baseUrl = DEFAULT_BASE_URL;
const defaultAvatar = "data:image/svg+xml;utf8," + encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
  <rect width="160" height="160" rx="80" fill="#E8F0FF"/>
  <circle cx="80" cy="60" r="28" fill="#8BA7E8"/>
  <path d="M38 128c9-24 28-36 42-36s33 12 42 36" fill="#8BA7E8"/>
</svg>
`);

let imageUrl = "";
let pollingTimer = null;
let currentToken = "";
let currentSessionToken = "";

const loginSection = document.getElementById("loginSection");
const collectSection = document.getElementById("collectSection");
const qrImage = document.getElementById("qrImage");
const loginStatus = document.getElementById("loginStatus");
const refreshLoginBtn = document.getElementById("refreshLoginBtn");
const avatarPreview = document.getElementById("avatarPreview");
const nicknameDisplay = document.getElementById("nicknameDisplay");
const preview = document.getElementById("preview");
const tagInput = document.getElementById("tagInput");
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

const stopPolling = () => {
    if (pollingTimer) {
        clearInterval(pollingTimer);
        pollingTimer = null;
    }
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
    avatarPreview.src = profile.avatarUrl || defaultAvatar;
    avatarPreview.onerror = () => {
        avatarPreview.src = defaultAvatar;
    };
    nicknameDisplay.textContent = profile.nickName || "微信用户";
};

const updatePreviewImage = () => {
    preview.src = imageUrl || defaultAvatar;
    preview.onerror = () => {
        preview.src = defaultAvatar;
    };
};

const fetchWithAuth = (url, options = {}) => {
    const headers = { ...(options.headers || {}) };
    if (currentSessionToken) {
        headers.Authorization = "Bearer " + currentSessionToken;
    }

    return fetch(url, {
        ...options,
        headers
    });
};

const createLoginSession = async () => {
    stopPolling();
    showLoginSection();
    loginStatus.textContent = "正在生成二维码...";
    qrImage.src = defaultAvatar;

    const response = await fetch(baseUrl + "/api/plugin/login/session", {
        method: "POST"
    });
    if (!response.ok) {
        throw new Error("create session failed");
    }

    const session = await response.json();
    currentToken = session.token;
    qrImage.src = baseUrl + "/api/plugin/login/session/" + currentToken + "/minicode";
    qrImage.onerror = () => {
        loginStatus.textContent = "小程序码加载失败，请确认已配置 AppSecret 并可访问后端";
        qrImage.src = defaultAvatar;
    };
    loginStatus.textContent = "请用微信扫一扫识别小程序码，打开小程序后完成登录确认";

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
        return;
    }

    if (result.status === "expired") {
        stopPolling();
        loginStatus.textContent = "二维码已失效，请点击刷新重新生成";
    }
};

const saveImage = async () => {
    const storedAuth = await getStoredProfile();
    if (!storedAuth || !storedAuth.sessionToken) {
        alert("请先完成扫码登录");
        return;
    }

    const tags = tagInput.value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean)
        .join(",");

    currentSessionToken = storedAuth.sessionToken;
    const response = await fetchWithAuth(baseUrl + "/api/image/saveByUrl", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            url: imageUrl,
            tags
        })
    });

    if (!response.ok) {
        throw new Error("save image failed");
    }

    alert("收藏成功");
};

const initialize = async () => {
    chrome.storage.local.get(["imageUrl", API_BASE_URL_KEY], async (res) => {
        baseUrl = normalizeBaseUrl(res[API_BASE_URL_KEY]);
        imageUrl = res.imageUrl || "";
        updatePreviewImage();

        const storedAuth = await getStoredProfile();
        if (storedAuth?.profile && storedAuth?.sessionToken) {
            currentSessionToken = storedAuth.sessionToken;
            showCollectSection(storedAuth.profile);
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
        loginStatus.textContent = "二维码生成失败，请重试";
    }
});

logoutBtn.addEventListener("click", async () => {
    await clearStoredProfile();
    currentToken = "";
    currentSessionToken = "";
    tagInput.value = "";
    await createLoginSession();
});

saveBtn.addEventListener("click", async () => {
    try {
        await saveImage();
    } catch (error) {
        console.error(error);
        alert("收藏失败，请重试");
    }
});

initialize().catch((error) => {
    console.error(error);
    showLoginSection();
    loginStatus.textContent = "初始化失败，请重试";
});
