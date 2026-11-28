export const PROJECT_KEY = 'watermark_project_id'
export const SAVE_ORIGINAL_KEY = 'watermark_save_original'
export const AUTO_CONTEXT_KEY = 'watermark_auto_context'
export const CAPTURE_KEY = 'watermark_capture'
export const INVITE_SCENE_KEY = 'pending_project_invite_scene'
export const PENDING_PAGE_KEY = 'pending_page_after_login'

export const readProjectId = () => {
  const value = Number(uni.getStorageSync(PROJECT_KEY))
  return Number.isInteger(value) && value > 0 ? value : 0
}

export const writeProjectId = (projectId) => {
  if (!projectId) {
    uni.removeStorageSync(PROJECT_KEY)
    return
  }
  uni.setStorageSync(PROJECT_KEY, String(projectId))
}

export const readCapture = () => uni.getStorageSync(CAPTURE_KEY) || null

export const writeCapture = (capture) => {
  uni.setStorageSync(CAPTURE_KEY, capture)
}

export const saveOriginalEnabled = () => uni.getStorageSync(SAVE_ORIGINAL_KEY) !== '0'

export const autoContextEnabled = () => uni.getStorageSync(AUTO_CONTEXT_KEY) === '1'
