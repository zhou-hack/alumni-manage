/**
 * 3D 视差：卡片随鼠标做阻尼缓动倾斜 + 悬浮小片按 data-depth 视差位移
 * 用法：rootRef 绑定页面根容器（内含 .chip 小片），tiltRef 绑定卡片倾斜层
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

export function useTilt(maxDeg = 5) {
  const rootRef = ref(null)
  const tiltRef = ref(null)
  let chips = []
  let raf = 0
  let tx = 0
  let ty = 0
  let cx = 0
  let cy = 0
  let mx = 0
  let my = 0
  let cmx = 0
  let cmy = 0

  function onMove(e) {
    const x = e.clientX / window.innerWidth - 0.5
    const y = e.clientY / window.innerHeight - 0.5
    tx = x * maxDeg * 2 // rotateY
    ty = -y * maxDeg * 1.6 // rotateX
    mx = x
    my = y
  }

  function loop() {
    cx += (tx - cx) * 0.06
    cy += (ty - cy) * 0.06
    if (tiltRef.value) {
      tiltRef.value.style.transform = `rotateY(${cx}deg) rotateX(${cy}deg)`
    }
    cmx += (mx - cmx) * 0.05
    cmy += (my - cmy) * 0.05
    for (const c of chips) {
      const d = Number(c.dataset.depth) || 30
      c.style.transform = `translate(${-cmx * d}px, ${-cmy * d}px)`
    }
    raf = requestAnimationFrame(loop)
  }

  onMounted(() => {
    chips = rootRef.value ? Array.from(rootRef.value.querySelectorAll('.chip')) : []
    window.addEventListener('mousemove', onMove)
    loop()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('mousemove', onMove)
    cancelAnimationFrame(raf)
  })

  return { rootRef, tiltRef }
}
