import assert from 'node:assert/strict'
import fs from 'node:fs'
import { buildCreatePhotoBody, buildPhotoPatch, createClientRequestId, createSubmitGuard, describeSaveFailure, gpsFromLocation, imageIdFromUpload, LOW_ACCURACY_METERS, messageFor } from '../utils/photo-payload.js'
import { bitmapSize, canvasTextPlan, formatClock, layoutWatermark, scaleLayout, watermarkLines } from '../utils/watermark-layout.js'
import { createDrawGate, paintPreview, PREVIEW_FONT_FAMILY } from '../utils/watermark-canvas.js'

const measure = (text, fontSize) => text.length * fontSize
const fakeContext = () => {
  const calls = []
  return {
    calls,
    beginPath() {},
    moveTo() {},
    arcTo() {},
    closePath() {},
    clearRect(...args) { calls.push(['clearRect', ...args]) },
    drawImage(...args) { calls.push(['drawImage', ...args]) },
    fill() { calls.push(['fill']) },
    fillText(...args) { calls.push(['fillText', ...args]) },
    set fillStyle(value) { calls.push(['fillStyle', value]) },
    set font(value) { calls.push(['font', value]) },
    set textBaseline(value) { calls.push(['textBaseline', value]) }
  }
}

assert.equal(gpsFromLocation(null, { errMsg: 'getLocation:fail auth deny' }).gpsStatus, 'denied')
assert.equal(gpsFromLocation(null, { errMsg: 'getLocation:fail:timeout' }).gpsStatus, 'timeout')
assert.equal(gpsFromLocation(null, { errMsg: 'getLocation:fail:ERROR_NOCELL&WIFI_LOCATIONSWITCHOFF' }).gpsStatus, 'system_off')
assert.equal(gpsFromLocation(null, { errMsg: 'getLocation:fail unknown' }).gpsStatus, 'empty')
assert.equal(gpsFromLocation({ latitude: 23.1, longitude: 113.2, accuracy: 12 }).gpsStatus, 'ok')
const low = gpsFromLocation({ latitude: 23.1, longitude: 113.2, accuracy: LOW_ACCURACY_METERS + 1 })
assert.equal(low.gpsStatus, 'low_accuracy')
assert.equal(low.latitude, 23.1)
assert.equal(gpsFromLocation({}, { errMsg: '' }).latitude, null)

const denied = gpsFromLocation(null, { errMsg: 'getLocation:fail auth deny' })
const failedBody = buildCreatePhotoBody({
  imageId: 9,
  clientRequestId: 'cap-once',
  takenAt: '2026-11-27T11:31:35.000Z',
  note: '现场',
  gpsStatus: denied.gpsStatus,
  watermarkPosition: 'bottom_left',
  watermarkVisible: false,
  widthPx: 1000,
  heightPx: 800,
  locationText: '不能提交',
  weatherText: '晴',
  localUrl: 'https://temporary.example/signed'
})
assert.equal(failedBody.imageId, 9)
assert.equal(Object.hasOwn(failedBody, 'latitude'), false)
assert.equal(Object.hasOwn(failedBody, 'locationText'), false)
assert.equal(Object.hasOwn(failedBody, 'weatherText'), false)
assert.equal(Object.hasOwn(failedBody, 'localUrl'), false)
assert.equal(failedBody.watermarkVisible, false)

const lowBody = buildCreatePhotoBody({ ...low, imageId: 9, clientRequestId: 'cap-once', takenAt: '2026-11-27T11:31:35.000Z', note: '', watermarkPosition: 'bottom_right', widthPx: 10, heightPx: 10, locationText: '广州' })
assert.equal(lowBody.coordinateSystem, 'gcj02')
assert.equal(lowBody.latitude, 23.1)
assert.equal(Object.hasOwn(lowBody, 'locationText'), false)

assert.equal(imageIdFromUpload({ id: 3, imageId: 9, localUrl: 'https://temporary.example/signed' }), 9)
assert.throws(() => imageIdFromUpload({ id: 3, localUrl: 'https://temporary.example/signed' }), /image not found/)

const guard = createSubmitGuard()
assert.equal(guard.tryBegin(), true)
assert.equal(guard.tryBegin(), false)
guard.finish()
assert.equal(guard.tryBegin(), true)
assert.match(describeSaveFailure({ uploaded: false, error: new Error('network') }), /^原图上传失败/)
assert.match(describeSaveFailure({ uploaded: true, error: new Error('archived project is read only') }), /还没有关联到项目/)
assert.match(describeSaveFailure({ uploaded: true, error: new Error('archived project is read only') }), /项目已归档/)

