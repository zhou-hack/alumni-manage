// 站内演示配图的统一入口：首页轮播、优秀校友照片、新闻封面共用同一图源，
// 避免每个数据文件各写一份拼接逻辑。
export type ImageSize =
  | 'square_hd'
  | 'square'
  | 'portrait_4_3'
  | 'portrait_16_9'
  | 'landscape_4_3'
  | 'landscape_16_9';

export function textToImage(prompt: string, imageSize: ImageSize): string {
  return `https://console.enterprise.trae.cn/api/ide/v1/text_to_image?prompt=${encodeURIComponent(
    prompt
  )}&image_size=${imageSize}`;
}