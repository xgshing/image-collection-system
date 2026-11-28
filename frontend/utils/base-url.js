const DEV_BASE_URL = 'http://192.168.43.148:8080'
const PROD_BASE_URL = 'https://api.yuntuxia.top'

// 只有「开发版」（开发者工具、预览、真机调试）才允许连本机后端；
// 体验版（trial）和正式版（release）一律使用线上域名，不再需要手动改开关。
// 开发时不想连本机后端，可以把它改成 false。
const USE_LOCAL_BACKEND = true

const explicitBaseUrl = (typeof process !== 'undefined' && process.env && process.env.UNI_APP_BASE_URL)
  ? process.env.UNI_APP_BASE_URL
  : ''

// 运行时读取小程序版本：develop / trial / release。读不到时按线上处理，避免误连本机地址。
const readEnvVersion = () => {
  try {
    if (typeof uni !== 'undefined' && typeof uni.getAccountInfoSync === 'function') {
      return uni.getAccountInfoSync()?.miniProgram?.envVersion || ''
    }
  } catch (error) {
    return ''
  }
  return ''
}

const useLocalBackend = USE_LOCAL_BACKEND && readEnvVersion() === 'develop'

const baseUrl = explicitBaseUrl || (useLocalBackend ? DEV_BASE_URL : PROD_BASE_URL)

export default baseUrl
