export const PRIVACY_CONSENT_KEY = 'privacy_consent_accepted'
export const USER_PROFILE_KEY = 'user_profile'
export const LOGIN_CODE_KEY = 'login_code'
export const PLUGIN_LOGIN_TOKEN_KEY = 'plugin_login_token'

export const getPrivacyConsent = () => Boolean(uni.getStorageSync(PRIVACY_CONSENT_KEY))

export const setPrivacyConsent = (accepted) => {
  uni.setStorageSync(PRIVACY_CONSENT_KEY, accepted ? '1' : '')
}

export const getUserProfile = () => {
  const profile = uni.getStorageSync(USER_PROFILE_KEY)
  return profile || null
}

export const setUserProfile = (profile) => {
  uni.setStorageSync(USER_PROFILE_KEY, profile)
}

export const setLoginCode = (code) => {
  uni.setStorageSync(LOGIN_CODE_KEY, code || '')
}

export const getPluginLoginToken = () => uni.getStorageSync(PLUGIN_LOGIN_TOKEN_KEY) || ''

export const setPluginLoginToken = (token) => {
  uni.setStorageSync(PLUGIN_LOGIN_TOKEN_KEY, token || '')
}

export const clearPluginLoginToken = () => {
  uni.removeStorageSync(PLUGIN_LOGIN_TOKEN_KEY)
}
