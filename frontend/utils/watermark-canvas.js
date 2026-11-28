/**
 * Canvas 预览绘制。只画到调用方提供的上下文，不导出、不上传。
 * 微信 loadFontFace 的 source 只接受 https 或 Data URL，包内路径会失败。
 * Noto Sans SC Regular 8331336 字节、Bold 8543168 字节，不能打进 2MB 主包。
 * 因此预览使用设备字体，带水印下载仍由服务端用 Noto Sans SC 生成。
 */

export const PREVIEW_FONT_FAMILY = 'sans-serif'
export const FONT_LIMIT_REASON = '微信 loadFontFace 不接受小程序包内路径，完整 Noto Sans SC 也超过主包体积限制。'

export const createDrawGate = () => {
  let current = 0
  return {
    next() {
      current += 1
      return current
    },
    isCurrent(token) {
      return token === current
    }
  }
}

const fillRoundRect = (ctx, x, y, width, height, radius) => {
  const r = Math.max(0, Math.min(radius, width / 2, height / 2))
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + width, y, x + width, y + height, r)
  ctx.arcTo(x + width, y + height, x, y + height, r)
  ctx.arcTo(x, y + height, x, y, r)
  ctx.arcTo(x, y, x + width, y, r)
  ctx.closePath()
  ctx.fill()
}

/** 在画布盒内按原图比例居中放置，避免只压高度导致变形。 */
export const containRect = (srcW, srcH, boxW, boxH) => {
  const sw = Math.max(1, Number(srcW) || 1)
  const sh = Math.max(1, Number(srcH) || 1)
  const bw = Math.max(1, Number(boxW) || 1)
  const bh = Math.max(1, Number(boxH) || 1)
  const scale = Math.min(bw / sw, bh / sh)
  const width = sw * scale
  const height = sh * scale
  return {
    x: (bw - width) / 2,
    y: (bh - height) / 2,
    width,
    height,
    scale
  }
}

export const paintPreview = (ctx, { cssWidth, cssHeight, image, imageRect, layout, visible, fontFamily = PREVIEW_FONT_FAMILY, textPlan }) => {
  ctx.clearRect(0, 0, cssWidth, cssHeight)
  const rect = imageRect || { x: 0, y: 0, width: cssWidth, height: cssHeight }
  if (image) ctx.drawImage(image, rect.x, rect.y, rect.width, rect.height)
  if (!visible || !layout) return { drewWatermark: false, imageRect: rect }
  ctx.fillStyle = 'rgba(0, 0, 0, 0.45)'
  fillRoundRect(ctx, layout.x, layout.y, layout.blockWidth, layout.blockHeight, layout.radius)
  const lines = textPlan || []
  lines.forEach((line) => {
    if (!line.text) return
    ctx.fillStyle = '#ffffff'
    ctx.textBaseline = 'top'
    ctx.font = `${line.bold ? 'bold' : 'normal'} ${Math.max(1, line.fontSize)}px ${fontFamily}`
    ctx.fillText(line.text, line.x, line.y)
  })
  return { drewWatermark: true, imageRect: rect }
}
