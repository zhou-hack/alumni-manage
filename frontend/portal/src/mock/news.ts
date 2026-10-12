// ============================================================================
// 学校最新动态 · 统一数据源（演示数据）
//
// 说明：本文件为**平台演示内容**，不是学校已发生的真实新闻，仅用于展示信息层级与交互。
//   分类沿用学校常见的新闻分类体系：学校新闻 / 教学科研 / 校园活动 / 学生工作 /
//   通知公告 / 媒体报道 / 校友资讯（校友资讯仅作其中一个辅助分类）。
//
// 结构：全部条目集中在 newsItems（单一数据源，避免重复维护），
//   头条由 FEATURED_IDS 指定，右侧"最新资讯"取其余条目。
//   将来接后端时只需替换本文件的导出，组件层无需改动。
// ============================================================================

import { textToImage } from '../utils/image';

export type NewsCategory =
  | '学校新闻'
  | '教学科研'
  | '校园活动'
  | '学生工作'
  | '通知公告'
  | '媒体报道'
  | '校友资讯';

export interface NewsItem {
  id: number;
  category: NewsCategory;
  date: string; // 展示用日期，如 2026.09.18
  title: string;
  summary: string;
  image: string; // 封面图（列表页与详情页共用）
  body: string[]; // 正文段落
}

export const NEWS_META = {
  title: '最新动态',
  eyebrow: 'LATEST NEWS',
  subtitle: '记录学校正在发生的每一件事',
  notice: '本模块为演示数据，用于展示信息层级与交互效果'
};

// 与首页轮播图、校友照片共用统一图源
const img = (prompt: string) => textToImage(prompt, 'landscape_16_9');

const CASTLE_SQUARE =
  'Grand European castle style university main building with crenellated battlements, wide stone plaza in front, crowd of students gathering for opening ceremony, blue sky, warm morning light, photorealistic wide shot';

