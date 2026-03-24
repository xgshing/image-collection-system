import baseUrl from './base-url'

const isAbsoluteUrl = (value) => /^https?:\/\//i.test(value || '')

const resolveImageUrl = (value) => {
  if (!value) {
    return ''
  }
  return isAbsoluteUrl(value) ? value : `${baseUrl}${value}`
}

export default resolveImageUrl
