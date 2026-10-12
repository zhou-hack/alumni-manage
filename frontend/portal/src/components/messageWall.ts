// ============================================================================
// 校友留言墙：共享数据与布局算法（自 React 版 MessageWall.tsx 上半部分拆出）
//
// 数据：墙内数据来自 GET /wall/messages（fromPublic 映射，仅 approved），每次刷新
//       都重新请求，不使用 localStorage 缓存；接口失败保留 NOTES 兜底演示数据；
//       新建留言走 POST 接口。
// ============================================================================
import type { WallPublicItem } from '../api';

export interface Note {
  id: number;
  name: string;
  gradYear: string;
  college: string;
  text: string;
  color: string; // 便签纸颜色
  left: string; // 墙上横坐标（占板块宽度百分比）
  top: string; // 墙上纵坐标（占板块高度百分比）
  rotate: number; // 初始倾斜角度，制造手工张贴的随机感
}

/** 新建留言表单收集到的内容（姓名/届次/学院由服务端从登录身份带出；showName 勾选才公开显示姓名） */
export type Draft = Pick<Note, 'text'> & { showName: boolean };

/** legacy 本地缓存键：历史版本把用户新增便签存 localStorage（刷新后产生脏乱数据），现仅用于挂载时清除 */
export const STORAGE_KEY = 'furuan_message_wall';

// 页面内墙板最多展示的便签数（取最新），全部便签在全屏幕布中查看
export const INLINE_MAX = 18;

// ------------------------------ 全屏大画布 ------------------------------
// 槽位边长(px)：便签 200×200，槽内留出抖动空间；2000 条时画布约 12480×10920
export const CELL = 260;
export const NOTE_SIZE = 200;
// 全屏缩放范围与灵敏度
export const ZOOM_MIN = 0.5;
export const ZOOM_MAX = 2.75;
export const ZOOM_SPEED = 0.0012;
export const ZERO_OFFSET = { x: 0, y: 0 };

// 便签纸配色：低饱和暖色系，压在暖白墙面之上不刺眼
export const PAPER = {
  amber: '#FEF3C7',
  blue: '#DBEAFE',
  violet: '#EDE9FE',
  green: '#DCFCE7',
  orange: '#FFEDD5',
  pink: '#FCE7F3'
};
export const PAPER_LIST = Object.values(PAPER);

// 届次 / 学院选项：原复用 mock 数据，2026-09-25 健康审计 P0-2 移除 mock 后改为占位常量。
// 实际下拉选项请由调用方按 `/honors` 或 `/directory` 真实接口返回填充。
export const COHORTS: string[] = [];
export const COLLEGES: string[] = [];

export const NOTES: Note[] = [
  { id: 1, name: '林子轩', gradYear: '2023届', college: '游戏产业学院', text: '愿我们不负青春，奔赴更好的未来', color: PAPER.amber, left: '2.5%', top: '2%', rotate: -5 },
  { id: 2, name: '陈思远', gradYear: '2024届', college: '智能产业学院', text: '从福软出发，在热爱中不断成长', color: PAPER.blue, left: '25.5%', top: '5%', rotate: 3.5 },
  { id: 3, name: '苏晓彤', gradYear: '2025届', college: '数字影视传媒产业学院', text: '护城湖的风，吹了我三年', color: PAPER.orange, left: '48.5%', top: '1.5%', rotate: -2.5 },
  { id: 4, name: '黄俊杰', gradYear: '2026届', college: '数据产业学院', text: '保持学习，保持热爱，保持向前', color: PAPER.violet, left: '71.5%', top: '4.5%', rotate: 5 },
  { id: 5, name: '郑雅雯', gradYear: '2022届', college: '数字金融产业学院', text: '城堡的灯一直亮着，我们也一直向前', color: PAPER.green, left: '11%', top: '33%', rotate: 4 },
  { id: 6, name: '林嘉豪', gradYear: '2021届', college: '智能建造产业学院', text: '毕业那天，我在垛口下拍了最后一张照片', color: PAPER.amber, left: '34%', top: '30.5%', rotate: -4.5 },
  { id: 7, name: '吴思怡', gradYear: '2024届', college: '未来技术产业学院', text: '图书馆五楼的自习位，是我的青春坐标', color: PAPER.pink, left: '57%', top: '34%', rotate: 2 },
  { id: 8, name: '何俊良', gradYear: '2023届', college: '现代通信产业学院', text: '无论走到哪，这里都是我出发的地方', color: PAPER.blue, left: '80%', top: '31%', rotate: -3 },
  { id: 9, name: '张雨桐', gradYear: '2025届', college: '游戏产业学院', text: '想念食堂三楼的那碗沙茶面', color: PAPER.violet, left: '4.5%', top: '63%', rotate: 3 },
  { id: 10, name: '王梓晴', gradYear: '2022届', college: '数据产业学院', text: '愿母校越来越好，愿我们常回家看看', color: PAPER.orange, left: '27.5%', top: '66%', rotate: -5.5 },
  { id: 11, name: '邱志豪', gradYear: '2026届', college: '智能产业学院', text: '数字教育小镇的黄昏，是最好看的晚霞', color: PAPER.green, left: '50.5%', top: '62.5%', rotate: 4.5 },
  { id: 12, name: '李诗涵', gradYear: '2021届', college: '数字影视传媒产业学院', text: '把祝福留在墙上，把想念带回城堡', color: PAPER.pink, left: '73.5%', top: '65.5%', rotate: -2 }
];

