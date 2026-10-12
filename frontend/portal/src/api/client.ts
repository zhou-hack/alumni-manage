// 统一请求封装：对齐《[定稿]API规范.md》
// - 1.7 密码前端 SHA-256 散列（hex 小写 64 字符）后上传，禁止明文；
//   HTTP 等非安全上下文无 crypto.subtle，自动降级为纯 JS 实现
// - 1.2 Bearer 令牌；401 仅允许自动刷新一次（2.4）
// - 双模式：VITE_USE_MOCK=true 时请求分发到 mock/server.ts（纯前端原型）；
//   默认走真实后端（/api/v1，dev 经 vite 代理转发，见 vite.config.ts）

import { ERR, type ErrKey } from './types';

export const BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1';
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

const LS = { access: 'fr_access_token', refresh: 'fr_refresh_token' };

/** 沙箱/隐私模式下 localStorage 可能被禁用（直接抛 SecurityError）：
 *  读写全部容错，避免启动期崩溃导致整站白屏。 */
function lsGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function lsSet(key: string, val: string) {
  try {
    localStorage.setItem(key, val);
  } catch {
    /* 存储被禁用时静默忽略 */
  }
}
function lsDel(key: string) {
  try {
    localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

/* ================= 纯 JS SHA-256（crypto.subtle 不可用时的降级实现） ================= */
// 非安全上下文（如经非 localhost 的 http 访问）浏览器不暴露 crypto.subtle，
// 此处提供等价的同步实现，保证登录/激活/改密在 HTTP 环境同样可用。
const SHA256_K: number[] = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
];

function sha256Bytes(data: Uint8Array): Uint8Array {
  const bitLen = data.length * 8;
  // 追加 0x80 + 补零 + 8 字节大端长度，凑成 64 字节整数倍
  const padded = new Uint8Array((((data.length + 8) >> 6) + 1) << 6);
  padded.set(data);
  padded[data.length] = 0x80;
  const dv = new DataView(padded.buffer);
  dv.setUint32(padded.length - 4, bitLen >>> 0);
  dv.setUint32(padded.length - 8, Math.floor(bitLen / 0x100000000));

  const h = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
  const w = new Uint32Array(64);
  const rotr = (x: number, n: number) => ((x >>> n) | (x << (32 - n))) >>> 0;

  for (let off = 0; off < padded.length; off += 64) {
    for (let t = 0; t < 16; t++) w[t] = dv.getUint32(off + t * 4);
    for (let t = 16; t < 64; t++) {
      const s0 = (rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >>> 3)) >>> 0;
      const s1 = (rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >>> 10)) >>> 0;
      w[t] = (w[t - 16] + s0 + w[t - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, hh] = h;
    for (let t = 0; t < 64; t++) {
      const S1 = (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) >>> 0;
      const ch = ((e & f) ^ (~e & g)) >>> 0;
      const t1 = (hh + S1 + ch + SHA256_K[t] + w[t]) >>> 0;
      const S0 = (rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) >>> 0;
      const maj = ((a & b) ^ (a & c) ^ (b & c)) >>> 0;
      const t2 = (S0 + maj) >>> 0;
      hh = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    h[0] = (h[0] + a) >>> 0; h[1] = (h[1] + b) >>> 0; h[2] = (h[2] + c) >>> 0; h[3] = (h[3] + d) >>> 0;
    h[4] = (h[4] + e) >>> 0; h[5] = (h[5] + f) >>> 0; h[6] = (h[6] + g) >>> 0; h[7] = (h[7] + hh) >>> 0;
  }

  const out = new Uint8Array(32);
  const odv = new DataView(out.buffer);
  h.forEach((v, i) => odv.setUint32(i * 4, v));
  return out;
}

const toHex = (buf: ArrayBuffer | Uint8Array) =>
  Array.from(new Uint8Array(buf instanceof Uint8Array ? buf.buffer : buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

/** 1.7 前端散列：SHA-256 → hex 小写（优先 crypto.subtle，HTTP 环境降级纯 JS） */
export async function sha256Hex(plain: string): Promise<string> {
  const bytes = new TextEncoder().encode(plain);
  if (globalThis.crypto?.subtle) {
    const buf = await crypto.subtle.digest('SHA-256', bytes);
    return toHex(buf);
  }
  return toHex(sha256Bytes(bytes));
}

/** 1.7 散列前强度校验：≥8 位、含字母+数字 */
export function checkPasswordStrength(plain: string): boolean {
  return plain.length >= 8 && /[A-Za-z]/.test(plain) && /\d/.test(plain);
}

export const tokenStore = {
  get access() {
    return lsGet(LS.access);
  },
  get refresh() {
    return lsGet(LS.refresh);
  },
  set(access: string, refresh: string) {
    lsSet(LS.access, access);
    lsSet(LS.refresh, refresh);
  },
  clear() {
    lsDel(LS.access);
    lsDel(LS.refresh);
  }
};

export class ApiError extends Error {
  status: number;
  code: string;
  data: unknown;
  constructor(status: number, code: string, message: string, data?: unknown) {
    super(message);
    this.status = status;
    this.code = code;
    this.data = data;
  }
}

export interface ReqOpts {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  /** multipart/form-data 请求体（附件上传，API 规范第六章；设置后忽略 body，不手动设 Content-Type） */
  form?: FormData;
  /** 跳过鉴权头（公开接口） */
  public?: boolean;
  /** 使用 reset_token 作为 Bearer（2.3 ③） */
  bearer?: string;
}

let refreshing: Promise<void> | null = null;

/** 底层调用：mock 模式分发到 mock/server.ts，真实模式走 fetch（统一信封 {code,message,data}）
 *  注意：mock 服务端必须动态导入——它反向 import 本模块的工具函数并在顶层初始化数据，
 *  静态导入会形成循环依赖，导致 `isoNow` 暂存死区（TDZ）崩溃、整站白屏。 */
async function rawCall(
  method: NonNullable<ReqOpts['method']>,
  path: string,
  body: unknown,
  token: string | null,
  form?: FormData
) {
  if (USE_MOCK) {
    const { mockDispatch } = await import('./mock/server');
    return mockDispatch(method, path, body, token);
  }
  const res = await fetch(BASE_URL + path, {
    method,
    headers: {
      // multipart 由浏览器自动生成带 boundary 的 Content-Type，禁止手动覆盖
      ...(form ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: form ?? (body !== undefined ? JSON.stringify(body) : undefined)
  });
  const json = await res.json();
  if (!res.ok) throw new ApiError(res.status, json.code, json.message, json.data);
  return json;
}

/** 2.4 静默刷新一次 */
async function silentRefresh() {
  if (!refreshing) {
    refreshing = (async () => {
      const rt = tokenStore.refresh;
      if (!rt) throw new ApiError(401, 'UNAUTHORIZED', '未登录');
      const res = await rawCall('POST', '/auth/refresh', { refresh_token: rt }, null);
      const d = res.data as { access_token: string; refresh_token: string };
      tokenStore.set(d.access_token, d.refresh_token);
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/** 统一请求入口：mock 模式直接分发；真实模式走 fetch */
export async function request<T>(path: string, opts: ReqOpts = {}): Promise<T> {
  const doCall = (token: string | null) => rawCall(opts.method ?? 'GET', path, opts.body, token, opts.form);

  const bearer = opts.bearer ?? (opts.public ? null : tokenStore.access);
  try {
    const res = await doCall(bearer);
    return res.data as T;
  } catch (e) {
    // 401 → 静默刷新一次；仍失败则抛给调用方跳登录
    if (e instanceof ApiError && e.status === 401 && !opts.public && !opts.bearer && tokenStore.refresh) {
      try {
        await silentRefresh();
        const res = await doCall(tokenStore.access);
        return res.data as T;
      } catch {
        tokenStore.clear();
        throw new ApiError(401, 'UNAUTHORIZED', '登录已过期，请重新登录');
      }
    }
    throw e;
  }
}

/** 抛规范错误（mock 服务端内部使用） */
export function apiErr(key: ErrKey, message?: string, data?: unknown): ApiError {
  const e = ERR[key];
  return new ApiError(e.status, e.code, message ?? key, data);
}

export const uuid = () =>
  (crypto as { randomUUID?: () => string }).randomUUID?.() ?? Math.random().toString(36).slice(2) + Date.now().toString(36);

/** 1.5 业务时间字段：ISO 8601 UTC */
export const isoNow = () => new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
