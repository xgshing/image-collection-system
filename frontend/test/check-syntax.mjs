import fs from 'node:fs'

const files = [
  'frontend/pages/watermark/home.vue',
  'frontend/pages/watermark/camera.vue',
  'frontend/pages/watermark/preview.vue',
  'frontend/pages/watermark/detail.vue',
  'frontend/components/auth-sheet.vue',
  'frontend/pages/index/index.vue'
]

let failed = false
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8')
  const match = text.match(/<script setup>([\s\S]*?)<\/script>/)
  if (!match) {
    console.error('no script', file)
    failed = true
    continue
  }
  const code = match[1].replace(/import[\s\S]*?from\s+['"][^'"]+['"]/g, '')
  try {
    new Function(code)
    console.log('syntax ok', file)
  } catch (error) {
    console.error('syntax fail', file, error.message)
    failed = true
  }
}
if (failed) process.exit(1)
