const DEV_BASE_URL = 'http://139.199.19.101'
const PROD_BASE_URL = 'http://139.199.19.101'

const explicitBaseUrl = (typeof process !== 'undefined' && process.env && process.env.UNI_APP_BASE_URL)
  ? process.env.UNI_APP_BASE_URL
  : ''

const baseUrl = explicitBaseUrl || (process.env.NODE_ENV === 'development' ? DEV_BASE_URL : PROD_BASE_URL)

export default baseUrl
