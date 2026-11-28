<template>
  <view class="page" :class="{ editing: editorOpen }">
    <view class="nav" :style="{ paddingTop: statusBar + 'px' }">
      <view class="back" @click="retake">‹</view>
      <text class="nav-title">水印预览与记录</text>
      <text class="nav-note">原图另存云端</text>
    </view>

    <view class="stage" :class="{ compact: editorOpen }" @click="onStageTap">
      <image v-if="capture.path" class="photo" :src="capture.path" mode="aspectFit" />
      <view v-if="visible" class="preview-mark" :class="position" :style="{ width: markWidthPx + 'px' }">
        <view class="mark-title">
          <view class="dot"></view>
          <text class="mark-name">{{ project?.name || '未命名项目' }}</text>
        </view>
        <view class="mark-line">
          <view class="mark-label">
            <text class="mark-ch">备</text><text class="mark-ch">注</text><text class="mark-ch">内</text><text class="mark-ch">容</text>
          </view>
          <text class="mark-colon">：</text>
          <text class="mark-value">{{ note || '（空记录）' }}</text>
        </view>
        <view class="mark-line">
          <view class="mark-label">
            <text class="mark-ch">拍</text><text class="mark-ch">摄</text><text class="mark-ch">时</text><text class="mark-ch">间</text>
          </view>
          <text class="mark-colon">：</text>
          <text class="mark-value">{{ clockDisplay }}</text>
        </view>
        <view class="mark-line">
          <view class="mark-label">
            <text class="mark-ch">地</text><text class="mark-ch">点</text>
          </view>
          <text class="mark-colon">：</text>
          <text class="mark-value">{{ locationDisplay }}</text>
        </view>
        <view class="mark-line">
          <view class="mark-label">
            <text class="mark-ch">天</text><text class="mark-ch">气</text>
          </view>
          <text class="mark-colon">：</text>
          <text class="mark-value">{{ weatherDisplay }}</text>
        </view>
      </view>
    </view>

    <view
      v-if="editorOpen"
      class="editor"
      :style="{ transform: `translateY(${editorDrag}px)` }"
      @click.stop=""
    >
      <view
        class="editor-handle-hit"
        @touchstart="onEditorTouchStart"
        @touchmove.prevent="onEditorTouchMove"
        @touchend="onEditorTouchEnd"
        @touchcancel="onEditorTouchEnd"
      >
        <view class="editor-handle"></view>
      </view>
      <view class="field-label">记录内容（选填）</view>
      <input v-model="note" class="note" maxlength="500" placeholder="选填，拍摄后可补充" />
      <view class="voice-tip">{{ voiceTip }}</view>
      <view
        class="hold-btn"
        :class="{ holding: voiceHolding, cancel: voiceCancel }"
        @touchstart.prevent="onVoiceStart"
        @touchmove.prevent="onVoiceMove"
        @touchend.prevent="onVoiceEnd"
        @touchcancel.prevent="onVoiceEnd"
      >按住说话</view>
      <view class="switch-row">
        <view class="switch-copy">
          <text class="switch-title">显示照片水印</text>
          <text class="hint">隐藏后仍保留记录数据</text>
        </view>
        <view class="mini-switch" :class="{ on: visible }" @click="visible = !visible">
          <view class="mini-switch-knob"></view>
        </view>
      </view>
    </view>

    <view v-if="voiceHolding || recognizing || speechText !== null" class="speech-modal-mask" @click="closeSpeechModal">
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
          <view class="speech-modal-body">{{ speechText || '（没有识别到文字）' }}</view>
          <view class="speech-modal-actions">
            <button class="speech-btn cancel" hover-class="none" @click="closeSpeechModal">取消</button>
            <button class="speech-btn ok" hover-class="none" :disabled="!speechText" @click="confirmSpeech">确认填入</button>
          </view>
        </template>
      </view>
    </view>

    <view class="actions">
      <button class="retake" hover-class="none" @click="retake">{{ capture.source === 'import' ? '返回' : '重新拍摄' }}</button>
      <button class="ghost" hover-class="none" @click="openEditor">水印信息编辑</button>
      <button class="primary" hover-class="none" :loading="saving" :disabled="saving" @click="save">保存照片</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { apiJson, readPhotoExif, uploadOriginal, uploadSpeech } from '../../utils/project-api'