export const newsItems: NewsItem[] = [
  // ------------------------------ 头条（左侧轮播） ------------------------------
  {
    id: 101,
    category: '学校新闻',
    date: '2026.09.18',
    title: '学校举行2026级新生开学典礼，师生齐聚城堡前广场',
    summary:
      '新学期开学典礼在城堡主楼前广场举行，师生代表共同开启新学年的篇章，现场还为新生代表佩戴了校徽。',
    image: img(CASTLE_SQUARE),
    body: [
      '新学期伊始，学校在城堡主楼前广场举行2026级新生开学典礼。师生代表齐聚广场，共同见证新学年的开启。',
      '典礼上，学校负责人向新生介绍了学校的办学特色与校园环境，鼓励同学们在新的学习阶段保持好奇心与行动力，在专业学习与校园生活中找到自己的方向。',
      '现场还安排了新生代表发言、佩戴校徽等环节。典礼结束后，各产业学院分别组织了专业介绍与校园参观活动。'
    ]
  },
  {
    id: 102,
    category: '教学科研',
    date: '2026.09.16',
    title: '学校召开新学期教学工作会议，部署专业建设重点任务',
    summary:
      '会议围绕课程建设、实践教学与产教融合展开讨论，明确了新学期各产业学院的教学重点工作方向。',
    image: img(
      'Modern university conference room meeting, teachers discussing curriculum planning around a large table, projector screen with charts, bright daylight, professional photography'
    ),
    body: [
      '学校召开新学期教学工作会议，各产业学院负责人与教师代表参加。会议围绕课程建设、实践教学安排与产教融合项目展开讨论。',
      '会议明确了新学期的教学重点工作：一方面继续完善专业课程体系，另一方面加强实训环节与行业项目的衔接，让课堂教学与真实岗位需求保持同步。'
    ]
  },
  {
    id: 103,
    category: '校园活动',
    date: '2026.09.14',
    title: '校园文化艺术节系列活动正式启动',
    summary:
      '本届校园文化艺术节涵盖社团展演、音乐分享会与创意市集等内容，活动将持续整个秋季学期。',
    image: img(
      'Vibrant university campus art festival, students performing on outdoor stage, colorful flags and booths, audience sitting on lawn, late afternoon golden light, photorealistic'
    ),
    body: [
      '本届校园文化艺术节正式启动，涵盖社团展演、音乐分享会、创意市集与摄影展等多个板块，活动将持续整个秋季学期。',
      '学生社团在开幕式上带来了舞蹈、乐队与民俗表演。创意市集区域集中展示了学生的手作与设计作品，吸引了不少师生驻足。'
    ]
  },
  {
    id: 104,
    category: '媒体报道',
    date: '2026.09.12',
    title: '媒体关注我校数字人才培养实践',
    summary:
      '多家媒体走访了学校的实训基地与数字教育小镇，报道学校在数字技能人才培养方面的探索。',
    image: img(
      'Journalists interviewing students inside a modern digital training laboratory, camera and microphone, computer workstations in background, bright interior, photorealistic press photo'
    ),
    body: [
      '近日，多家媒体走进学校实训基地与数字教育小镇，实地了解学校在数字技能人才培养方面的做法。',
      '采访中，记者观摩了虚拟现实、数字媒体等专业的实训课堂，并与师生就课程设置、项目实践等话题进行了交流。相关报道已在多家媒体平台刊发。'
    ]
  },
  {
    id: 105,
    category: '学生工作',
    date: '2026.09.10',
    title: '学校开展新学期学生安全教育与消防演练',
    summary: '各学院组织安全教育主题班会，并在宿舍区开展消防疏散演练，提升学生的安全意识与应急能力。',
    image: img(
      'Fire safety drill on university campus, students lining up and evacuating a dormitory building in orderly manner, instructors guiding, daytime, photorealistic documentary photo'
    ),
    body: [
      '新学期开始，学校组织开展了学生安全教育系列活动。各产业学院以主题班会形式，围绕消防安全、用电安全与防范电信网络诈骗等内容进行宣讲。',
      '在学生宿舍区，学校还组织了消防疏散演练，模拟突发情况下的有序撤离流程，帮助学生熟悉逃生路线与基本自救方法。'
    ]
  },

  // ------------------------------ 右侧最新资讯 ------------------------------
  {
    id: 201,
    category: '教学科研',
    date: '2026.09.11',
    title: '我校教师团队在省级教学能力比赛中获奖',
    summary: '参赛团队以项目化教学设计参赛，获得评委认可，为学校教学改革积累了可复用的经验。',
    image: img(
      'University teaching competition award ceremony, teachers holding certificates on stage, stage lights, audience applauding, photorealistic event photography'
    ),
    body: [
      '在近期举办的省级教师教学能力比赛中，我校教师团队以项目化教学设计参赛并获奖。',
      '团队围绕真实项目场景重构了课程模块，将岗位任务拆解为可教学的单元。相关教学设计将在校内进行分享与推广。'
    ]
  },
  {
    id: 202,
    category: '校园活动',
    date: '2026.09.09',
    title: '校园歌手大赛决赛落幕，十组选手同台竞演',
    summary: '决赛在校园礼堂举行，十组选手带来不同风格的表演，现场气氛热烈。',
    image: img(
      'University singing competition final on indoor stage, student performers singing with microphones, colorful stage lighting, audience in silhouette, photorealistic'
    ),
    body: [
      '校园歌手大赛决赛在礼堂举行，十组选手依次登台，表演涵盖流行、民谣与原创作品。',
      '评委从音准、表现力与舞台完成度等方面进行评分。比赛吸引了不少同学到场观看，现场气氛热烈。'
    ]
  },
  {
    id: 203,
    category: '学校新闻',
    date: '2026.09.08',
    title: '学校与行业企业共建数字创意实训基地',
    summary: '实训基地将围绕数字内容制作与虚拟仿真方向，为学生提供更贴近岗位的实践环境。',
    image: img(
      'Signing ceremony between university and company representatives, handshake in a bright meeting room, documents on table, blue banner behind, photorealistic corporate photo'
    ),
    body: [
      '学校与行业企业签署合作协议，共建数字创意实训基地。基地将围绕数字内容制作、虚拟仿真等方向开展教学与项目实践。',
      '双方计划在课程共建、项目实训与学生实习等方面开展合作，让学生在真实项目流程中积累经验。'
    ]
  },
  {
    id: 204,
    category: '通知公告',
    date: '2026.09.07',
    title: '关于2026年秋季学期教学安排的通知',
    summary: '秋季学期课程安排、实训周次与考试时间已确定，请师生按通知要求做好相应准备。',
    image: img(
      'Neat university notice board with printed announcements, students reading notices in a bright corridor, photorealistic'
    ),
    body: [
      '2026年秋季学期教学安排已确定，各年级课程表、实训周次与考试时间已下发至各产业学院。',
      '请同学们按通知要求核对个人课表，如有疑问可向所在学院教学秘书咨询。相关文件可在学校信息平台查看。'
    ]
  },
  {
    id: 205,
    category: '学生工作',
    date: '2026.09.05',
    title: '学校开展新学期心理健康教育系列活动',
    summary: '活动包括专题讲座、团体辅导与心理测评，帮助学生更快适应新学期的学习节奏。',
    image: img(
      'University campus counseling room, students in a friendly group activity circle, soft daylight through window, plants on shelf, warm tone, photorealistic'
    ),
    body: [
      '学校在新学期开展心理健康教育系列活动，包括专题讲座、团体辅导与心理测评等内容。',
      '活动旨在帮助同学们更快适应新学期的学习节奏，掌握基本的情绪调节与沟通方法。心理咨询中心在工作日持续开放。'
    ]
  },
  {
    id: 206,
    category: '媒体报道',
    date: '2026.09.04',
    title: '媒体探访我校数字教育小镇建设进展',
    summary: '报道聚焦校园与产业园区的联动模式，介绍了小镇在教学实训与产业合作方面的进展。',
    image: img(
      'Aerial view of a modern digital education town, futuristic glass building beside European castle style campus, green landscape, clear sky, photorealistic drone shot'
    ),
    body: [
      '媒体近日探访我校数字教育小镇，重点关注校园与产业园区的联动模式。',
      '报道介绍了小镇在教学实训、产业合作与学生实践等方面的建设进展。相关报道已在多家平台发布。'
    ]
  },
  {
    id: 207,
    category: '教学科研',
    date: '2026.09.03',
    title: '学校新增多门项目化课程，覆盖数字内容方向',
    summary: '新增课程以真实项目为主线组织教学，强调从需求分析到成果交付的完整流程。',
    image: img(
      'Students working on digital content projects in a computer lab, dual monitors showing 3D models, teacher guiding, bright modern interior, photorealistic'
    ),
    body: [
      '本学期学校新增多门项目化课程，覆盖数字内容制作、虚拟现实应用等方向。',
      '课程以真实项目为主线组织教学，学生需要在课程周期内完成需求分析、方案设计与成果交付的完整流程。'
    ]
  },
  {
    id: 208,
    category: '校园活动',
    date: '2026.09.02',
    title: '学生社团招新落下帷幕，四十余个社团参与',
    summary: '招新现场设置了社团展示与体验环节，涵盖科技、文艺、体育与公益等类别。',
    image: img(
      'University club recruitment fair, colorful booths along campus walkway, students talking and signing up, banners overhead, lively atmosphere, photorealistic'
    ),
    body: [
      '为期两周的学生社团招新活动落下帷幕，四十余个社团参与，涵盖科技、文艺、体育与公益等类别。',
      '招新现场设置了社团展示与体验环节，同学们可以现场了解社团日常活动与加入方式。'
    ]
  },
  {
    id: 209,
    category: '通知公告',
    date: '2026.09.01',
    title: '图书馆秋季学期开放时间调整通知',
    summary: '开学后图书馆恢复常规开放时间，部分阅览区域完成调整，新增自习座位。',
    image: img(
      'Bright modern university library interior, students reading at long wooden tables, tall bookshelves, large windows, photorealistic'
    ),
    body: [
      '开学后图书馆恢复常规开放时间，具体时段已在入口与信息平台公布。',
      '本次还对部分阅览区域布局做了调整，新增了一批自习座位，方便同学们使用。'
    ]
  },
  {
    id: 210,
    category: '校友资讯',
    date: '2026.08.30',
    title: '校友返校分享交流活动在城堡报告厅举行',
    summary: '多位校友回到校园，结合自身经历与在校生交流了专业学习与职业发展的体会。',
    image: img(
      'Alumni sharing session in a university lecture hall, speaker on stage with slides, students listening and taking notes, warm indoor lighting, photorealistic'
    ),
    body: [
      '校友返校分享交流活动在学校报告厅举行，多位校友回到校园与在校生面对面交流。',
      '他们结合自身经历，分享了专业学习、实习实践与职业发展中的体会，并就同学们关心的问题进行了解答。'
    ]
  },
  {
    id: 211,
    category: '学校新闻',
    date: '2026.08.28',
    title: '新学期校园服务设施完成升级改造',
    summary: '学生食堂、运动场地与部分教学楼的公共空间完成改造，新学期已投入使用。',
    image: img(
      'Renovated university campus facilities, modern canteen interior with wooden tables and green plants, students dining, bright daylight, photorealistic'
    ),
    body: [
      '为改善师生的学习与生活条件，学校在假期对部分校园服务设施进行了升级改造。',
      '学生食堂的就餐区域重新规划，运动场地与教学楼公共空间也完成了修缮，新学期已全部投入使用。'
    ]
  },
  {
    id: 212,
    category: '教学科研',
    date: '2026.08.26',
    title: '学校组织教师参加企业实践锻炼',
    summary: '教师利用假期进入合作企业参与项目实践，为课程内容更新收集一线素材。',
    image: img(
      'Teacher working alongside engineers in a modern technology company office, whiteboard with diagrams, team collaboration, photorealistic'
    ),
    body: [
      '学校组织专业教师利用假期进入合作企业参与项目实践，了解行业一线的技术应用与岗位要求。',
      '参与实践的教师表示，企业项目中的实际问题为课程内容更新提供了素材，后续将把相关案例引入课堂教学。'
    ]
  }
];

// 头条新闻：由 id 指定，保证广场左侧轮播内容可控且不与右侧列表重复
const FEATURED_IDS = [101, 102, 103, 104, 105];

export const featuredNews: NewsItem[] = FEATURED_IDS.flatMap((id) => {
  const item = newsItems.find((n) => n.id === id);
  return item ? [item] : [];
});

// 最新资讯：除头条外的其余条目（按日期倒序）
export const latestNews: NewsItem[] = newsItems
  .filter((n) => !FEATURED_IDS.includes(n.id))
  .sort((a, b) => b.date.localeCompare(a.date));

// 列表页使用：全部条目按日期倒序
export const allNews: NewsItem[] = [...newsItems].sort((a, b) => b.date.localeCompare(a.date));

export const PAGE_SIZE = 6; // 右侧"最新资讯"每页 6 条（2 列 × 3 行）

export function getNewsById(id: number): NewsItem | undefined {
  return newsItems.find((n) => n.id === id);
}