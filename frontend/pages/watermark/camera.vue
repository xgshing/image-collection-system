<template>
  <view class="page">
    <camera
      v-if="ready && !noteOpen && cameraAlive"
      id="wmCamera"
      class="camera"
      :device-position="facing"
      :flash="flash"
      @initdone="onCameraInit"
      @stop="onCameraStop"
      @error="onCameraError"
    />
    <view v-else-if="!ready" class="notice">
      <view class="title">需要相机权限</view>
      <view class="text">拍照需要使用相机。若刚才拒绝了授权，请点下方按钮打开设置并开启相机。</view>
      <button class="primary" open-type="openSetting" hover-class="none" @opensetting="onSettingReturn">去开启相机权限</button>
    </view>

    <!-- 九宫格构图辅助线：不用全屏罩层遮挡 camera，避免拦截原生触摸 -->
    <cover-view v-if="ready && !noteOpen" class="grid-v one"></cover-view>
    <cover-view v-if="ready && !noteOpen" class="grid-v two"></cover-view>
    <cover-view v-if="ready && !noteOpen" class="grid-h one"></cover-view>
    <cover-view v-if="ready && !noteOpen" class="grid-h two"></cover-view>

    <cover-view
      v-if="ready && !noteOpen && watermarkOn"
      :key="markKey"
      class="mark"
      :class="position"
      :style="markStyle"
    >
      <cover-view class="mark-title">
        <cover-view class="dot"></cover-view>
        <cover-view class="mark-name">{{ projectName }}</cover-view>
      </cover-view>
      <cover-view class="mark-line" :style="lineBox(noteLines.length)" @tap="openNote">
        <cover-view class="mark-label">
          <cover-view class="mark-ch">备</cover-view>
          <cover-view class="mark-ch">注</cover-view>
          <cover-view class="mark-ch">内</cover-view>
          <cover-view class="mark-ch">容</cover-view>
        </cover-view>
        <cover-view class="mark-colon">：</cover-view>
        <cover-view class="mark-values">
          <cover-view v-for="(line, index) in noteLines" :key="'note-' + index" class="mark-value-line">{{ line }}</cover-view>
        </cover-view>
      </cover-view>
      <cover-view class="mark-line" :style="lineBox(1)">
        <cover-view class="mark-label">
          <cover-view class="mark-ch">拍</cover-view>
          <cover-view class="mark-ch">摄</cover-view>
          <cover-view class="mark-ch">时</cover-view>
          <cover-view class="mark-ch">间</cover-view>
        </cover-view>
        <cover-view class="mark-colon">：</cover-view>
        <cover-view class="mark-values">
          <cover-view class="mark-value-line">{{ clockText || '获取中…' }}</cover-view>
        </cover-view>
      </cover-view>
      <cover-view class="mark-line" :style="lineBox(locationLines.length)">
        <cover-view class="mark-label">
          <cover-view class="mark-ch">地</cover-view>
          <cover-view class="mark-ch">点</cover-view>
        </cover-view>
        <cover-view class="mark-colon">：</cover-view>
        <cover-view class="mark-values">
          <cover-view v-for="(line, index) in locationLines" :key="'loc-' + index" class="mark-value-line">{{ line }}</cover-view>
        </cover-view>
      </cover-view>
      <cover-view class="mark-line" :style="lineBox(weatherLines.length)">
        <cover-view class="mark-label">
          <cover-view class="mark-ch">天</cover-view>
          <cover-view class="mark-ch">气</cover-view>
        </cover-view>
        <cover-view class="mark-colon">：</cover-view>
        <cover-view class="mark-values">
          <cover-view v-for="(line, index) in weatherLines" :key="'wx-' + index" class="mark-value-line">{{ line }}</cover-view>
        </cover-view>
      </cover-view>
    </cover-view>

    <cover-view v-if="ready && !noteOpen" class="topbar" :style="{ paddingTop: statusBar + 'px' }">
      <cover-view class="icon-btn" @tap="close">×</cover-view>
      <cover-view class="top-pill flash-pill" @tap="cycleFlash">
        <cover-image class="flash-icon" :src="flashIcon" @tap="cycleFlash" />
        <cover-view class="flash-text" @tap="cycleFlash">{{ flashLabel }}</cover-view>
      </cover-view>
      <cover-view class="top-pill" @tap="toggleLocal">本地原图: {{ saveOriginal ? '开' : '关' }}</cover-view>
      <cover-view class="icon-btn flip" @tap="flip">↺</cover-view>
    </cover-view>

    <cover-view v-if="openProjects && !noteOpen" class="picker" :style="{ bottom: dockHeight + 'px' }" @tap="openProjects = false">
      <cover-view class="picker-card" @tap.stop="">
        <cover-view v-if="!activeProjects.length" class="picker-empty">没有可切换的项目</cover-view>
        <cover-view v-for="item in activeProjects" :key="item.id" class="picker-row" @tap.stop="chooseProject(item)">
          <cover-view class="picker-name">{{ item.name }}</cover-view>
          <cover-view v-if="item.id === projectId" class="picker-current">当前</cover-view>
        </cover-view>
      </cover-view>
    </cover-view>

    <cover-view v-if="openGear && !noteOpen" class="gear-menu" :style="{ bottom: dockHeight + 'px' }">
      <cover-view v-for="item in positions" :key="item" class="gear-item" @tap="setPosition(item)">{{ positionText[item] }}</cover-view>
    </cover-view>

    <cover-view v-if="ready && !noteOpen" class="dock" :style="{ paddingBottom: safeBottom + 'px' }">
      <cover-view class="zoom-bar">
        <cover-view class="zoom-pill ultra" @tap="shootWithSystemCamera">超广角</cover-view>
        <cover-view class="zoom-btn" @tap="stepZoom(-0.5)">－</cover-view>
        <cover-view
          v-for="preset in zoomPresets"
          :key="preset"
          class="zoom-pill"
          :class="{ active: Math.abs(zoom - preset) < 0.15 }"
          @tap="applyZoom(preset)"
        >{{ preset }}x</cover-view>
        <cover-view class="zoom-now">{{ zoomDisplay }}</cover-view>
        <cover-view class="zoom-btn" @tap="stepZoom(0.5)">＋</cover-view>
      </cover-view>
      <cover-view class="dock-meta">
        <cover-view class="project-chip" @tap="toggleProjects">
          <cover-image class="chip-folder" src="/static/watermark/folder-white.png" @tap="toggleProjects" />
          <cover-view class="chip-name">{{ projectName }}</cover-view>
          <cover-view class="chip-arrow">▾</cover-view>
        </cover-view>
        <cover-view class="wm-state">
          <cover-view class="wm-toggle" @tap="toggleWatermark">水印: {{ watermarkOn ? '开' : '关' }}</cover-view>
          <cover-view class="wm-pos" @tap="cyclePosition">{{ positionText[position] }}</cover-view>
        </cover-view>
      </cover-view>
      <cover-view class="shutter-row">
        <cover-view class="thumb" @tap="openThumb">
          <cover-image v-if="thumbUrl" class="thumb-image" :src="thumbUrl" @tap="openThumb" />
        </cover-view>
        <cover-view class="shutter" :class="{ busy: shooting }" @tap="shoot">
          <cover-view class="shutter-core" @tap="shoot"></cover-view>
        </cover-view>
        <cover-view class="gear" @tap="toggleGear">⚙</cover-view>
      </cover-view>
    </cover-view>

    <view v-if="noteOpen" class="note-layer" :style="{ paddingTop: statusBar + 'px' }" @click="closeNote">
      <view class="note-card" @click.stop>
        <view class="note-head">
          <text class="note-title">添加备注</text>
          <text class="note-close" @click="closeNote">×</text>
        </view>
        <textarea v-model="noteDraft" class="note-input" maxlength="500" placeholder="输入备注内容，将显示在水印里" />
        <view class="voice-tip">{{ voiceTip }}</view>
        <view
          class="hold-btn"
          :class="{ holding: voiceHolding, cancel: voiceCancel }"
          @touchstart.prevent="onVoiceStart"
          @touchmove.prevent="onVoiceMove"
          @touchend.prevent="onVoiceEnd"
          @touchcancel.prevent="onVoiceEnd"
        >按住说话</view>
      </view>
    </view>

    <view v-if="noteOpen && (voiceHolding || recognizing || speechPreview !== null)" class="speech-modal-mask" @click="closeSpeechModal">
      <view class="speech-modal" @click.stop>
        <template v-if="voiceHolding">
          <view class="speech-modal-title">{{ voiceCancel ? '松开取消语音' : '正在聆听…' }}</view>
          <view class="speech-modal-desc">{{ voiceCancel ? '已上滑，松手将取消本次识别' : '松手结束并转换为文字，上滑取消' }}</view>
        </template>
        <template v-else-if="recognizing">
          <view class="speech-modal-title">正在转换文字…</view>
          <view class="speech-modal-desc">请稍候，马上完成</view>
        </template>
        <template v-else>
          <view class="speech-modal-title">语音转换结果</view>
          <view class="speech-modal-body">{{ speechPreview || '（没有识别到文字）' }}</view>
          <view class="speech-modal-actions">
            <button class="speech-btn cancel" hover-class="none" @click="closeSpeechModal">取消</button>
            <button class="speech-btn ok" hover-class="none" :disabled="!speechPreview" @click="useSpeech">确认填入</button>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { apiJson, uploadSpeech } from '../../utils/project-api'