import { buildCreatePhotoBody, createSubmitGuard, describeSaveFailure, messageFor } from '../../utils/photo-payload'
import { formatClock, NO_LOCATION, NO_WEATHER } from '../../utils/watermark-layout'
import { CAPTURE_KEY, readCapture, saveOriginalEnabled, writeCapture } from '../../utils/watermark-session'

const system = uni.getSystemInfoSync()
const statusBar = ref(system.statusBarHeight || 20)
const markWidthPx = Math.floor((system.windowWidth || 375) * 0.48)
const capture = ref({})
const project = ref(null)
const note = ref('')
const position = ref('bottom_left')
const visible = ref(true)
const editorOpen = ref(false)
const editorDrag = ref(0)
const locationText = ref('')
const weatherText = ref('')
const weatherStatus = ref('')
const temperatureC = ref(null)
const weatherObservedAt = ref('')
const speechText = ref(null)
const recognizing = ref(false)
const voiceHolding = ref(false)
const voiceCancel = ref(false)
const saving = ref(false)
const submitGuard = createSubmitGuard()
let recorder = null
let dragStartY = 0
let dragging = false
let voiceStartY = 0
let voiceCancelled = false
let voicePressing = false
let voiceRecording = false
let voiceHoldTimer = 0
let voiceStartedAt = 0
let exifPending = null

const voiceTip = computed(() => {
  if (recognizing.value) return '正在识别…'
  if (voiceHolding.value && voiceCancel.value) return '松开取消'
  if (voiceHolding.value) return '松手结束，上滑取消'
  return '按住说话，松手识别；上滑取消'
})
const holdLabel = computed(() => {
  if (recognizing.value) return '识别中'
  if (voiceHolding.value && voiceCancel.value) return '松开取消'
  if (voiceHolding.value) return '松开结束'
  return '按住 说话'
})

const clockDisplay = computed(() => {
  const clock = formatClock(capture.value.takenAt)
  return clock ? clock.replace(/-/g, '.') : ''
})

const locationDisplay = computed(() => {
  if (locationText.value) return locationText.value
  const preview = capture.value.previewLocationText
  if (preview && preview !== '定位中…' && preview !== '天气获取中…') return preview
  return NO_LOCATION
})

const weatherDisplay = computed(() => {
  if (weatherStatus.value === 'ok' && weatherText.value) {
    return temperatureC.value == null || temperatureC.value === ''
      ? weatherText.value
      : `${weatherText.value} ${temperatureC.value}℃`
  }
  const preview = capture.value.previewWeatherText
  if (preview && preview !== '天气获取中…' && preview !== '定位中…') return preview
  return NO_WEATHER
})

const looksLikeApiError = (text) => {
  const value = String(text || '')
  if (!value) return true
  if (value === '定位中…' || value === '天气获取中…') return true
  if (value.startsWith('天气等待') || value.startsWith('天气未请求') || value.startsWith('天气空')) return true
  return /HTTP|失败|异常|配额|非法|未配置|未开通|认证/.test(value)
}

