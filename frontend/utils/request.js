import { getSessionToken } from './auth'

const buildAuthHeader = () => {
  const token = getSessionToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const request = (options = {}) => {
  const {
    auth = true,
    header = {},
    success,
    fail,
    complete,
    ...rest
  } = options
  const mergedHeader = auth
    ? { ...buildAuthHeader(), ...header }
    : header

  return new Promise((resolve, reject) => {
    uni.request({
      ...rest,
      header: mergedHeader,
      success: (res) => {
        if (typeof success === 'function') {
          success(res)
        }
        resolve(res)
      },
      fail: (err) => {
        if (typeof fail === 'function') {
          fail(err)
        }
        reject(err)
      },
      complete
    })
  })
}

export const uploadFile = (options = {}) => {
  const {
    auth = true,
    header = {},
    success,
    fail,
    complete,
    ...rest
  } = options
  const mergedHeader = auth
    ? { ...buildAuthHeader(), ...header }
    : header

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      ...rest,
      header: mergedHeader,
      success: (res) => {
        if (typeof success === 'function') {
          success(res)
        }
        resolve(res)
      },
      fail: (err) => {
        if (typeof fail === 'function') {
          fail(err)
        }
        reject(err)
      },
      complete
    })
  })
}
