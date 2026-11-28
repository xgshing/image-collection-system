import baseUrl from './base-url'

const isAbsoluteUrl = (value) => /^https?:\/\//i.test(value || '')

const resolveImageUrl = (value) => {
  if (!value) {
    return ''
  }
  let url = isAbsoluteUrl(value) ? value : `${baseUrl}${value}`
  // 后端本地配置曾返回 127.0.0.1，手机上不可达，改写到当前接口地址
  url = url.replace(/^https?:\/\/(127\.0\.0\.1|localhost)(?::\d+)?/i, baseUrl)
  return url
}

export default resolveImageUrl
