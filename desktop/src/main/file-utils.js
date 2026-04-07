const fs = require("fs/promises");
const path = require("path");

const IMAGE_EXTENSIONS = new Set([".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"]);

function isSupportedImage(filePath) {
  return IMAGE_EXTENSIONS.has(path.extname(filePath).toLowerCase());
}

async function listImageFilesRecursive(rootPath) {
  const results = [];
  const stack = [rootPath];

  while (stack.length) {
    const current = stack.pop();
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile() && isSupportedImage(fullPath)) {
        results.push(fullPath);
      }
    }
  }

  results.sort((left, right) => left.localeCompare(right, "zh-CN"));
  return results;
}

function sanitizeFolderName(value) {
  const input = String(value || "").trim();
  return input.replace(/[<>:"/\\|?*\x00-\x1f]/g, "_").replace(/\s+/g, " ") || "Untitled";
}

function sanitizeFileStem(value) {
  const input = String(value || "").trim();
  return input.replace(/[<>:"/\\|?*\x00-\x1f]/g, "_").replace(/\s+/g, "-") || "image";
}

function resolveRemoteUrl(baseUrl, value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "";
  }
  if (/^https?:\/\//i.test(raw)) {
    return raw;
  }
  return `${String(baseUrl || "").trim().replace(/\/+$/, "")}${raw.startsWith("/") ? raw : `/${raw}`}`;
}

function inferExtension(url, contentType) {
  const pathname = (() => {
    try {
      return new URL(url).pathname || "";
    } catch (error) {
      return "";
    }
  })();
  const extension = path.extname(pathname).toLowerCase();
  if (IMAGE_EXTENSIONS.has(extension)) {
    return extension;
  }
  const normalizedType = String(contentType || "").toLowerCase();
  if (normalizedType.includes("png")) return ".png";
  if (normalizedType.includes("gif")) return ".gif";
  if (normalizedType.includes("webp")) return ".webp";
  if (normalizedType.includes("bmp")) return ".bmp";
  return ".jpg";
}

function buildImageFilename(imageId, createTime, sourceUrl, contentType) {
  const timestamp = createTime ? new Date(createTime) : new Date();
  const validDate = !Number.isNaN(timestamp.getTime());
  const safeDate = validDate
    ? [
        timestamp.getFullYear(),
        String(timestamp.getMonth() + 1).padStart(2, "0"),
        String(timestamp.getDate()).padStart(2, "0")
      ].join("") +
      "-" +
      [
        String(timestamp.getHours()).padStart(2, "0"),
        String(timestamp.getMinutes()).padStart(2, "0"),
        String(timestamp.getSeconds()).padStart(2, "0")
      ].join("")
    : "unknown-time";
  return `${safeDate}-${sanitizeFileStem(imageId)}${inferExtension(sourceUrl, contentType)}`;
}

async function ensureUniquePath(targetPath) {
  const parsed = path.parse(targetPath);
  let attempt = 0;
  let candidate = targetPath;
  while (true) {
    try {
      await fs.access(candidate);
      attempt += 1;
      candidate = path.join(parsed.dir, `${parsed.name}-${attempt}${parsed.ext}`);
    } catch (error) {
      return candidate;
    }
  }
}

module.exports = {
  buildImageFilename,
  ensureUniquePath,
  listImageFilesRecursive,
  resolveRemoteUrl,
  sanitizeFolderName
};