import { createClientRequestId, describeApiFailure, describeGpsFailure, gpsFromLocation, messageFor } from '../../utils/photo-payload'
import { formatClock, wrapOverlayLines } from '../../utils/watermark-layout'
import { SAVE_ORIGINAL_KEY, readProjectId, saveOriginalEnabled, writeCapture, writeProjectId } from '../../utils/watermark-session'

const positions = ['bottom_left', 'bottom_right', 'top_left', 'top_right']
const positionText = { top_left: '左上', top_right: '右上', bottom_left: '左下', bottom_right: '右下' }
const flashOrder = ['auto', 'off', 'torch']
const flashText = { auto: '自动', off: '关闭', torch: '常亮' }
const flashIcons = {
  auto: '/static/watermark/flash-auto.png',
  off: '/static/watermark/flash-off.png',
  torch: '/static/watermark/flash-torch.png'
}
const GPS_CACHE_MS = 180000
const system = uni.getSystemInfoSync()
const statusBar = ref(system.statusBarHeight || 20)
const safeBottom = ref(system.safeAreaInsets?.bottom || 0)
const dockHeight = 204
const windowWidth = system.windowWidth || 375
const windowHeight = system.windowHeight || 667

const projectId = ref(0)
const projectName = ref('尚未选择项目')
const projects = ref([])
const ready = ref(false)
const cameraAlive = ref(true)
const denied = ref(false)
const shooting = ref(false)
const facing = ref('back')
const flash = ref('auto')
const zoom = ref(1)
const maxZoom = ref(5)
const saveOriginal = ref(true)
const watermarkOn = ref(true)
const position = ref('bottom_left')
const clockText = ref('')
const locationText = ref('定位中…')
const weatherText = ref('天气获取中…')
const note = ref('')
const noteOpen = ref(false)
const noteDraft = ref('')
const speechPreview = ref(null)
const recognizing = ref(false)
const voiceHolding = ref(false)
const voiceCancel = ref(false)
const thumbUrl = ref('')
const thumbId = ref(0)
const openProjects = ref(false)
const openGear = ref(false)
const deviceAngle = ref(0)
let locationPrompted = false
let locationDenied = false
let clockTimer = 0
let remountTimer = 0
let pageVisible = true
let settingPrompted = false
let accelListening = false
let recorder = null
let voiceStartY = 0
let voiceCancelled = false
let voicePressing = false
let voiceRecording = false
let voiceHoldTimer = 0
let voiceStartedAt = 0
let cachedGps = null
let cachedGpsAt = 0
let prefetchToken = 0