const retry = buildCreatePhotoBody({ ...lowBody })
assert.equal(retry.clientRequestId, lowBody.clientRequestId)
const patch = buildPhotoPatch({ note: '改', watermarkPosition: 'top_left', watermarkVisible: true, latitude: 1, locationText: 'x' })
assert.deepEqual(Object.keys(patch).sort(), ['note', 'watermarkPosition', 'watermarkVisible'])
assert.equal(messageFor({ statusCode: 401, message: '' }), '请先登录')
assert.equal(createClientRequestId().length <= 64, true)

const lines = watermarkLines({ projectName: '项目', note: '', clock: '2026-11-27 19:31', weatherStatus: 'failed' })
assert.equal(lines[1], '')
assert.equal(lines[3], '未获取到地点')
assert.equal(lines[4], '未获取到天气')
assert.equal(formatClock('2026-11-27T11:31:35.000Z'), '2026-11-27 19:31')

const layout = layoutWatermark({ width: 1000, height: 800, lines, position: 'bottom_right', measure })
assert.equal(layout.lines.some((line) => line.text === ''), true)
assert.equal(layout.anchor, 'bottom_right')
const left = layoutWatermark({ width: 1000, height: 800, lines, position: 'bottom_left', measure })
assert.equal(left.anchor, 'bottom_left')
assert.equal(layout.x > left.x, true)

const longNote = '记录'.repeat(40)
const longLayout = layoutWatermark({
  width: 240,
  height: 320,
  lines: watermarkLines({ projectName: '很长的项目名称'.repeat(4), note: longNote, clock: '2026-11-27 19:31', locationText: '', weatherStatus: 'failed' }),
  position: 'bottom_left',
  measure
})
assert.ok(longLayout.lines.length > 5)
assert.equal(longLayout.lines.filter((line) => line.text.includes('记录')).length, 3)
assert.equal(longLayout.lines.at(-1).text, '未获取到天气')

const scaled = scaleLayout(layout, 0.5)
assert.equal(scaled.x, layout.x * 0.5)
assert.equal(scaled.lines[0].fontSize, layout.lines[0].fontSize * 0.5)
const bitmap = bitmapSize(100, 80, 3)
assert.deepEqual(bitmap, { width: 300, height: 240, pixelRatio: 3 })

const hidden = fakeContext()
const hiddenResult = paintPreview(hidden, { cssWidth: 100, cssHeight: 80, image: { src: 'original' }, layout: scaled, visible: false, textPlan: canvasTextPlan(scaled) })
assert.equal(hiddenResult.drewWatermark, false)
assert.equal(hidden.calls.some((call) => call[0] === 'fillText'), false)
assert.equal(hidden.calls.some((call) => call[0] === 'clearRect'), true)

const shown = fakeContext()
const shownResult = paintPreview(shown, { cssWidth: 100, cssHeight: 80, image: { src: 'original' }, layout: scaled, visible: true, fontFamily: PREVIEW_FONT_FAMILY, textPlan: canvasTextPlan(scaled) })
assert.equal(shownResult.drewWatermark, true)
assert.equal(shown.calls.filter((call) => call[0] === 'fillText').length >= 4, true)
assert.equal(shown.calls.some((call) => call[0] === 'font' && String(call[1]).includes(PREVIEW_FONT_FAMILY)), true)

const gate = createDrawGate()
const first = gate.next()
const second = gate.next()
assert.equal(gate.isCurrent(first), false)
assert.equal(gate.isCurrent(second), true)

const pages = JSON.parse(fs.readFileSync(new URL('../pages.json', import.meta.url), 'utf8'))
assert.deepEqual(pages.tabBar.list.map((item) => item.text), ['首页', '水印相机', '插件', '桌面端'])
const indexSource = fs.readFileSync(new URL('../pages/index/index.vue', import.meta.url), 'utf8')
assert.equal(indexSource.indexOf("rawScene.startsWith('pinv_')") < indexSource.indexOf('setPluginLoginToken(token)'), true)
const detailSource = fs.readFileSync(new URL('../pages/watermark/detail.vue', import.meta.url), 'utf8')
assert.equal(detailSource.includes('/api/location/reverse'), false)
assert.equal(detailSource.includes('/api/weather/current'), false)
assert.equal(detailSource.includes('getLocation'), false)
const collectSource = fs.readFileSync(new URL('../pages/collect/collect.vue', import.meta.url), 'utf8')
assert.equal(collectSource.includes('/api/image/upload'), true)
const collectionDetail = fs.readFileSync(new URL('../pages/detail/detail.vue', import.meta.url), 'utf8')
assert.equal(collectionDetail.includes('/api/image/delete/'), true)
const manifest = JSON.parse(fs.readFileSync(new URL('../manifest.json', import.meta.url), 'utf8'))
assert.equal(manifest['mp-weixin'].requiredPrivateInfos.includes('getLocation'), true)

console.log(`watermark tests passed: ${canvasTextPlan(layout).length} canvas lines`)