// ------------------------------ 接口数据映射 ------------------------------
/**
 * 公开留言（GET /wall/messages，仅 approved）→ 便签：
 * color 按 PAPER_LIST 轮转；left/top/rotate 用 id 确定性生成（与既有 addNote 同一手法），
 * 保证同一张便签在任何时刻、任何设备上位置一致。
 */
export function fromPublic(m: WallPublicItem, index: number): Note {
  const id = Number(m.id) || index + 1;
  return {
    id,
    name: m.name ?? '',
    gradYear: m.grad_year ?? '',
    college: m.college ?? '',
    text: m.content ?? '',
    color: PAPER_LIST[index % PAPER_LIST.length],
    left: `${6 + ((id * 17) % 78)}%`,
    top: `${8 + ((id * 23) % 62)}%`,
    rotate: ((id * 37) % 13) - 6
  };
}

// ------------------------------ 大画布布局算法 ------------------------------
export interface Slot {
  x: number;
  y: number;
  rotate: number;
}

/** 由 id 生成 0~1 的确定性伪随机数：同一张便签的抖动/角度在任何时刻、任何设备上都一致 */
export function seeded(id: number, salt: number) {
  const x = Math.sin(id * 127.1 + salt * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/**
 * 把便签按数组顺序分配到网格槽位，槽内叠加确定性抖动与随机角度。
 * 便签少时画布不小于视口（便签自然摊开分布）；便签多时画布按 260px/张 增长，
 * 配合视口裁剪，数千张也只渲染视野附近的几十张。
 */
export function computeLayout(notes: Note[], vpW: number, vpH: number): { slots: Slot[]; width: number; height: number } {
  const n = Math.max(1, notes.length);
  const cols = Math.max(1, Math.ceil(Math.sqrt(n * 1.15))); // 略宽于正方形，横向浏览更顺手
  const rows = Math.max(1, Math.ceil(n / cols));
  const width = Math.max(cols * CELL, vpW);
  const height = Math.max(rows * CELL, vpH);
  const stepX = width / cols;
  const stepY = height / rows;
  // 抖动幅度：不越过相邻槽位，便签多时自动收紧
  const jx = Math.max(0, Math.min(30, stepX / 2 - NOTE_SIZE / 2 - 6));
  const jy = Math.max(0, Math.min(30, stepY / 2 - NOTE_SIZE / 2 - 6));
  const slots = notes.map((note, i) => {
    const c = i % cols;
    const r = Math.floor(i / cols);
    return {
      x: (c + 0.5) * stepX - NOTE_SIZE / 2 + (seeded(note.id, 1) - 0.5) * 2 * jx,
      y: (r + 0.5) * stepY - NOTE_SIZE / 2 + (seeded(note.id, 2) - 0.5) * 2 * jy,
      rotate: (seeded(note.id, 3) - 0.5) * 11
    };
  });
  return { slots, width, height };
}
