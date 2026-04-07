const crypto = require("crypto");
const fs = require("fs/promises");
const path = require("path");
const {
  buildImageFilename,
  ensureUniquePath,
  resolveRemoteUrl,
  sanitizeFolderName
} = require("./file-utils");

function createId(prefix) {
  return `${prefix}_${crypto.randomUUID()}`;
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

async function downloadBuffer(url, sessionToken) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${sessionToken}`
    }
  });

  if (!response.ok) {
    throw new Error(`Download failed with status ${response.status}`);
  }

  return {
    buffer: Buffer.from(await response.arrayBuffer()),
    contentType: response.headers.get("content-type") || ""
  };
}

async function startExportTask(options) {
  const {
    baseUrl,
    sessionToken,
    outputDir,
    items,
    concurrency,
    sendProgress,
    sendCompleted,
    sendFailed
  } = options;

  const queue = Array.isArray(items) ? items.filter(Boolean) : [];
  const taskId = createId("export_task");
  const progress = {
    taskId,
    type: "export",
    status: "running",
    startedAt: new Date().toISOString(),
    total: queue.length,
    completed: 0,
    successCount: 0,
    failedCount: 0,
    writtenCount: 0,
    currentLabel: "",
    lastError: "",
    percent: 0
  };

  if (!queue.length) {
    sendCompleted({
      ...progress,
      status: "completed",
      finishedAt: new Date().toISOString(),
      message: "No images matched the selected tags."
    });
    return { taskId };
  }

  sendProgress({ ...progress });

  try {
    await fs.mkdir(outputDir, { recursive: true });

    await runConcurrent(queue, concurrency, async (item) => {
      const matchedTags = Array.isArray(item.matchedTags) ? item.matchedTags.filter(Boolean) : [];
      if (!matchedTags.length) {
        progress.completed += 1;
        progress.percent = Math.round((progress.completed / progress.total) * 100);
        sendProgress({ ...progress });
        return;
      }

      const remoteUrl = resolveRemoteUrl(baseUrl, item.localUrl || item.sourceUrl);
      progress.currentLabel = `${matchedTags.join(", ")} / ${remoteUrl}`;
      sendProgress({ ...progress });

      try {
        const { buffer, contentType } = await downloadBuffer(remoteUrl, sessionToken);
        for (const tagName of matchedTags) {
          const targetDir = path.join(outputDir, sanitizeFolderName(tagName));
          await fs.mkdir(targetDir, { recursive: true });
          const targetPath = await ensureUniquePath(
            path.join(targetDir, buildImageFilename(item.imageId, item.createTime, remoteUrl, contentType))
          );
          await fs.writeFile(targetPath, buffer);
          progress.writtenCount += 1;
        }
        progress.successCount += 1;
        progress.lastError = "";
      } catch (error) {
        progress.failedCount += 1;
        progress.lastError = error.message || "Export failed";
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
        ? `Export completed with ${progress.failedCount} failed image(s).`
        : "Export completed successfully."
    });
    return { taskId };
  } catch (error) {
    sendFailed({
      ...progress,
      status: "failed",
      finishedAt: new Date().toISOString(),
      message: error.message || "Export failed"
    });
    return { taskId };
  }
}

module.exports = {
  startExportTask
};