const flashLabel = computed(() => flashText[flash.value] || '自动')
const flashIcon = computed(() => flashIcons[flash.value] || flashIcons.auto)
// 微信 camera 组件变焦范围为 [1, maxZoom]，不支持 0.5x，超广角由系统相机提供
const zoomPresets = computed(() => {
  const max = Math.max(1, Number(maxZoom.value) || 5)
  const list = [1]
  if (max >= 2) list.push(2)
  if (max >= 3) list.push(3)
  if (max >= 5) list.push(5)
  return list
})
const zoomDisplay = computed(() => `${Number(zoom.value).toFixed(1).replace(/\.0$/, '')}x`)
const activeProjects = computed(() => projects.value.filter((item) => item.status !== 'archived'))
const noteHint = computed(() => note.value.trim() || '点击添加备注')
const voiceTip = computed(() => '按住说话，松手转文字，上滑取消')
const markWidthPx = Math.floor(windowWidth * 0.48)
const LINE_PX = 14
const overlayChars = computed(() => {
  const unit = windowWidth / 750
  const valuePx = markWidthPx - (124 * unit)
  const charPx = Math.max(10, 20 * unit)
  return Math.max(6, Math.floor(valuePx / charPx))
})
const noteLines = computed(() => wrapOverlayLines(noteHint.value, overlayChars.value, 3))
const locationLines = computed(() => wrapOverlayLines(locationText.value, overlayChars.value, 3))
const weatherLines = computed(() => wrapOverlayLines(weatherText.value, overlayChars.value, 3))
const lineBox = (count) => ({ height: `${Math.max(1, count) * LINE_PX}px` })
const visualPosition = (pos, angle) => {
  if (angle === 90) {
    const map = {
      bottom_left: 'top_left',
      bottom_right: 'bottom_left',
      top_left: 'top_right',
      top_right: 'bottom_right'
    }
    return map[pos] || pos
  }
  if (angle === -90) {
    const map = {
      bottom_left: 'bottom_right',
      bottom_right: 'top_right',
      top_left: 'bottom_left',
      top_right: 'top_left'
    }
    return map[pos] || pos
  }
  return pos
}

const markStyle = computed(() => {
  const lines = 1 + noteLines.value.length + locationLines.value.length + weatherLines.value.length
  const w = markWidthPx
  const h = 22 + lines * LINE_PX
  const angle = deviceAngle.value
  const effectivePos = visualPosition(position.value, angle)
  const edgePx = Math.round(24 * (windowWidth / 750))
  const topBase = statusBar.value + 64
  const bottomBase = safeBottom.value + dockHeight
  const shift = angle !== 0 ? Math.round((w - h) / 2) : 0
  const style = { width: `${w}px`, height: `${h}px` }
  if (effectivePos === 'top_left' || effectivePos === 'bottom_left') {
    style.left = `${edgePx - shift}px`
  } else {
    style.right = `${edgePx - shift}px`
  }
  if (effectivePos === 'top_left' || effectivePos === 'top_right') {
    style.top = `${topBase + shift}px`
  } else {
    style.bottom = `${bottomBase + shift}px`
  }
  if (angle !== 0) {
    style.transform = `rotate(${angle}deg)`
    style.transformOrigin = 'center center'
  }
  return style
})
const markKey = computed(() => [
  projectId.value,
  projectName.value,
  noteHint.value,
  clockText.value,
  locationText.value,
  weatherText.value
].join('|'))

