/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,vue}'],
  theme: {
    extend: {
      colors: {
        brick: '#E8E0D5', // 城堡石砖米灰
        night: '#1A103D', // 夜空紫
        tech: '#2563EB', // 福软科技蓝
        lamp: '#F5B301', // 灯光金
        warm: '#FFF9F0', // 暖白
        ink: '#1F2937' // 深灰文字
      },
      fontFamily: {
        title: ['"Noto Serif SC"', 'STZhongsong', 'SimSun', 'serif'],
        code: ['"JetBrains Mono"', 'Consolas', 'Courier New', 'monospace']
      }
    }
  },
  plugins: []
};
