import baseUrl from './base-url'
import { getSessionToken } from './auth'
import { imageIdFromUpload } from './photo-payload'
import { request, uploadFile } from './request'

export class ApiError extends Error {
  constructor(statusCode, message) {
    super(message || '请求失败')
    this.statusCode = statusCode || 0
  }
}

const parseBody = (data) => {
  if (data && typeof data === 'object') return data
  if (typeof data !== 'string' || !data) return {}
  try {
    return JSON.parse(data)
  } catch (error) {
    return {}
  }
}

export const apiJson = async (path, options = {}) => {
  const response = await request({
    url: `${baseUrl}${path}`,
    method: options.method || 'GET',
    data: options.data
  })
  const body = parseBody(response.data)
  if (response.statusCode >= 200 && response.statusCode < 300) return body
  throw new ApiError(response.statusCode, body.message || '请求失败')
}

export const uploadOriginal = (filePath) => new Promise((resolve, reject) => {
  uploadFile({
    // origin=project：水印相机原图只属于项目照片，不进入收藏列表
    url: `${baseUrl}/api/image/upload?origin=project`,
    filePath,
    name: 'file',
    formData: { tags: '' },
    success: (response) => {
      const body = parseBody(response.data)
      if (response.statusCode !== 200) {
        reject(new ApiError(response.statusCode, body.message || '原图上传失败'))
        return
      }
      try {
        resolve({ imageId: imageIdFromUpload(body) })
      } catch (error) {
        reject(new ApiError(response.statusCode, error.message || '原图上传失败'))
      }
    },
    fail: () => reject(new ApiError(0, '网络异常'))
  })
})

export const uploadSpeech = (filePath) => new Promise((resolve, reject) => {
  uploadFile({
    url: `${baseUrl}/api/speech/recognize`,
    filePath,
    name: 'file',
    success: (response) => {
      const body = parseBody(response.data)
      if (response.statusCode !== 200 || !Object.prototype.hasOwnProperty.call(body, 'text')) {
        reject(new ApiError(response.statusCode, body.message || 'speech failed'))
        return
      }
      resolve(body.text == null ? '' : String(body.text))
    },
    fail: () => reject(new ApiError(0, 'speech failed'))
  })
})

export const readPhotoExif = (filePath) => new Promise((resolve) => {
  uploadFile({
    url: `${baseUrl}/api/photo/exif`,
    filePath,
    name: 'file',
    success: (response) => {
      const body = parseBody(response.data)
      if (response.statusCode === 200 && body && typeof body.takenAt === 'string' && body.takenAt) {
        resolve({ takenAt: body.takenAt, hasExif: true })
        return
      }
      resolve({ takenAt: '', hasExif: false })
    },
    fail: () => resolve({ takenAt: '', hasExif: false })
  })
})

export const downloadAuthorized = (path) => new Promise((resolve, reject) => {
  uni.downloadFile({
    url: `${baseUrl}${path}`,
    header: { Authorization: `Bearer ${getSessionToken()}` },
    success: (response) => {
      if (response.statusCode === 200 && response.tempFilePath) {
        resolve(response.tempFilePath)
        return
      }
      reject(new ApiError(response.statusCode, '图片下载失败'))
    },
    fail: () => reject(new ApiError(0, '网络异常'))
  })
})
