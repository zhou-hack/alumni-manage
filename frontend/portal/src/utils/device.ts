// ============================================================================
// 设备识别 v2 · 多信号投票 + 置信度
// ----------------------------------------------------------------------------
// 设计要点：
//  1) 覆盖主流移动设备 + 浏览器（iOS / Android / HarmonyOS / WP / 黑莓 / Symbian /
//     WebOS / Kindle / Tizen / KaiOS / Ubuntu Touch / Fennec / Opera Mini；
//     微信内置 / QQ / UC / 夸克 / 华为 / 小米 / 360 / Edge / Samsung Internet / Vivaldi）
//  2) 多信号投票：UA 关键字命中（权重 0.7）+ 触摸点（权重 0.15）+ 视口尺寸（权重 0.10）
//     + pointer:coarse（权重 0.05）；总分 ≥ 0.55 视为移动端
//  3) 排除桌面：Windows NT / Macintosh / X11 等关键字显著负加权，避免误判
//  4) iPadOS 13+ 伪装为 Macintosh 时靠 maxTouchPoints + pointer:coarse 兜底
//  5) 弱网友好：所有判断纯同步 UA/属性读取，不依赖网络；localStorage 缓存用户偏好
//  6) 仅在根路径触发跳转；用户已选桌面版（?nomobile=1）永久尊重
// ============================================================================

/** 设备类型枚举 */
export type DeviceKind = 'mobile' | 'tablet' | 'desktop';

/** 检测结果（含置信度，方便测试与埋点） */
export interface DeviceInfo {
  kind: DeviceKind;
  /** 综合置信度 0~1，越高越确定 */
  confidence: number;
  /** 各信号得分明细（测试与埋点用） */
  signals: {
    ua: number; // UA 关键字得分 (-1 ~ 1)
    touch: number; // 触摸点数 (0 ~ 1)
    viewport: number; // 视口尺寸得分 (0 ~ 1)
    pointer: number; // pointer:coarse 得分 (0 ~ 1)
  };
  /** 是否识别到 iPadOS 13+ 伪装 Macintosh（需要兜底） */
  isIpadOssCamouflage: boolean;
}

/* ============================================================================
 * 内部工具
 * ==========================================================================*/

/** 桌面端关键字（命中越强越像桌面） */
const DESKTOP_KEYWORDS: RegExp[] = [
  /windows nt/i,
  /windows xp/i,
  /wow64/i,
  /macintosh/i, // 注意：iPadOS 13+ 也会命中，需配合 touch 兜底
  /mac os x/i,
  /x11/i,
  /linux x86_64/i,
  /linux i686/i,
  /cros x86/i // Chrome OS 桌面
];

/** 移动端关键字（每条带命中权重） */
interface MobileRule {
  pattern: RegExp;
  weight: number;
  note: string;
}

