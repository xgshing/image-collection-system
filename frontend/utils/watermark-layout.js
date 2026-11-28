/**
 * 水印排版。数值是建议参数，空记录占一行是已确认规则。
 * 这里不使用系统字体，也不把「无法获取定位」「无法获取天气」写进提交字段。
 */

// 保持打包进小程序，避免微信开发者工具因残留 sourcemap 找不到该文件而预览失败。
export { containRect } from './watermark-canvas.js'

export const NO_LOCATION = '未获取到地点'
export const NO_WEATHER = '未获取到天气'
export const POSITIONS = ['top_left', 'top_right', 'bottom_left', 'bottom_right']
export const WATERMARK_FONT_URL = ''

export const formatClock = (iso) => {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  // 微信真机部分环境没有 Intl，改用 UTC+8 手工换算（上海无夏令时）
  const shifted = new Date(date.getTime() + 8 * 60 * 60 * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())} ${pad(shifted.getUTCHours())}:${pad(shifted.getUTCMinutes())}`
}

/** 按字符宽度把水印值拆成多行，供 cover-view 使用（真机不支持自动换行）。 */
export const wrapOverlayLines = (text, charsPerLine, maxLines = 3) => {
  const value = text == null ? '' : String(text)
  const size = Math.max(4, Number(charsPerLine) || 8)
  const limit = Math.max(1, Number(maxLines) || 3)
  if (!value) return ['']
  const lines = []
  for (let index = 0; index < value.length && lines.length < limit; index += size) {
    let chunk = value.slice(index, index + size)
    if (lines.length === limit - 1 && index + size < value.length) {
      chunk = `${chunk.slice(0, Math.max(1, chunk.length - 1))}…`
    }
    lines.push(chunk)
  }
  return lines
}

export const watermarkLines = (photo = {}) => {
  const note = photo.note == null ? '' : String(photo.note)
  const location = photo.locationText ? String(photo.locationText) : NO_LOCATION
  let weather = NO_WEATHER
  if (photo.weatherStatus === 'ok' && photo.weatherText) {
    weather = photo.temperatureC == null || photo.temperatureC === ''
      ? String(photo.weatherText)
      : `${photo.weatherText} ${photo.temperatureC}℃`
  }
  return [photo.projectName || '', note, photo.clock || '', location, weather]
}

const wrapText = (text, maxWidth, measure) => {
  const value = text == null ? '' : String(text)
  if (!value) return []
  const lines = []
  let current = ''
  for (const char of value) {
    const next = current + char
    if (current && measure(next) > maxWidth) {
      lines.push(current)
      current = char
    } else {
      current = next
    }
  }
  if (current) lines.push(current)
  return lines
}

const ellipsis = (text, maxWidth, measure) => {
  const suffix = '…'
  let value = text || ''
  if (measure(value + suffix) <= maxWidth) return value + suffix
  while (value && measure(value + suffix) > maxWidth) value = value.slice(0, -1)
  return value + suffix
}

export const layoutWatermark = ({ width, height, lines, position = 'bottom_left', measure }) => {
  const safeWidth = Math.max(1, Number(width) || 1)
  const safeHeight = Math.max(1, Number(height) || 1)
  const shortSide = Math.min(safeWidth, safeHeight)
  const titleSize = Math.max(12, Math.round(shortSide * 0.035))
  const bodySize = Math.max(12, Math.round(shortSide * 0.028))
  const padding = Math.max(1, Math.round(shortSide * 0.02))
  const margin = Math.max(1, Math.round(shortSide * 0.04))
  const radius = Math.max(1, Math.round(shortSide * 0.012))
  const maxTextWidth = Math.max(1, Math.round(safeWidth * 0.72) - padding * 2)
  const source = Array.isArray(lines) ? lines : []
  const laid = []
  for (let index = 0; index < source.length; index += 1) {
    const fontSize = index === 0 ? titleSize : bodySize
    const bold = index === 0
    const text = source[index] == null ? '' : String(source[index])
    const lineHeight = Math.round(fontSize * 1.18)
    if (index === 1 && text === '') {
      laid.push({ text: '', fontSize, bold, lineHeight })
      continue
    }
    if (text === '') continue
    const wrapped = wrapText(text, maxTextWidth, (value) => measure(value, fontSize, bold))
    // 备注、地点、天气允许最多三行；标题和时间保持一行
    const canWrap = index === 1 || index === 3 || index === 4
    const kept = canWrap ? wrapped.slice(0, 3) : wrapped.slice(0, 1)
    if (!canWrap && wrapped.length > 1) {
      laid.push({
        text: ellipsis(wrapped[0], maxTextWidth, (value) => measure(value, fontSize, bold)),
        fontSize,
        bold,
        lineHeight
      })
      continue
    }
    if (canWrap && wrapped.length > kept.length && kept.length) {
      kept[kept.length - 1] = ellipsis(kept[kept.length - 1], maxTextWidth, (value) => measure(value, fontSize, bold))
    }
    kept.forEach((line) => laid.push({ text: line, fontSize, bold, lineHeight }))
  }
  let blockWidth = padding * 2
  let blockHeight = padding * 2
  laid.forEach((line) => {
    const textWidth = line.text ? measure(line.text, line.fontSize, line.bold) : 0
    blockWidth = Math.max(blockWidth, textWidth + padding * 2)
    blockHeight += line.lineHeight
  })
  const anchor = POSITIONS.includes(position) ? position : 'bottom_left'
  let x = anchor.endsWith('right') ? safeWidth - margin - blockWidth : margin
  let y = anchor.startsWith('bottom') ? safeHeight - margin - blockHeight : margin
  x = Math.max(0, Math.min(x, safeWidth - blockWidth))
  y = Math.max(0, Math.min(y, safeHeight - blockHeight))
  return { lines: laid, x, y, blockWidth, blockHeight, padding, radius, anchor }
}

export const canvasTextPlan = (layout) => {
  let cursor = layout.y + layout.padding
  return layout.lines.map((line) => {
    const item = { text: line.text, x: layout.x + layout.padding, y: cursor, fontSize: line.fontSize, bold: line.bold, lineHeight: line.lineHeight }
    cursor += line.lineHeight
    return item
  })
}

export const scaleLayout = (layout, scale) => {
  const factor = Number.isFinite(Number(scale)) && Number(scale) > 0 ? Number(scale) : 1
  return {
    ...layout,
    x: layout.x * factor,
    y: layout.y * factor,
    blockWidth: layout.blockWidth * factor,
    blockHeight: layout.blockHeight * factor,
    padding: layout.padding * factor,
    radius: layout.radius * factor,
    lines: layout.lines.map((line) => ({
      ...line,
      fontSize: line.fontSize * factor,
      lineHeight: line.lineHeight * factor
    }))
  }
}

export const bitmapSize = (cssWidth, cssHeight, pixelRatio) => {
  const ratio = Number(pixelRatio) > 0 ? Number(pixelRatio) : 1
  return {
    width: Math.max(1, Math.round(Number(cssWidth) * ratio)),
    height: Math.max(1, Math.round(Number(cssHeight) * ratio)),
    pixelRatio: ratio
  }
}