const tick = () => {
  try {
    const now = new Date()
    const formatted = formatClock(now.toISOString()).replace(/-/g, '.')
    if (formatted) {
      clockText.value = formatted
      return
    }
    const pad = (n) => String(n).padStart(2, '0')
    clockText.value = `${now.getFullYear()}.${pad(now.getMonth() + 1)}.${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
  } catch (error) {
    clockText.value = `时间异常:${error?.message || error}`
  }
}

const showPermissionPage = () => {
  denied.value = true
  ready.value = false
}

const promptCameraSetting = () => {
  showPermissionPage()
  if (settingPrompted) return
  settingPrompted = true
  setTimeout(() => {
    uni.showModal({
      title: '需要相机权限',
      content: '请点击「去开启相机权限」，在设置里打开相机后返回。',
      confirmText: '知道了',
      showCancel: false,
      complete: () => { settingPrompted = false }
    })
  }, 200)
}

const onSettingReturn = (event) => {
  settingPrompted = false
  const enabled = event?.detail?.authSetting?.['scope.camera']
  if (enabled) {
    denied.value = false
    ready.value = true
    return
  }
  prepare()
}

const close = () => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/watermark/home' }) })
const flip = () => {
  facing.value = facing.value === 'back' ? 'front' : 'back'
  zoom.value = 1
}
const onCameraInit = (event) => {
  const rawMax = Number(event?.detail?.maxZoom)
  if (Number.isFinite(rawMax) && rawMax >= 1) {
    maxZoom.value = Math.min(10, Math.round(rawMax * 10) / 10)
  }
  applyZoom(zoom.value)
}
const applyZoom = (value) => {
  const limit = Math.max(1, Number(maxZoom.value) || 5)
  const next = Math.max(1, Math.min(limit, Math.round(Number(value) * 10) / 10))
  zoom.value = next
  const ctx = uni.createCameraContext()
  if (ctx && typeof ctx.setZoom === 'function') {
    ctx.setZoom({ zoom: next })
  }
}
const stepZoom = (delta) => {
  applyZoom((Number(zoom.value) || 1) + delta)
}
const cycleFlash = () => {
  const index = flashOrder.indexOf(flash.value)
  flash.value = flashOrder[(index + 1) % flashOrder.length]
}
const toggleLocal = () => {
  saveOriginal.value = !saveOriginal.value
  uni.setStorageSync(SAVE_ORIGINAL_KEY, saveOriginal.value ? '1' : '0')
}
const cyclePosition = () => {
  const index = positions.indexOf(position.value)
  position.value = positions[(index + 1) % positions.length]
}
const setPosition = (value) => {
  position.value = value
  openGear.value = false
}
const toggleGear = () => {
  openProjects.value = false
  openGear.value = !openGear.value
}
const toggleProjects = () => {
  openGear.value = false
  openProjects.value = !openProjects.value
}
const toggleWatermark = () => {
  watermarkOn.value = !watermarkOn.value
}

const remountCamera = () => {
  cameraAlive.value = false
  clearTimeout(remountTimer)
  remountTimer = setTimeout(() => {
    if (pageVisible) cameraAlive.value = true
  }, 120)
}

const onCameraStop = () => {
  if (pageVisible && !shooting.value) {
    remountCamera()
  }
}

const prepare = () => {
  uni.getSetting({
    success: (setting) => {
      const state = setting.authSetting?.['scope.camera']
      if (state === false) {
        promptCameraSetting()
        return
      }
      denied.value = false
      ready.value = true
      settingPrompted = false
    },
    fail: () => {
      denied.value = false
      ready.value = true
    }
  })
}

const loadProject = async (applyDefaults = false) => {
  if (!projectId.value) return
  try {
    const detail = await apiJson(`/api/project/${projectId.value}`)
    projectName.value = detail.name || '未命名项目'
    if (applyDefaults) {
      position.value = detail.defaultWatermarkPosition || 'bottom_left'
      watermarkOn.value = detail.watermarkEnabled !== false
    }
  } catch (error) {
    uni.showToast({ title: messageFor(error, '项目读取失败'), icon: 'none' })
  }
  try {
    const body = await apiJson('/api/project?page=1&pageSize=50')
    projects.value = Array.isArray(body.items) ? body.items : []
  } catch (error) {
    projects.value = []
  }
  try {
    const photos = await apiJson(`/api/project/${projectId.value}/photos?page=1&pageSize=1`)
    const first = Array.isArray(photos.items) ? photos.items[0] : null
    thumbUrl.value = first?.previewUrl || ''
    thumbId.value = first?.id || 0
  } catch (error) {
    thumbUrl.value = ''
    thumbId.value = 0
  }
}

const chooseProject = async (item) => {
  if (!item || item.status === 'archived') return
  projectId.value = item.id
  writeProjectId(item.id)
  openProjects.value = false
  await loadProject(true)
}

const openThumb = () => {
  if (!thumbId.value) return
  uni.navigateTo({ url: `/pages/watermark/detail?projectId=${projectId.value}&photoId=${thumbId.value}` })
}

const ensureLocationAuth = () => new Promise((resolve) => {
  if (locationDenied) {
    resolve({ ok: false, reason: '定位权限此前已拒绝' })
    return
  }
  uni.getSetting({
    success: (setting) => {
      const state = setting.authSetting?.['scope.userLocation']
      if (state === true) {
        resolve({ ok: true, reason: '' })
        return
      }
      if (state === false) {
        if (!locationPrompted) {
          locationPrompted = true
          uni.showModal({
            title: '需要定位权限',
            content: '打开定位后，拍照页才能自动显示地点和天气。',
            confirmText: '打开设置',
            success: (result) => {
              if (result.confirm) {
                uni.openSetting({
                  success: (again) => {
                    const ok = again.authSetting?.['scope.userLocation'] === true
                    locationDenied = !ok
                    resolve({ ok, reason: ok ? '' : '设置页未开启定位权限' })
                  },
                  fail: (error) => {
                    locationDenied = true
                    resolve({ ok: false, reason: `打开设置失败:${error?.errMsg || 'unknown'}` })
                  }
                })
                return
              }
              locationDenied = true
              resolve({ ok: false, reason: '用户取消开启定位权限' })
            }
          })
          return
        }
        locationDenied = true
        resolve({ ok: false, reason: '定位权限已关闭' })
        return
      }
      uni.authorize({
        scope: 'scope.userLocation',
        success: () => resolve({ ok: true, reason: '' }),
        fail: (error) => {
          if (!locationPrompted) {
            locationPrompted = true
            uni.showModal({
              title: '需要定位权限',
              content: '打开定位后，拍照页才能自动显示地点和天气。',
              confirmText: '打开设置',
              success: (result) => {
                if (result.confirm) {
                  uni.openSetting({
                    success: (again) => {
                      const ok = again.authSetting?.['scope.userLocation'] === true
                      locationDenied = !ok
                      resolve({ ok, reason: ok ? '' : '设置页未开启定位权限' })
                    },
                    fail: (openError) => {
                      locationDenied = true
                      resolve({ ok: false, reason: `打开设置失败:${openError?.errMsg || 'unknown'}` })
                    }
                  })
                  return
                }
                locationDenied = true
                resolve({ ok: false, reason: `用户拒绝授权:${error?.errMsg || 'auth deny'}` })
              }
            })
            return
          }
          locationDenied = true
          resolve({ ok: false, reason: `定位授权失败:${error?.errMsg || 'auth deny'}` })
        }
      })
    },
    fail: (error) => resolve({ ok: false, reason: `读取权限失败:${error?.errMsg || 'getSetting fail'}` })
  })
})

const locate = async ({ highAccuracy = true } = {}) => {
  const auth = await ensureLocationAuth()
  if (!auth.ok) return gpsFromLocation(null, { errMsg: auth.reason || 'auth deny' })
  return new Promise((resolve) => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: highAccuracy,
      highAccuracyExpireTime: highAccuracy ? 3500 : 1500,
      success: (positionInfo) => {
        const gps = gpsFromLocation(positionInfo)
        cachedGps = gps
        cachedGpsAt = Date.now()
        resolve(gps)
      },
      fail: (error) => resolve(gpsFromLocation(null, error))
    })
  })
}

const gpsForShoot = () => {
  if (cachedGps && (Date.now() - cachedGpsAt) < GPS_CACHE_MS) {
    return {
      ...cachedGps,
      locationCapturedAt: new Date().toISOString()
    }
  }
  return null
}

const prefetchContext = async () => {
  const token = ++prefetchToken
  tick()
  if (!clockText.value) clockText.value = '时间未刷新:tick空'
  locationText.value = '定位中…'
  weatherText.value = '天气等待定位…'
  const location = await locate({ highAccuracy: true })
  if (token !== prefetchToken) return
  const located = location.gpsStatus === 'ok' || location.gpsStatus === 'low_accuracy'
  if (!located) {
    const why = describeGpsFailure(location)
    locationText.value = why
    weatherText.value = `天气未请求:${why}`
    return
  }
  try {
    const place = await apiJson('/api/location/reverse', {
      method: 'POST',
      data: {
        latitude: location.latitude,
        longitude: location.longitude,
        coordinateSystem: 'gcj02',
        accuracyMeters: location.locationAccuracyMeters,
        capturedAt: location.locationCapturedAt
      }
    })
    if (token !== prefetchToken) return
    locationText.value = place.locationText || `逆地理空结果:${location.latitude},${location.longitude}`
  } catch (error) {
    if (token !== prefetchToken) return
    locationText.value = describeApiFailure(error, messageFor(error, '逆地理失败'))
  }
  try {
    const weather = await apiJson('/api/weather/current', {
      method: 'POST',
      data: {
        latitude: location.latitude,
        longitude: location.longitude,
        capturedAt: new Date().toISOString()
      }
    })
    if (token !== prefetchToken) return
    const text = weather.weatherText || ''
    if (!text && (weather.temperatureC == null || weather.temperatureC === '')) {
      weatherText.value = `天气空结果:${JSON.stringify(weather)}`
      return
    }
    weatherText.value = weather.temperatureC == null || weather.temperatureC === ''
      ? text
      : `${text} ${weather.temperatureC}℃`
  } catch (error) {
    if (token !== prefetchToken) return
    weatherText.value = describeApiFailure(error, messageFor(error, '天气失败'))
  }
}

const openNote = () => {
  openProjects.value = false
  openGear.value = false
  noteDraft.value = note.value
  speechPreview.value = null
  voiceHolding.value = false
  voiceCancel.value = false
  noteOpen.value = true
}

const closeNote = () => {
  note.value = String(noteDraft.value || '').trim()
  noteOpen.value = false
  voiceHolding.value = false
  recognizing.value = false
}

const useSpeech = () => {
  if (speechPreview.value == null) return
  const text = String(speechPreview.value || '').trim()
  noteDraft.value = text
  note.value = text
  speechPreview.value = null
  noteOpen.value = false
}

const cancelVoiceHolding = () => {
  voicePressing = false
  clearTimeout(voiceHoldTimer)
  voiceCancelled = true
  voiceHolding.value = false
  voiceCancel.value = false
  if (voiceRecording) {
    voiceRecording = false
    try {
      ensureRecorder().stop()
    } catch (error) {
      recognizing.value = false
    }
  }
}

const closeSpeechModal = () => {
  if (recognizing.value) return
  if (voiceHolding.value || voiceRecording) {
    cancelVoiceHolding()
    return
  }
  speechPreview.value = null
}

const prepareSpeechFile = (tempPath) => new Promise((resolve, reject) => {
  uni.getFileInfo({
    filePath: tempPath,
    success: (info) => {
      if (info.size > 3 * 1024 * 1024) {
        reject(new Error('audio too large'))
        return
      }
      const base = typeof wx !== 'undefined' ? wx.env?.USER_DATA_PATH : ''
      if (!base) {
        resolve(tempPath)
        return
      }
      const dest = `${base}/speech-once.aac`
      uni.getFileSystemManager().copyFile({
        srcPath: tempPath,
        destPath: dest,
        success: () => resolve(dest),
        fail: () => resolve(tempPath)
      })
    },
    fail: () => reject(new Error('speech failed'))
  })
})

const ensureRecorder = () => {
  if (recorder) return recorder
  recorder = uni.getRecorderManager()
  recorder.onError(() => {
    voiceHolding.value = false
    voiceRecording = false
    voiceCancel.value = false
    recognizing.value = false
  })
  recorder.onStop(async (result) => {
    voiceHolding.value = false
    voiceRecording = false
    if (voiceCancelled) {
      voiceCancelled = false
      return
    }
    recognizing.value = true
    let speechFile = ''
    try {
      speechFile = await prepareSpeechFile(result.tempFilePath)
      const text = await uploadSpeech(speechFile)
      speechPreview.value = text
    } catch (error) {
      uni.showToast({ title: messageFor(error, '语音识别失败，可以手动输入'), icon: 'none' })
    } finally {
      if (speechFile && speechFile !== result.tempFilePath) {
        uni.getFileSystemManager().unlink({ filePath: speechFile })
      }
      recognizing.value = false
    }
  })
  return recorder
}

const onVoiceStart = (event) => {
  if (recognizing.value) return
  voicePressing = true
  voiceStartY = event.touches?.[0]?.clientY || 0
  voiceCancelled = false
  voiceCancel.value = false
  clearTimeout(voiceHoldTimer)
  // 只有持续按住超过 220ms 才进入聆听，单击直接忽略且不弹窗
  voiceHoldTimer = setTimeout(() => {
    if (!voicePressing) return
    uni.getSetting({
      success: (setting) => {
        if (!voicePressing) return
        if (setting.authSetting?.['scope.record'] === false) {
          voicePressing = false
          uni.showModal({
            title: '需要录音权限',
            content: '可以改为手动输入。',
            confirmText: '打开设置',
            success: (result) => { if (result.confirm) uni.openSetting({}) }
          })
          return
        }
        voiceStartedAt = Date.now()
        voiceHolding.value = true
        voiceRecording = true
        ensureRecorder().start({ duration: 60000, format: 'aac' })
      }
    })
  }, 220)
}

const onVoiceMove = (event) => {
  if (!voicePressing || !voiceHolding.value) return
  const y = event.touches?.[0]?.clientY || voiceStartY
  voiceCancel.value = voiceStartY - y > 80
}

const onVoiceEnd = () => {
  voicePressing = false
  clearTimeout(voiceHoldTimer)
  if (!voiceHolding.value && !voiceRecording) return
  const elapsed = Date.now() - voiceStartedAt
  voiceCancelled = voiceCancel.value || elapsed < 300
  voiceHolding.value = false
  voiceCancel.value = false
  voiceRecording = false
  try {
    ensureRecorder().stop()
  } catch (error) {
    recognizing.value = false
  }
}

const shoot = async () => {
  if (!ready.value || shooting.value || noteOpen.value) return
  if (!projectId.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  shooting.value = true
  openProjects.value = false
  openGear.value = false
  try {
    const context = uni.createCameraContext('wmCamera')
    const photo = await new Promise((resolve, reject) => {
      context.takePhoto({ quality: 'high', success: resolve, fail: reject })
    })
    await saveCapture(photo.tempImagePath)
  } catch (error) {
    uni.showToast({ title: '拍照失败', icon: 'none' })
  } finally {
    shooting.value = false
  }
}

// 超广角：微信 camera 组件没有超广角镜头，改为调用系统相机（系统相机通常自带 0.5x）
const shootWithSystemCamera = async () => {
  if (shooting.value || noteOpen.value) return
  if (!projectId.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  shooting.value = true
  openProjects.value = false
  openGear.value = false
  // 先卸载小程序 camera 释放硬件占用，避免未拍照返回时画面卡死
  cameraAlive.value = false
  clearTimeout(remountTimer)
  await new Promise((resolve) => setTimeout(resolve, 80))
  let navigated = false
  try {
    const picked = await new Promise((resolve, reject) => {
      uni.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: ['camera'],
        sizeType: ['original'],
        camera: 'back',
        success: resolve,
        fail: reject
      })
    })
    const path = picked?.tempFiles?.[0]?.tempFilePath
    if (!path) throw new Error('no-file')
    await saveCapture(path)
    navigated = true
  } catch (error) {
    if (!/cancel/i.test(String(error?.errMsg || ''))) {
      uni.showToast({ title: '超广角拍照失败', icon: 'none' })
    }
  } finally {
    shooting.value = false
    if (!navigated) remountCamera()
  }
}

const handleAccelerometer = (res) => {
  const x = Number(res?.x) || 0
  const y = Number(res?.y) || 0
  // 使用滞回阈值避免横竖屏临界角抖动
  if (Math.abs(x) > 0.65 && Math.abs(y) < 0.45) {
    deviceAngle.value = x < 0 ? 90 : -90
  } else if (Math.abs(y) > 0.65 && Math.abs(x) < 0.45) {
    deviceAngle.value = 0
  }
}

const startOrientationWatch = () => {
  if (accelListening) return
  accelListening = true
  try {
    uni.onAccelerometerChange(handleAccelerometer)
    uni.startAccelerometer({ interval: 'ui' })
  } catch (error) {
    accelListening = false
  }
}

const stopOrientationWatch = () => {
  if (!accelListening) return
  accelListening = false
  try {
    if (typeof uni.offAccelerometerChange === 'function') {
      uni.offAccelerometerChange(handleAccelerometer)
    }
    uni.stopAccelerometer()
  } catch (error) {
    // ignore
  }
}

const rotateCapturedForLandscape = async (filePath, widthPx, heightPx, angle) => {
  // 只有手机横拍且相机仍输出竖图时，才把图片旋转为正向横图，让预览和成片水印都横向显示
  if (!angle || !widthPx || !heightPx || widthPx >= heightPx) {
    return { path: filePath, widthPx, heightPx }
  }
  const createCanvas = typeof uni.createOffscreenCanvas === 'function'
    ? uni.createOffscreenCanvas
    : (typeof wx !== 'undefined' && typeof wx.createOffscreenCanvas === 'function' ? wx.createOffscreenCanvas : null)
  if (!createCanvas) {
    return { path: filePath, widthPx, heightPx }
  }
  try {
    const maxSide = 2560
    const scale = Math.min(1, maxSide / Math.max(widthPx, heightPx))
    const srcW = Math.max(1, Math.round(widthPx * scale))
    const srcH = Math.max(1, Math.round(heightPx * scale))
    const outW = srcH
    const outH = srcW
    const canvas = createCanvas({ type: '2d', width: outW, height: outH })
    const ctx = canvas.getContext('2d')
    const img = canvas.createImage()
    await new Promise((resolve, reject) => {
      img.onload = resolve
      img.onerror = reject
      img.src = filePath
    })
    ctx.save()
    ctx.translate(outW / 2, outH / 2)
    // 手机左横（angle=90）时图像需逆时针转 90°；右横（angle=-90）时顺时针转 90°
    ctx.rotate(angle === 90 ? -Math.PI / 2 : Math.PI / 2)
    ctx.drawImage(img, -srcW / 2, -srcH / 2, srcW, srcH)
    ctx.restore()
    const exported = await new Promise((resolve, reject) => {
      uni.canvasToTempFilePath({
        canvas,
        fileType: 'jpg',
        quality: 0.92,
        success: resolve,
        fail: reject
      })
    })
    if (exported?.tempFilePath) {
      return { path: exported.tempFilePath, widthPx: outW, heightPx: outH }
    }
  } catch (error) {
    // 离屏旋转失败时回退原图，不阻断拍照
  }
  return { path: filePath, widthPx, heightPx }
}

const saveCapture = async (tempImagePath) => {
  // 用进页已缓存的 GPS，避免快门后再等高精度定位 3–4 秒
  const location = gpsForShoot() || await locate({ highAccuracy: false })
  const captureAngle = deviceAngle.value
  let widthPx = 0
  let heightPx = 0
  try {
    const info = await Promise.race([
      new Promise((resolve, reject) => {
        uni.getImageInfo({ src: tempImagePath, success: resolve, fail: reject })
      }),
      new Promise((resolve) => setTimeout(() => resolve(null), 800))
    ])
    widthPx = info?.width || 0
    heightPx = info?.height || 0
  } catch (error) {
    widthPx = 0
    heightPx = 0
  }
  const normalized = await rotateCapturedForLandscape(tempImagePath, widthPx, heightPx, captureAngle)
  writeCapture({
    projectId: projectId.value,
    path: normalized.path,
    widthPx: normalized.widthPx,
    heightPx: normalized.heightPx,
    takenAt: new Date().toISOString(),
    clientRequestId: createClientRequestId(),
    watermarkPosition: position.value,
    watermarkVisible: watermarkOn.value,
    draftNote: note.value,
    previewLocationText: locationText.value,
    previewWeatherText: weatherText.value,
    ...location
  })
  uni.navigateTo({ url: `/pages/watermark/preview?projectId=${projectId.value}` })
}

const onCameraError = () => {
  promptCameraSetting()
}

onLoad((query) => {
  projectId.value = Number(query.projectId) || readProjectId() || 0
  if (projectId.value) writeProjectId(projectId.value)
  if (query.projectName) {
    try {
      projectName.value = decodeURIComponent(String(query.projectName)) || projectName.value
    } catch (error) {
      projectName.value = String(query.projectName)
    }
  }
  tick()
  loadProject(true)
  prefetchContext()
})

onShow(() => {
  pageVisible = true
  saveOriginal.value = saveOriginalEnabled()
  startOrientationWatch()
  tick()
  clearInterval(clockTimer)
  clockTimer = setInterval(tick, 1000)
  prepare()
  if (!cameraAlive.value && !shooting.value) {
    remountCamera()
  }
  loadProject(false)
  if ((!cachedGps || Date.now() - cachedGpsAt > GPS_CACHE_MS) && !locationDenied) {
    prefetchContext()
  }
})

onHide(() => {
  pageVisible = false
  stopOrientationWatch()
  clearTimeout(remountTimer)
})

onUnload(() => {
  pageVisible = false
  stopOrientationWatch()
  clearInterval(clockTimer)
  clearTimeout(remountTimer)
  clearTimeout(voiceHoldTimer)
})
</script>

<style scoped>
.page { position: relative; height: 100vh; background: #000; overflow: hidden; }
.camera { width: 100%; height: 100%; }
.notice { padding: 220rpx 48rpx 0; color: #fff; }
.title { font-size: 36rpx; font-weight: 700; }
.text { margin-top: 16rpx; font-size: 26rpx; line-height: 1.6; color: #cbd5e1; }
.primary, .confirm { margin-top: 28rpx; background: #7965D8; color: #fff; border-radius: 999rpx; }
.primary::after, .confirm::after { border: none; }
.grid-v, .grid-h, .mark, .topbar, .dock, .picker, .gear-menu { position: fixed; }
.grid-v, .grid-h { z-index: 5; background-color: rgba(255, 255, 255, 0.28); }
.grid-v { top: 0; bottom: 0; width: 1px; }
.grid-v.one { left: 33.33%; }
.grid-v.two { left: 66.66%; }
.grid-h { left: 0; right: 0; height: 1px; }
.grid-h.one { top: 33.33%; }
.grid-h.two { top: 66.66%; }
.mark { z-index: 15; }
.topbar, .dock, .picker, .gear-menu { z-index: 20; }
.topbar { left: 0; right: 0; top: 0; display: flex; align-items: center; gap: 16rpx; padding: 12rpx 24rpx 16rpx; }
.icon-btn, .top-pill { height: 64rpx; border-radius: 999rpx; background: rgba(20, 22, 28, 0.55); color: #fff; font-size: 26rpx; line-height: 64rpx; text-align: center; }
.icon-btn { width: 64rpx; font-size: 40rpx; }
.top-pill { padding: 0 22rpx; }
.flash-pill { display: flex; flex-direction: row; align-items: center; justify-content: center; padding: 0 18rpx; }
.flash-icon { width: 32rpx; height: 32rpx; margin-right: 8rpx; }
.flash-text { color: #fff; font-size: 26rpx; line-height: 64rpx; }
.flip { font-size: 32rpx; }
.mark { border-radius: 14rpx; overflow: hidden; background-color: rgba(176, 178, 184, 0.48); box-sizing: border-box; }
.mark-title { display: flex; flex-direction: row; align-items: center; justify-content: flex-start; height: 36rpx; padding: 0 14rpx; background-color: rgba(120, 122, 128, 0.9); }
.dot { width: 12rpx; height: 12rpx; margin-right: 8rpx; border-radius: 50%; background-color: #F19D2F; }
.mark-name { max-width: 86%; overflow: hidden; color: #fff; font-size: 22rpx; font-weight: 700; line-height: 36rpx; text-align: left; white-space: nowrap; }
.mark-line { position: relative; padding: 0 14rpx; box-sizing: border-box; }
.mark-label { position: absolute; left: 14rpx; top: 0; width: 76rpx; height: 14px; display: flex; flex-direction: row; justify-content: space-between; align-items: center; }
.mark-ch { color: #fff; font-size: 10px; line-height: 14px; }
.mark-colon { position: absolute; left: 90rpx; top: 0; color: #fff; font-size: 10px; line-height: 14px; }
.mark-values { position: absolute; left: 108rpx; right: 8rpx; top: 0; }
.mark-value-line { height: 14px; overflow: hidden; color: #fff; font-size: 10px; line-height: 14px; text-align: left; }
.picker, .gear-menu { left: 24rpx; right: 24rpx; }
.picker-card, .gear-menu { padding: 8rpx 0; border-radius: 20rpx; background: rgba(22, 24, 28, 0.92); }
.picker-row, .gear-item, .picker-empty { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 28rpx; color: #fff; font-size: 28rpx; }
.picker-name { flex: 1; }
.picker-current { color: #C4B5FD; font-size: 24rpx; }
.picker-empty { color: rgba(255, 255, 255, 0.7); }
.dock { left: 0; right: 0; bottom: 0; padding: 8rpx 28rpx 20rpx; background: linear-gradient(180deg, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.55) 28%, rgba(0, 0, 0, 0.72)); }
.zoom-bar { display: flex; flex-direction: row; align-items: center; justify-content: center; margin-bottom: 14rpx; }
.zoom-btn, .zoom-pill { height: 48rpx; margin: 0 6rpx; padding: 0 16rpx; border-radius: 999rpx; background-color: rgba(20, 22, 28, 0.62); color: #fff; font-size: 22rpx; line-height: 48rpx; text-align: center; }
.zoom-btn { width: 48rpx; padding: 0; font-size: 26rpx; }
.zoom-pill.active { background-color: #7965D8; color: #fff; font-weight: 700; }
.zoom-pill.ultra { background-color: rgba(121, 101, 216, 0.85); color: #fff; font-weight: 700; }
.zoom-now { margin: 0 8rpx; color: #F5D76E; font-size: 22rpx; line-height: 48rpx; }
.dock-meta, .shutter-row, .project-chip, .wm-state { display: flex; align-items: center; }
.dock-meta { justify-content: space-between; }
.project-chip { max-width: 62%; height: 64rpx; padding: 0 18rpx; border-radius: 14rpx; background: rgba(22, 24, 28, 0.72); }
.chip-folder { width: 32rpx; height: 32rpx; margin-right: 10rpx; }
.chip-name { max-width: 280rpx; overflow: hidden; color: #fff; font-size: 26rpx; white-space: nowrap; }
.chip-arrow { margin-left: 8rpx; color: #fff; font-size: 22rpx; }
.wm-state { color: #fff; font-size: 26rpx; }
.wm-pos { margin-left: 20rpx; }
.shutter-row { justify-content: space-between; margin-top: 28rpx; padding: 0 36rpx 12rpx; }
.thumb, .gear { width: 84rpx; height: 84rpx; border-radius: 16rpx; background: rgba(255, 255, 255, 0.16); color: #fff; font-size: 40rpx; line-height: 84rpx; text-align: center; overflow: hidden; }
.thumb-image { width: 84rpx; height: 84rpx; }
.shutter { width: 132rpx; height: 132rpx; border: 8rpx solid #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.shutter.busy { opacity: 0.45; }
.shutter-core { width: 100rpx; height: 100rpx; border-radius: 50%; background: #fff; }
.wm-toggle { color: #fff; font-size: 26rpx; }
.note-layer { position: fixed; inset: 0; z-index: 40; display: flex; align-items: flex-end; background: rgba(15, 23, 42, 0.55); }
.note-card { width: 100%; padding: 28rpx 32rpx calc(28rpx + env(safe-area-inset-bottom)); border-radius: 28rpx 28rpx 0 0; background: #fff; box-sizing: border-box; }
.note-head { display: flex; align-items: center; justify-content: space-between; }
.note-title { color: #1A1D23; font-size: 34rpx; font-weight: 700; }
.note-close { color: #6B7280; font-size: 44rpx; line-height: 1; }
.note-input { width: 100%; min-height: 180rpx; margin-top: 20rpx; padding: 20rpx; border-radius: 20rpx; background: #F7F6FB; box-sizing: border-box; font-size: 28rpx; }
.voice-tip { margin-top: 16rpx; color: #8B93A1; font-size: 24rpx; text-align: center; }
.hold-btn { margin-top: 16rpx; height: 96rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 30rpx; font-weight: 700; line-height: 96rpx; text-align: center; }
.hold-btn.holding { background: #7965D8; color: #fff; }
.hold-btn.cancel { background: #FEE2E2; color: #C2410C; }
.speech-modal-mask { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; padding: 40rpx; background: rgba(15, 23, 42, 0.55); box-sizing: border-box; }
.speech-modal { width: 100%; max-width: 560rpx; padding: 32rpx; border-radius: 28rpx; background: #fff; box-sizing: border-box; text-align: center; }
.speech-modal-title { font-size: 32rpx; font-weight: 700; color: #1A1D23; }
.speech-modal-desc { margin-top: 16rpx; font-size: 26rpx; line-height: 1.5; color: #64748b; }
.speech-modal-body { margin-top: 20rpx; padding: 20rpx; border-radius: 18rpx; background: #F7F6FB; color: #1A1D23; font-size: 28rpx; line-height: 1.6; text-align: left; max-height: 320rpx; overflow-y: auto; }
.speech-modal-actions { display: flex; gap: 16rpx; margin-top: 24rpx; }
.speech-btn { flex: 1; height: 74rpx; margin: 0; border-radius: 999rpx; font-size: 26rpx; line-height: 74rpx; }
.speech-btn::after { border: none; }
.speech-btn.cancel { background: #F3F4F6; color: #4B5563; }
.speech-btn.ok { background: #7965D8; color: #fff; }
.speech-text { flex: 1; color: #4B5563; font-size: 26rpx; }
.speech-use { flex-shrink: 0; color: #7965D8; font-size: 26rpx; }
.confirm { width: 100%; height: 88rpx; margin-top: 24rpx; font-size: 30rpx; line-height: 88rpx; }
</style>
