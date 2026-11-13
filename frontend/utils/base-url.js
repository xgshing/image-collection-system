const DEV_BASE_URL = 'http://127.0.0.1:8080'
const PROD_BASE_URL = 'https://api.yuntuxia.top'

const explicitBaseUrl = (typeof process !== 'undefined' && process.env && process.env.UNI_APP_BASE_URL)
  ? process.env.UNI_APP_BASE_URL
  : ''

const baseUrl = explicitBaseUrl || (process.env.NODE_ENV === 'development' ? DEV_BASE_URL : PROD_BASE_URL)

export default baseUrl
