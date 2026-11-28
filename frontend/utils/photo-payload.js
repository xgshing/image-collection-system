/**
 * 创建项目照片的请求体。只提交 GPS 和用户确认过的记录，不提交预览得到的地点或天气。
 */

const LOCATED = new Set(['ok', 'low_accuracy'])
export const LOW_ACCURACY_METERS = 100

export const gpsFromLocation = (position, error) => {
  const latitude = Number(position?.latitude)
  const longitude = Number(position?.longitude)
  if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
    const accuracy = Number(position?.accuracy)
    const knownAccuracy = Number.isFinite(accuracy) ? accuracy : null
    return {
      gpsStatus: knownAccuracy != null && knownAccuracy > LOW_ACCURACY_METERS ? 'low_accuracy' : 'ok',
      latitude,
      longitude,
      locationAccuracyMeters: knownAccuracy,
      locationCapturedAt: new Date().toISOString(),
      gpsError: ''
    }
  }
  const message = String(error?.errMsg || error?.message || error?.reason || '')
  let gpsStatus = 'empty'
  if (/timeout/i.test(message)) gpsStatus = 'timeout'
  else if (/auth deny|auth denied|authorize|privacy permission/i.test(message)) gpsStatus = 'denied'
  else if (/LOCATIONSWITCHOFF|system permission|location service|ERROR_NOCELL|ERROR_SERVER_NOTLOCATION/i.test(message)) gpsStatus = 'system_off'
  return {
    gpsStatus,
    latitude: null,
    longitude: null,
    locationAccuracyMeters: null,
    locationCapturedAt: null,
    gpsError: message || gpsStatus
  }
}

/** 把定位失败写成水印上可读的原因，避免只显示「无法获取」。 */
export const describeGpsFailure = (gps) => {
  const raw = String(gps?.gpsError || '').trim()
  const status = gps?.gpsStatus || 'empty'
  if (status === 'denied') return raw ? `定位权限被拒:${raw}` : '定位权限被拒，请在设置中开启'
  if (status === 'timeout') return raw ? `定位超时:${raw}` : '定位超时'
  if (status === 'system_off') return raw ? `系统定位关闭:${raw}` : '系统定位未开启'
  if (raw) return `定位失败:${raw}`
  return `定位失败:${status}`
}

/** 把接口失败写成水印上可读的原因（含状态码与原文）。 */
export const describeApiFailure = (error, fallback = '请求失败') => {
  const status = Number(error?.statusCode) || 0
  const raw = String(error?.message || error?.errMsg || '').trim()
  if (status === 0 && (/网络|fail|timeout/i.test(raw) || !raw)) {
    return raw ? `网络异常:${raw}` : '网络异常:后端连不上'
  }
  if (status) return raw ? `HTTP${status}:${raw}` : `HTTP${status}:${fallback}`
  return raw || fallback
}

export const imageIdFromUpload = (body) => {
  const imageId = Number(body?.imageId)
  if (!Number.isInteger(imageId) || imageId <= 0 || String(body?.imageId) !== String(imageId)) {
    throw new Error('image not found')
  }
  return imageId
}

export const describeSaveFailure = ({ uploaded, error }) => {
  const detail = messageFor(error, '')
  if (uploaded) {
    return detail
      ? `原图已上传，但还没有关联到项目。${detail}`
      : '原图已上传，但还没有关联到项目。可以再次保存，不会重新上传。'
  }
  return detail && detail !== '操作失败'
    ? `原图上传失败，照片还没有进入项目。${detail}`
    : '原图上传失败，照片还没有进入项目。'
}

export const createSubmitGuard = () => {
  let pending = false
  return {
    tryBegin() {
      if (pending) return false
      pending = true
      return true
    },
    finish() {
      pending = false
    }
  }
}

export const buildCreatePhotoBody = (capture = {}) => {
  const located = LOCATED.has(capture.gpsStatus)
  const body = {
    imageId: capture.imageId,
    clientRequestId: capture.clientRequestId,
    takenAt: capture.takenAt,
    timezone: 'Asia/Shanghai',
    note: capture.note == null ? '' : String(capture.note),
    gpsStatus: capture.gpsStatus || 'empty',
    watermarkPosition: capture.watermarkPosition,
    watermarkVisible: capture.watermarkVisible !== false,
    widthPx: capture.widthPx,
    heightPx: capture.heightPx
  }
  if (located) {
    body.latitude = capture.latitude
    body.longitude = capture.longitude
    body.coordinateSystem = 'gcj02'
    body.locationAccuracyMeters = capture.locationAccuracyMeters
    body.locationCapturedAt = capture.locationCapturedAt
  }
  Object.keys(body).forEach((key) => {
    if (body[key] === undefined || body[key] === null) delete body[key]
  })
  return body
}

export const buildPhotoPatch = (change = {}) => {
  const body = {}
  if (Object.prototype.hasOwnProperty.call(change, 'note')) body.note = change.note == null ? '' : String(change.note)
  if (change.watermarkPosition) body.watermarkPosition = change.watermarkPosition
  if (Object.prototype.hasOwnProperty.call(change, 'watermarkVisible')) body.watermarkVisible = Boolean(change.watermarkVisible)
  return body
}

const MESSAGES = {
  'not a project member': '你已不在这个项目中',
  'project not found': '找不到这个项目',
  'photo not found': '找不到这张照片',
  'archived project is read only': '项目已归档，只能查看',
  'project name is required': '请输入项目名称',
  'only owner can delete project': '只有管理员可以删除项目',
  'only owner can rename other members': '只有管理员可以修改其它成员的名字',
  'member name is required': '请输入名字',
  'only archived project can be purged': '只能彻底删除回收站中的项目',
  'image already belongs to another project': '这张图片已经属于其他项目',
  'image not found': '找不到可关联的原图',
  'image is used by a project photo': '这张图片已被项目照片使用，不能删除',
  'only photographer can edit photo': '只有拍摄人可以修改',
  'client snapshot not allowed': '地点和天气由服务端生成，不能由手机提交',
  'field not editable': '这个内容不能修改',
  'gps payload invalid': '定位数据不符合要求',
  'geocode failed': '无法获取定位',
  'weather failed': '无法获取天气',
  'rate limited': '请求过于频繁，请稍后再试',
  'tencent lbs is not configured': '未配置腾讯位置服务 Key',
  'qweather is not configured': '未配置和风天气 JWT',
  'tencent speech is not configured': '语音识别暂不可用，可以手动输入',
  'speech failed': '语音识别失败，可以手动输入',
  'audio too large': '录音超过大小或时长限制',
  'watermark render failed': '带水印图片生成失败',
  'owner must transfer before leaving': '请先转让负责人，再退出',
  'target is not a project member': '对方已不在项目中',
  'invite not found': '邀请不存在或已失效',
  'invite revoked': '邀请已撤销',
  'invite expired': '邀请已过期',
  'invite already accepted': '你已经接受过这个邀请',
  unauthorized: '请先登录'
}

export const messageFor = (error, fallback = '操作失败') => {
  const raw = error?.message || error?.errMsg || ''
  if (error?.statusCode === 401) return MESSAGES.unauthorized
  return MESSAGES[raw] || raw || fallback
}

export const createClientRequestId = () => {
  const time = Date.now().toString(36)
  const random = Math.random().toString(36).slice(2, 10)
  return `cap${time}${random}`.slice(0, 64)
}
