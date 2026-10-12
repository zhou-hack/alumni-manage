// ============================================================================
// 学号 / 手机号 / 邮箱等敏感标识脱敏（审查报告 P0-3：公开页面不暴露完整学号）
// ----------------------------------------------------------------------------
// 规则：
//   - 学号：保留前 4 + 后 2，中间用 ****；不足 7 位则只显示前 2 + *。
//   - 手机号：保留前 3 + 后 4；不足 7 位原样返回（前端信任已校验的合法格式）。
//   - 邮箱：保留首字符 + @ + 域名。
// 任何输入为 falsy 时返回 ''（不显示占位文本，避免误读）。
// ============================================================================

/** 学号脱敏：例如 2021010001 → 2021****01 */
export function maskStudentId(sid: string | null | undefined): string {
  if (!sid) return '';
  const s = String(sid).trim();
  if (s.length < 7) return s.length > 2 ? `${s.slice(0, 2)}${'*'.repeat(Math.max(s.length - 2, 1))}` : s;
  return `${s.slice(0, 4)}${'*'.repeat(Math.max(s.length - 6, 2))}${s.slice(-2)}`;
}

/** 手机号脱敏：例如 13900010001 → 139****0001 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '';
  const s = String(phone).trim();
  if (s.length < 7) return s;
  return `${s.slice(0, 3)}${'*'.repeat(Math.max(s.length - 7, 4))}${s.slice(-4)}`;
}

/** 邮箱脱敏：例如 foo@bar.com → f****@bar.com */
export function maskEmail(email: string | null | undefined): string {
  if (!email) return '';
  const s = String(email).trim();
  const at = s.indexOf('@');
  if (at <= 0) return s;
  return `${s[0]}****${s.slice(at)}`;
}