const loadPreview = async () => {
  const located = capture.value.gpsStatus === 'ok' || capture.value.gpsStatus === 'low_accuracy'
  if (!located) return
  // 拍照页已有可用结果时不再重复请求，避免腾讯日配额被预览页再打一次。
  if (!looksLikeApiError(capture.value.previewLocationText)) {
    locationText.value = capture.value.previewLocationText
  } else {
    try {
      const place = await apiJson('/api/location/reverse', {
        method: 'POST',
        data: {
          latitude: capture.value.latitude,
          longitude: capture.value.longitude,
          coordinateSystem: 'gcj02',
          accuracyMeters: capture.value.locationAccuracyMeters,
          capturedAt: capture.value.locationCapturedAt
        }
      })
      locationText.value = place.locationText || ''
    } catch (error) {
      locationText.value = ''
    }
  }
  const previewWeather = String(capture.value.previewWeatherText || '')
  if (!looksLikeApiError(previewWeather)) {
    const match = previewWeather.match(/(-?\d+(?:\.\d+)?)\s*℃/)
    weatherText.value = previewWeather.replace(/\s*-?\d+(?:\.\d+)?\s*℃$/, '').trim() || previewWeather
    weatherStatus.value = 'ok'
    temperatureC.value = match ? Number(match[1]) : null
    return
  }
  try {
    const weather = await apiJson('/api/weather/current', {
      method: 'POST',
      data: {
        latitude: capture.value.latitude,
        longitude: capture.value.longitude,
        capturedAt: capture.value.takenAt
      }
    })
    weatherText.value = weather.weatherText || ''
    weatherStatus.value = 'ok'
    temperatureC.value = weather.temperatureC
    weatherObservedAt.value = weather.observedAt || ''
  } catch (error) {
    weatherText.value = ''
    weatherStatus.value = 'failed'
    temperatureC.value = null
    weatherObservedAt.value = ''
  }
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

const confirmSpeech = () => {
  if (speechText.value == null) return
  note.value = String(speechText.value || '').trim()
  speechText.value = null
  editorOpen.value = false
  editorDrag.value = 0
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
  speechText.value = null
}

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
      speechText.value = await uploadSpeech(speechFile)
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

const openEditor = () => {
  editorDrag.value = 0
  speechText.value = null
  voiceHolding.value = false
  voiceCancel.value = false
  editorOpen.value = true
}

const closeEditor = () => {
  editorOpen.value = false
  editorDrag.value = 0
  voiceHolding.value = false
}

const onStageTap = () => {
  if (editorOpen.value) closeEditor()
}

const onEditorTouchStart = (event) => {
  dragStartY = event.touches?.[0]?.clientY || 0
  dragging = true
}

const onEditorTouchMove = (event) => {
  if (!dragging) return
  const y = event.touches?.[0]?.clientY || dragStartY
  editorDrag.value = Math.max(0, y - dragStartY)
}

const onEditorTouchEnd = () => {
  if (!dragging) return
  dragging = false
  if (editorDrag.value > 72) {
    closeEditor()
    return
  }
  editorDrag.value = 0
}

const retake = () => {
  uni.navigateBack({
    fail: () => uni.redirectTo({ url: `/pages/watermark/camera?projectId=${capture.value.projectId || ''}` })
  })
}

const saveLocalOriginal = () => {
  if (capture.value.source === 'import' || !saveOriginalEnabled()) return
  uni.saveImageToPhotosAlbum({
    filePath: capture.value.path,
    fail: () => uni.showToast({ title: '原图未保存到相册，项目照片不受影响', icon: 'none' })
  })
}

const loadImportExif = async () => {
  if (capture.value.source !== 'import' || !capture.value.path) return
  try {
    const info = await readPhotoExif(capture.value.path)
    if (info && info.takenAt) {
      capture.value = { ...capture.value, takenAt: info.takenAt }
      writeCapture(capture.value)
    }
  } catch (error) {
    // 没读到 EXIF 时保留默认时间
  }
}

const save = async () => {
  if (!submitGuard.tryBegin()) return
  saving.value = true
  if (exifPending) {
    try { await exifPending } catch (error) {}
  }
  if (!capture.value.path || !capture.value.projectId || !capture.value.clientRequestId) {
    uni.showToast({ title: '没有找到刚拍摄的原图', icon: 'none' })
    saving.value = false
    submitGuard.finish()
    return
  }
  if (project.value?.status === 'archived') {
    uni.showToast({ title: '项目已归档，只能查看', icon: 'none' })
    saving.value = false
    submitGuard.finish()
    return
  }
  let imageId = capture.value.imageId
  try {
    if (!imageId) {
      const uploaded = await uploadOriginal(capture.value.path)
      imageId = uploaded.imageId
      capture.value = { ...capture.value, imageId, draftNote: note.value }
      writeCapture(capture.value)
    }
    const body = buildCreatePhotoBody({
      ...capture.value,
      imageId,
      note: note.value,
      watermarkPosition: position.value,
      watermarkVisible: visible.value
    })
    await apiJson(`/api/project/${capture.value.projectId}/photos`, { method: 'POST', data: body })
    saveLocalOriginal()
    uni.removeStorageSync(CAPTURE_KEY)
    uni.showToast({ title: '已保存', icon: 'success', duration: 900 })
    setTimeout(() => {
      uni.navigateBack({
        fail: () => uni.redirectTo({ url: `/pages/watermark/camera?projectId=${capture.value.projectId || ''}` })
      })
    }, 350)
  } catch (error) {
    capture.value = { ...capture.value, draftNote: note.value }
    writeCapture(capture.value)
    const content = describeSaveFailure({ uploaded: Boolean(imageId), error })
    uni.showModal({ title: imageId ? '还没有进入项目' : '上传失败', content, showCancel: false })
  } finally {
    saving.value = false
    submitGuard.finish()
  }
}

onLoad(async (query) => {
  capture.value = readCapture() || {}
  if (!capture.value.path || Number(query.projectId) !== Number(capture.value.projectId)) {
    uni.showToast({ title: '没有找到刚拍摄的原图', icon: 'none' })
    return
  }
  if (typeof capture.value.draftNote === 'string') note.value = capture.value.draftNote
  position.value = capture.value.watermarkPosition || 'bottom_left'
  visible.value = typeof capture.value.watermarkVisible === 'boolean' ? capture.value.watermarkVisible : true
  try {
    project.value = await apiJson(`/api/project/${capture.value.projectId}`)
    position.value = capture.value.watermarkPosition || project.value.defaultWatermarkPosition || 'bottom_left'
    visible.value = typeof capture.value.watermarkVisible === 'boolean'
      ? capture.value.watermarkVisible
      : project.value.watermarkEnabled !== false
  } catch (error) {
    uni.showToast({ title: messageFor(error, '项目读取失败'), icon: 'none' })
  }
  if (capture.value.source === 'import') {
    exifPending = loadImportExif()
  }
  await loadPreview()
})

watch(note, (value) => {
  if (!capture.value.path) return
  capture.value = { ...capture.value, draftNote: value }
  writeCapture(capture.value)
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100vh; background: #fff; box-sizing: border-box; position: relative; }
.nav { flex-shrink: 0; display: flex; align-items: center; height: 88rpx; padding-right: 28rpx; padding-left: 8rpx; box-sizing: content-box; }
.back { width: 72rpx; color: #1A1D23; font-size: 52rpx; line-height: 88rpx; text-align: center; }
.nav-title { flex: 1; color: #1A1D23; font-size: 34rpx; font-weight: 700; text-align: center; }
.nav-note { width: 150rpx; color: #8B93A1; font-size: 22rpx; text-align: right; }
.stage { position: relative; flex: 1; min-height: 0; background: #000; overflow: hidden; }
.stage.compact { flex: 1 1 auto; min-height: 280rpx; }
.photo { width: 100%; height: 100%; background: #111; }
.preview-mark {
  position: absolute;
  z-index: 2;
  height: auto;
  border-radius: 14rpx;
  overflow: hidden;
  background: rgba(176, 178, 184, 0.48);
  box-sizing: border-box;
}
.preview-mark.bottom_left { left: 24rpx; bottom: 24rpx; }
.preview-mark.bottom_right { right: 24rpx; bottom: 24rpx; }
.preview-mark.top_left { left: 24rpx; top: 24rpx; }
.preview-mark.top_right { right: 24rpx; top: 24rpx; }
.mark-title { display: flex; align-items: center; justify-content: flex-start; height: 38rpx; padding: 0 14rpx; background: rgba(120, 122, 128, 0.9); }
.dot { width: 12rpx; height: 12rpx; margin-right: 8rpx; border-radius: 50%; background: #F19D2F; flex-shrink: 0; }
.mark-name { max-width: 86%; overflow: hidden; color: #fff; font-size: 22rpx; font-weight: 700; line-height: 38rpx; text-align: left; white-space: nowrap; }
.mark-line { display: flex; align-items: flex-start; min-height: 28rpx; height: auto; padding: 0 14rpx; box-sizing: border-box; }
.mark-label { width: 76rpx; height: 28rpx; display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
.mark-ch { color: #fff; font-size: 20rpx; line-height: 28rpx; }
.mark-colon { color: #fff; font-size: 20rpx; line-height: 28rpx; flex-shrink: 0; }
.mark-value { flex: 1; min-width: 0; color: #fff; font-size: 20rpx; line-height: 28rpx; text-align: left; white-space: normal; word-break: break-all; }
.editor {
  flex-shrink: 0;
  padding: 8rpx 28rpx 16rpx;
  border-radius: 24rpx 24rpx 0 0;
  background: #F7F6FB;
  box-shadow: 0 -8rpx 24rpx rgba(15, 23, 42, 0.08);
  transition: transform 0.12s ease-out;
}
.editor-handle-hit { padding: 8rpx 0 12rpx; }
.editor-handle { width: 56rpx; height: 6rpx; margin: 0 auto; border-radius: 999rpx; background: #D5D8E0; }
.field-label { color: #1A1D23; font-size: 24rpx; }
.note { width: 100%; height: 64rpx; margin-top: 10rpx; padding: 0 22rpx; border-radius: 999rpx; background: #fff; box-sizing: border-box; font-size: 26rpx; }
.voice-tip { margin-top: 12rpx; color: #8B93A1; font-size: 22rpx; text-align: center; }
.hold-btn { margin-top: 10rpx; height: 80rpx; border-radius: 999rpx; background: #F3F0FF; color: #7965D8; font-size: 28rpx; font-weight: 700; line-height: 80rpx; text-align: center; }
.hold-btn.holding { background: #7965D8; color: #fff; }
.hold-btn.cancel { background: #FEE2E2; color: #C2410C; }
.speech { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-top: 10rpx; padding: 12rpx 16rpx; border-radius: 14rpx; background: #fff; color: #4B5563; font-size: 22rpx; }
.speech-text { flex: 1; min-width: 0; }
.speech-use { flex-shrink: 0; color: #7965D8; }
.switch-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 8rpx; padding-top: 8rpx; border-top: 1rpx solid #E6E8EF; }
.switch-copy { flex: 1; min-width: 0; }
.switch-title { display: block; color: #1A1D23; font-size: 26rpx; }
.hint { display: block; margin-top: 2rpx; color: #8B93A1; font-size: 22rpx; line-height: 1.3; }
.mini-switch { position: relative; width: 76rpx; height: 36rpx; flex-shrink: 0; border-radius: 999rpx; background: #D8DCE6; transition: background-color 0.2s ease; }
.mini-switch.on { background: #7965D8; }
.mini-switch-knob { position: absolute; left: 4rpx; top: 4rpx; width: 28rpx; height: 28rpx; border-radius: 50%; background: #fff; box-shadow: 0 2rpx 6rpx rgba(15, 23, 42, 0.16); transition: transform 0.2s ease; }
.mini-switch.on .mini-switch-knob { transform: translateX(40rpx); }
.speech-modal-mask { position: fixed; inset: 0; z-index: 40; display: flex; align-items: center; justify-content: center; padding: 40rpx; background: rgba(15, 23, 42, 0.52); box-sizing: border-box; }
.speech-modal { width: 100%; max-width: 560rpx; padding: 32rpx; border-radius: 28rpx; background: #fff; box-sizing: border-box; text-align: center; }
.speech-modal-title { font-size: 32rpx; font-weight: 700; color: #1A1D23; }
.speech-modal-desc { margin-top: 16rpx; font-size: 26rpx; line-height: 1.5; color: #64748b; }
.speech-modal-body { margin-top: 20rpx; padding: 20rpx; border-radius: 18rpx; background: #F7F6FB; color: #1A1D23; font-size: 28rpx; line-height: 1.6; text-align: left; max-height: 320rpx; overflow-y: auto; }
.speech-modal-actions { display: flex; gap: 16rpx; margin-top: 24rpx; }
.speech-btn { flex: 1; height: 74rpx; margin: 0; border-radius: 999rpx; font-size: 26rpx; line-height: 74rpx; }
.speech-btn::after { border: none; }
.speech-btn.cancel { background: #F3F4F6; color: #4B5563; }
.speech-btn.ok { background: #7965D8; color: #fff; }
.retake, .ghost, .primary { margin: 0; border-radius: 999rpx; }
.retake::after, .ghost::after, .primary::after { border: none; }
.actions { flex-shrink: 0; display: flex; gap: 12rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: #fff; }
.retake, .ghost, .primary { flex: 1; height: 80rpx; font-size: 26rpx; line-height: 80rpx; }
.retake { background: #fff; color: #3D4654; border: 2rpx solid #E6E8EF; }
.ghost { background: #F3F0FF; color: #7965D8; }
.primary { background: #7965D8; color: #fff; }
</style>
