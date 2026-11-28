import { getSessionToken } from './auth'
import resolveImageUrl from './image-url'

const cache = Object.create(null)

/**
 * 真机上 <image> 不再支持 http:// 图床地址。
 * 本地调试时先 downloadFile 成临时路径再显示。
 */
export const toDisplayImageSrc = (value) => {
  const url = resolveImageUrl(value)
  if (!url) return Promise.resolve('')
  if (!/^http:\/\//i.test(url)) return Promise.resolve(url)
  if (cache[url]) return Promise.resolve(cache[url])
  return new Promise((resolve) => {
    const token = getSessionToken()
    uni.downloadFile({
      url,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if (res.statusCode === 200 && res.tempFilePath) {
          cache[url] = res.tempFilePath
          resolve(res.tempFilePath)
          return
        }
        resolve('')
      },
      fail: () => resolve('')
    })
  })
}
