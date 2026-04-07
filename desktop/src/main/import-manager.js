const crypto = require("crypto");
const fs = require("fs/promises");
const path = require("path");
const { listImageFilesRecursive, sanitizeFolderName } = require("./file-utils");

const importSessions = new Map();

function createId(prefix) {
  return `${prefix}_${crypto.randomUUID()}`;
}

async function prepareImportFolders(folderPaths) {
  const normalizedPaths = Array.isArray(folderPaths)
    ? folderPaths.map((item) => String(item || "").trim()).filter(Boolean)
    : [];

  if (!normalizedPaths.length) {
    return { sessionId: "", folders: [] };
  }

  const entries = [];
  const session = new Map();

  for (const folderPath of normalizedPaths) {
    const folderName = path.basename(folderPath);
    const files = await listImageFilesRecursive(folderPath);
    const folderId = createId("folder");
    const entry = {
      folderId,
      folderPath,
      folderName,
      defaultTag: sanitizeFolderName(folderName),
      fileCount: files.length,
      previewFiles: files.slice(0, 3).map((filePath) => path.relative(folderPath, filePath))
    };
    entries.push(entry);
    session.set(folderId, {
      ...entry,
      files
    });
  }

  const sessionId = createId("import_session");
  importSessions.set(sessionId, session);
  return { sessionId, folders: entries };
}

async function runConcurrent(queue, concurrency, worker) {
  const size = Math.max(1, Number(concurrency) || 1);
  let index = 0;
  const workers = new Array(Math.min(size, queue.length)).fill(null).map(async () => {
    while (true) {
      const current = index;
      index += 1;
      if (current >= queue.length) {
        return;
      }
      await worker(queue[current], current);
    }
  });
  await Promise.all(workers);
}

async function uploadSingleFile({ baseUrl, sessionToken, filePath, tagName }) {
  const fileBuffer = await fs.readFile(filePath);
  const formData = new FormData();
  formData.append("file", new Blob([fileBuffer]), path.basename(filePath));
  formData.append("tags", tagName);

  const response = await fetch(`${baseUrl}/api/image/upload`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${sessionToken}`
    },
    body: formData
  });

  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : {};
  } catch (error) {
    payload = { message: text };
  }

  if (!response.ok) {
    throw new Error(payload?.message || `Upload failed with status ${response.status}`);
  }

  return payload;
}

async function startImportTask(options) {
  const {
    baseUrl,
    sessionToken,
    sessionId,
    mappings,
    concurrency,
    sendProgress,
    sendCompleted,
    sendFailed
  } = options;

  const session = importSessions.get(sessionId);
  if (!session) {
    throw new Error("Import session has expired. Please reselect folders.");
  }

  const tagByFolderId = new Map(
    (Array.isArray(mappings) ? mappings : []).map((item) => [item.folderId, String(item.tagName || "").trim()])
  );
  const queue = [];

  for (const [folderId, folder] of session.entries()) {
    const tagName = tagByFolderId.get(folderId) || folder.defaultTag;
    for (const filePath of folder.files) {
      queue.push({
        folderName: folder.folderName,
        filePath,
        tagName
      });
    }
  }

  const taskId = createId("import_task");
  const progress = {
    taskId,
    type: "import",
    status: "running",
    startedAt: new Date().toISOString(),
    total: queue.length,
    completed: 0,
    successCount: 0,
    failedCount: 0,
    currentLabel: "",
    lastError: "",
    percent: 0
  };

  if (!queue.length) {
    sendCompleted({
      ...progress,
      status: "completed",
      finishedAt: new Date().toISOString(),
      message: "No image files were found in the selected folders."
    });
    return { taskId };
  }

  sendProgress({ ...progress });

  try {
    await runConcurrent(queue, concurrency, async (item) => {
      progress.currentLabel = `${item.folderName} / ${path.basename(item.filePath)}`;
      sendProgress({ ...progress });

      try {
        await uploadSingleFile({
          baseUrl,
          sessionToken,
          filePath: item.filePath,
          tagName: item.tagName
        });
        progress.successCount += 1;
        progress.lastError = "";
      } catch (error) {
        progress.failedCount += 1;
        progress.lastError = error.message || "Upload failed";
      } finally {
        progress.completed += 1;
        progress.percent = Math.round((progress.completed / progress.total) * 100);
        sendProgress({ ...progress });
      }
    });

    sendCompleted({
      ...progress,
      status: "completed",
      finishedAt: new Date().toISOString(),
      message: progress.failedCount
        ? `Import completed with ${progress.failedCount} failed file(s).`
        : "Import completed successfully."
    });
    return { taskId };
  } catch (error) {
    sendFailed({
      ...progress,
      status: "failed",
      finishedAt: new Date().toISOString(),
      message: error.message || "Import failed"
    });
    return { taskId };
  }
}

module.exports = {
  prepareImportFolders,
  startImportTask
};