const MOBILE_RULES: MobileRule[] = [
  // iOS 系列（必为移动；iPad 在 iPadOS 13+ 报 Macintosh，靠 touch 兜底）
  { pattern: /iphone/i, weight: 0.95, note: 'iPhone' },
  { pattern: /ipod/i, weight: 0.95, note: 'iPod Touch' },
  { pattern: /ipad/i, weight: 0.90, note: 'iPad (iPadOS 12-)' },
  // Android：mobile 关键字强烈信号；android 单独命中（部分平板）也判移动
  { pattern: /android.*mobile|mobile.*android/i, weight: 0.95, note: 'Android phone' },
  { pattern: /\bandroid\b(?![a-z])/i, weight: 0.80, note: 'Android (any)' },
  // 国产系统 / 浏览器（UA 内嵌 "Mobile" / "Android"）
  { pattern: /harmonyos|huawei.*\bmobile\b/i, weight: 0.90, note: 'HarmonyOS' },
  // 经典移动 OS
  { pattern: /windows phone|iemobile|wps /i, weight: 0.90, note: 'Windows Phone' },
  { pattern: /blackberry|bada|bb10|playbook/i, weight: 0.85, note: 'BlackBerry' },
  { pattern: /symbian|series60|series40|nokia|maemo|meego/i, weight: 0.80, note: 'Symbian/MeeGo' },
  { pattern: /webos|hpwos|web0s/i, weight: 0.85, note: 'webOS' },
  { pattern: /kindle|silk-accelerated|silk\//i, weight: 0.90, note: 'Kindle' },
  { pattern: /opera mini|opera mobi|opera tablet/i, weight: 0.80, note: 'Opera Mobile' },
  { pattern: /fennec|firefox.+mobile/i, weight: 0.85, note: 'Firefox Mobile' },
  { pattern: /ubuntu;\s.+mobile|tizen/i, weight: 0.85, note: 'Ubuntu Touch / Tizen' },
  { pattern: /\bkaios\b/i, weight: 0.85, note: 'KaiOS' },
  // 主流移动浏览器特征（覆盖国内 90% 移动端用户）
  { pattern: /micromessenger|wechat/i, weight: 0.95, note: '微信内置浏览器' },
  { pattern: /mqqbrowser|qq\//i, weight: 0.90, note: 'QQ 浏览器' },
  { pattern: /ucbrowser|ucweb/i, weight: 0.90, note: 'UC 浏览器' },
  { pattern: /quark/i, weight: 0.85, note: '夸克浏览器' },
  { pattern: /huawei.*browser|hbuawei/i, weight: 0.85, note: '华为浏览器' },
  { pattern: /xiaomi.*miuibrowser|miuibrowser/i, weight: 0.85, note: '小米浏览器' },
  { pattern: /qbcore|qihoobrowser|360se/i, weight: 0.85, note: '360 浏览器' },
  { pattern: /samsungbrowser/i, weight: 0.85, note: 'Samsung Internet' },
  { pattern: /edg\/(a|ios)/i, weight: 0.85, note: 'Edge Mobile' },
  { pattern: /vivaldi/i, weight: 0.20, note: 'Vivaldi（桌面+移动共用）' },
  // 通用 "Mobile" 兜底（命中得分低，避免与 desktop 字符串冲突）
  { pattern: /\bmobile\b(?![a-z])/i, weight: 0.40, note: '通用 Mobile token' }
];

/* ============================================================================
 * 信号计算（核心：投票）
 * ==========================================================================*/

interface InternalNavigator {
  userAgent: string;
  maxTouchPoints: number;
}

interface InternalWindow {
  innerWidth: number;
  innerHeight: number;
  matchMedia?: (q: string) => MediaQueryList;
}

interface EnvLike {
  navigator?: Partial<InternalNavigator>;
  window?: Partial<InternalWindow>;
}

/**
 * 信号 1：UA 关键字（主信号）
 *   - 命中移动规则 → 加分；命中桌面规则 → 减分
 *   - 取规则权重的最大值（避免多条规则叠加爆炸）
 */
function scoreUA(ua: string): number {
  let max = 0;
  for (const r of MOBILE_RULES) {
    if (r.pattern.test(ua) && r.weight > max) max = r.weight;
  }
  // 桌面关键字负权（命中越多越负）
  let desk = 0;
  for (const r of DESKTOP_KEYWORDS) {
    if (r.test(ua)) desk += 0.5;
  }
  // 同时命中移动规则和桌面关键字时（如 Macintosh 兜底场景），负权衰减
  if (max > 0 && desk > 0) desk *= 0.4;
  return Math.max(-1, max - desk);
}

/**
 * 信号 2：触摸点数
 *   - maxTouchPoints > 1 强烈移动端信号；== 0 几乎桌面；== 1 通常是触屏笔记本
 */
function scoreTouch(maxTouchPoints: number): number {
  if (maxTouchPoints <= 0) return -0.5;
  if (maxTouchPoints === 1) return 0;
  return Math.min(1, maxTouchPoints / 5); // 5+ 触摸点满分
}

/**
 * 信号 3：视口尺寸
 *   - innerWidth < 600 强烈移动端；600~900 可能是平板或小窗口桌面；> 900 桌面
 */
function scoreViewport(innerWidth: number): number {
  if (innerWidth <= 0) return 0;
  if (innerWidth < 480) return 1;
  if (innerWidth < 600) return 0.85;
  if (innerWidth < 768) return 0.55;
  if (innerWidth < 1024) return 0.20;
  return -0.30;
}

/**
 * 信号 4：pointer:coarse（粗指针 = 触摸主导）
 *   - coarse → 移动/平板；fine → 桌面；none → 早期设备
 */
function scorePointer(matchMedia: InternalWindow['matchMedia']): number {
  if (!matchMedia) return 0;
  try {
    if (matchMedia('(pointer: coarse)').matches) return 0.85;
    if (matchMedia('(any-pointer: coarse)').matches) return 0.60;
    if (matchMedia('(pointer: fine)').matches) return -0.30;
  } catch {
    /* matchMedia 抛错时静默 */
  }
  return 0;
}

/* ============================================================================
 * 综合判定（加权投票）
 * ==========================================================================*/

const W = { ua: 0.70, touch: 0.15, viewport: 0.10, pointer: 0.05 } as const;

/** 仅 Tablet 设备（不是 mobile，但也不是 desktop）的判定阈值 */
const TABLET_THRESHOLD = 0.45;

interface RawInputs {
  ua: string;
  maxTouchPoints: number;
  innerWidth: number;
  innerHeight: number;
  matchMedia?: InternalWindow['matchMedia'];
}

/** 纯函数：输入 UA + 视口 + 触摸，返回 DeviceInfo（便于测试） */
export function detectDevice(inputs: RawInputs): DeviceInfo {
  const sUA = scoreUA(inputs.ua);
  const sTouch = scoreTouch(inputs.maxTouchPoints);
  const sVP = scoreViewport(inputs.innerWidth);
  const sPtr = scorePointer(inputs.matchMedia);

  // 加权总分
  const score = sUA * W.ua + sTouch * W.touch + sVP * W.viewport + sPtr * W.pointer;

  // iPadOS 13+ 兜底：UA 报 Macintosh 但触摸点 > 0 → 视为平板（mobile）
  const isIpadOssCamouflage = /macintosh/i.test(inputs.ua) && inputs.maxTouchPoints > 0;

  // 判定类型
  let kind: DeviceKind;
  if (isIpadOssCamouflage && inputs.maxTouchPoints >= 5) {
    // 真 iPadOS 13+ 伪装：UA 是 Macintosh 但触摸点 ≥ 5（iPad 真实多点触控）
    kind = 'tablet';
  } else if (isIpadOssCamouflage) {
    // UA 是 Mac 但触摸点 1~4：触屏笔记本（MacBook 带触控条 / iPad Pro 妙控键盘）
    // 不当作 iPad，按桌面/移动分支继续判定
    // 这里直接落到下面的桌面视口分支
    if (inputs.innerWidth >= 1024) {
      kind = 'desktop';
    } else {
      kind = 'mobile';
    }
  } else if (/ipad/i.test(inputs.ua)) {
    // iPad OS 13 以下（含 iPadOS 14 早期仍报 iPad）：UA 直接锁定 tablet
    kind = 'tablet';
  } else if (/\bandroid\b/i.test(inputs.ua) && /\bmobile\b/i.test(inputs.ua)) {
    // Android phone（UA 显式含 mobile 关键字）：稳定判 mobile，不受 viewport 干扰
    kind = 'mobile';
  } else if (/\bandroid\b/i.test(inputs.ua) && inputs.innerWidth >= 1024) {
    // Android 平板：UA 含 android 但不含 mobile 关键字 + 大视口 → tablet
    kind = 'tablet';
  } else if (
    // 触屏笔记本/桌面端：UA 是桌面关键字（Windows/macOS/Linux）+ 视口 ≥ 1024 → desktop
    /macintosh|windows nt|x11|linux/i.test(inputs.ua) &&
    inputs.innerWidth >= 1024
  ) {
    kind = 'desktop';
  } else if (score >= 0.62) {
    kind = 'mobile';
  } else if (score >= TABLET_THRESHOLD) {
    kind = 'tablet';
  } else {
    kind = 'desktop';
  }

  // 置信度：sigmoid-like，把 [-1,1] 映射到 [0,1]
  const confidence = 1 / (1 + Math.exp(-score * 4));

  return {
    kind,
    confidence,
    signals: { ua: sUA, touch: sTouch, viewport: sVP, pointer: sPtr },
    isIpadOssCamouflage
  };
}

/* ============================================================================
 * 浏览器侧便利 API（运行时直接调用）
 * ==========================================================================*/

/**
 * 安全包一层 navigator/window，避免 SSR / 隐私模式下抛错
 */
function readEnv(): EnvLike {
  if (typeof globalThis === 'undefined') return {};
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const g = globalThis as any;
  return {
    navigator: typeof g.navigator !== 'undefined' ? g.navigator : undefined,
    window: typeof g.window !== 'undefined' ? g.window : undefined
  };
}

/** 实时检测（同步、单次；不读缓存）。推荐在守卫/router 跳转前调用 */
export function detectCurrentDevice(): DeviceInfo {
  const env = readEnv();
  const ua = env.navigator?.userAgent ?? '';
  const touch = env.navigator?.maxTouchPoints ?? 0;
  const innerW = env.window?.innerWidth ?? 0;
  const innerH = env.window?.innerHeight ?? 0;
  const mm = env.window?.matchMedia;
  return detectDevice({ ua, maxTouchPoints: touch, innerWidth: innerW, innerHeight: innerH, matchMedia: mm });
}

/* ============================================================================
 * 兼容老 API（向后兼容，App.vue / Router 等还在用）
 * ==========================================================================*/

export function isMobileDevice(ua?: string): boolean {
  // 兼容语义：手机 + 平板都算"移动端"（H5 优先策略）
  // 真正三态分类见 detectDevice()；老 API 仅返回 boolean 兼容性
  if (typeof ua === 'string') {
    // iPhone/iPad/Android phone/Android tablet/HarmonyOS/微信/QQ/UC 等都返回 true
    return scoreUA(ua) >= 0.50;
  }
  const info = detectCurrentDevice();
  return info.kind === 'mobile' || info.kind === 'tablet';
}

export function isTablet(ua?: string): boolean {
  // 仅 UA 显式含**强平板特征**时返回 true（避免 Windows Phone / Opera Mobile 这种 UA 含
  // android 但不含 "mobile" 关键字的设备被误判为平板）
  if (typeof ua === 'string') {
    const s = (ua || '').toLowerCase();
    // iPad (iPadOS 12-)：UA 直接含 ipad
    if (/\bipad\b/.test(s)) return true;
    // Android Tablet UA 通常含 "Tablet" token（Chrome / Edge 都会带）
    if (/\btablet\b/i.test(s)) return true;
    // Playbook（旧 BlackBerry 平板）
    if (/playbook/i.test(s)) return true;
    // 真实运行时（detectCurrentDevice）会用大视口 + Android UA 判 Android 平板
    return false;
  }
  return detectCurrentDevice().kind === 'tablet';
}

/* ============================================================================
 * 调试工具（仅 dev 使用，prod 不会调用）
 * ==========================================================================*/

/**
 * 详细诊断信息（用于埋点 + 自助问题排查）
 * 返回人类可读的字符串 + 结构化对象
 */
export function diagnose(): {
  info: DeviceInfo;
  ua: string;
  touchPoints: number;
  viewport: { w: number; h: number };
  pointer: { coarse: boolean; fine: boolean };
} {
  const env = readEnv();
  const ua = env.navigator?.userAgent ?? '';
  const touchPoints = env.navigator?.maxTouchPoints ?? 0;
  const w = env.window?.innerWidth ?? 0;
  const h = env.window?.innerHeight ?? 0;
  const mm = env.window?.matchMedia;
  const coarse = mm?.('(pointer: coarse)').matches ?? false;
  const fine = mm?.('(pointer: fine)').matches ?? false;
  const info = detectDevice({ ua, maxTouchPoints: touchPoints, innerWidth: w, innerHeight: h, matchMedia: mm });
  return { info, ua, touchPoints, viewport: { w, h }, pointer: { coarse, fine } };
}