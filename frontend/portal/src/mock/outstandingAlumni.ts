// 优秀校友展示数据：增删改校友只需编辑本数组
// photo 留空时自动渲染占位图（蓝紫渐变 + 姓氏首字）；替换为真实照片路径（如 /alumni/linzixuan.jpg）即可
import { textToImage } from '../utils/image';

export interface AlumniTimelineItem {
  year: string;
  event: string;
}
/**
 * 荣誉类别（与 PRD M03-03「荣誉类别与授予单位字典」对齐）：
 * 荣誉、证书、奖项统一为「荣誉」实体，通过类别字典区分，不再分模块。
 */
export type HonorCategory = '奖学金' | '三好学生' | '优秀干部' | '证书' | '奖项' | '社会贡献';

/** 附件类型（PRD M02-03：图片 / PDF / Word / Excel） */
export type AttachmentKind = 'image' | 'pdf' | 'word' | 'excel';
export interface HonorAttachment {
  /** 附件显示名（不带扩展名） */
  name: string;
  kind: AttachmentKind;
  /** 资源地址（项目内放 /attachments/... 或外链） */
  url: string;
}

export interface AlumniAwardItem {
  title: string;
  year: string;
  level: string;
  /** 荣誉类别（PRD 类别字典；缺省视为「奖项」） */
  category?: HonorCategory;
  /**
   * 证书/获奖材料附件（PRD M03-02：有原件上传附件，无原件文字记录）。
   * 展示端点击荣誉项即可查看附件：图片走灯箱、PDF 走在线预览、Word/Excel 提供下载。
   */
  attachments?: HonorAttachment[];
}
export interface OutstandingAlumni {
  name: string;
  photo: string;
  message: string;
  college: string;
  gradYear: string;
  /** 当前身份：职位 / 城市（校友档案索引列表使用） */
  current: string;
  /** 学号（校友档案索引筛选使用，入学年 + 院系码 + 序号） */
  studentId: string;
  timeline: AlumniTimelineItem[];
  awards: AlumniAwardItem[];
}

// 统一的图片生成入口（与首页轮播图使用同一图源）
const img = (prompt: string) => textToImage(prompt, 'portrait_4_3');

export const OUTSTANDING_ALUMNI: OutstandingAlumni[] = [
  {
    name: '林子轩',
    photo: img('Professional portrait photo of a confident young Chinese male university graduate in his early twenties, navy blazer over white shirt, standing in front of a beige European castle style university building with battlements, warm afternoon sunlight, shallow depth of field, realistic photography'),
    message: '愿我们不负青春，奔赴更好的未来',
    college: '游戏产业学院',
    gradYear: '2023届',
    current: 'VR开发工程师 / 杭州',
    studentId: '202001001',
    timeline: [
      { year: '2020', event: '入读虚拟现实技术应用专业' },
      { year: '2021', event: '入选学院VR创新工作室' },
      { year: '2022', event: '带队获福建省VR应用开发大赛一等奖' },
      { year: '2022', event: '获国家奖学金' },
      { year: '2023', event: '完成企业级VR项目实训' },
      { year: '2023', event: '毕业，入职元宇宙企业任VR开发工程师' }
    ],
    awards: [
      {
        title: '福建省VR应用开发大赛一等奖',
        year: '2022',
        level: '省级',
        category: '奖项',
        attachments: [{ name: '获奖证书', kind: 'image', url: '/attachments/sample-certificate.svg' }]
      },
      { title: '国家奖学金', year: '2022', level: '国家级', category: '奖学金' }
    ]
  },
  {
    name: '陈思远',
    photo: img('Portrait photo of a cheerful young Chinese male graduate in his early twenties, light blue shirt, holding a laptop, modern glass wall campus building background, bright natural daylight, realistic photography'),
    message: '从福软出发，在热爱中不断成长',
    college: '智能产业学院',
    gradYear: '2024届',
    current: '前端开发工程师 / 福州',
    studentId: '202102002',
    timeline: [
      { year: '2021', event: '入读软件技术专业' },
      { year: '2022', event: '加入前端开发兴趣小组' },
      { year: '2023', event: '进入前端开发团队顶岗实习' },
      { year: '2023', event: '获校级三好学生' },
      { year: '2024', event: '取得1+X Web前端证书（中级）' },
      { year: '2024', event: '毕业，成为前端开发工程师' }
    ],
    awards: [
      {
        title: '1+X Web前端开发职业技能等级证书（中级）',
        year: '2024',
        level: '行业',
        category: '证书',
        attachments: [{ name: '证书原件（扫描件）', kind: 'pdf', url: '/attachments/sample-certificate.pdf' }]
      },
      { title: '校级三好学生', year: '2023', level: '校级', category: '三好学生' }
    ]
  },
  {
    name: '黄俊杰',
    photo: img('Portrait photo of a young Chinese male graduate in a casual grey sweater, standing on a green campus lawn with trees, golden hour sunlight, friendly smile, realistic photography'),
    message: '保持学习，保持热爱，保持向前',
    college: '数据产业学院',
    gradYear: '2026届',
    current: '大数据技术专业在读 / 福州',
    studentId: '202403003',
    timeline: [
      { year: '2024', event: '入读大数据技术专业' },
      { year: '2024', event: '加入数据建模社团' },
      { year: '2025', event: '获数据建模训练营优秀学员' },
      { year: '2025', event: '获院级奖学金三等奖' },
      { year: '2026', event: '担任班级学习委员，备战技能竞赛' }
    ],
    awards: [
      {
        title: '校级数据建模训练营优秀学员',
        year: '2025',
        level: '校级',
        category: '奖项',
        attachments: [{ name: '结业证书', kind: 'image', url: '/attachments/sample-certificate.svg' }]
      },
      { title: '院级奖学金三等奖', year: '2025', level: '院级', category: '奖学金' }
    ]
  },
  {
    name: '李欣怡',
    photo: img('Portrait photo of a smiling young Chinese female graduate in her early twenties, long black hair, white blouse, European castle style campus towers in soft focus background, natural light, realistic photography'),
    message: '母校是起点，未来由自己书写',
    college: '数字影视传媒产业学院',
    gradYear: '2022届',
    current: '原画师 / 上海',
    studentId: '201904004',
    timeline: [
      { year: '2019', event: '入读动漫制作技术专业' },
      { year: '2020', event: '入选校动漫创作社' },
      { year: '2021', event: '获海峡两岸动漫设计大赛银奖' },
      { year: '2021', event: '参与校企动画短片项目' },
      { year: '2022', event: '获校级优秀毕业生' },
      { year: '2022', event: '毕业，加入数字媒体公司任原画师' }
    ],
    awards: [
      { title: '海峡两岸动漫设计大赛银奖', year: '2021', level: '省级', category: '奖项' },
      { title: '校级优秀毕业生', year: '2022', level: '校级', category: '三好学生' }
    ]
  },
  {
    name: '张浩然',
    photo: img('Portrait photo of a young Chinese male graduate wearing glasses and a dark polo shirt, standing in a modern university library with bookshelves, warm indoor lighting, realistic photography'),
    message: '把每一次实训都当成真正的战场',
    college: '智能产业学院',
    gradYear: '2024届',
    current: '后端开发工程师 / 深圳',
    studentId: '202102005',
    timeline: [
      { year: '2021', event: '入读软件技术专业' },
      { year: '2022', event: '获校级优秀学生干部' },
      { year: '2023', event: '获蓝桥杯程序设计大赛省三等奖' },
      { year: '2023', event: '参与后端服务实训开发' },
      { year: '2024', event: '完成分布式系统课程设计' },
      { year: '2024', event: '毕业，入职软件企业任后端开发' }
    ],
    awards: [
      { title: '蓝桥杯程序设计大赛省三等奖', year: '2023', level: '省级', category: '奖项' },
      { title: '校级优秀学生干部', year: '2022', level: '校级', category: '优秀干部' }
    ]
  },
  {
    name: '吴雨桐',
    photo: img('Portrait photo of a young Chinese female graduate with shoulder length hair, light pink cardigan, campus lake with castle tower reflection in background, sunset glow, realistic photography'),
    message: '代码与热爱，都是青春最好的注脚',
    college: '现代通信产业学院',
    gradYear: '2025届',
    current: '通信网络运维工程师 / 厦门',
    studentId: '202207006',
    timeline: [
      { year: '2022', event: '入读智能互联网络技术专业' },
      { year: '2023', event: '获省大学生摄影大赛优秀奖' },
      { year: '2024', event: '获校级志愿服务之星' },
      { year: '2024', event: '参与5G基站勘测实践' },
      { year: '2025', event: '完成网络运维综合实训' },
      { year: '2025', event: '毕业，投身通信网络运维岗位' }
    ],
    awards: [
      { title: '校级志愿服务之星', year: '2024', level: '校级', category: '社会贡献' },
      { title: '省大学生摄影大赛优秀奖', year: '2023', level: '省级', category: '奖项' }
    ]
  },
  {
    name: '郑嘉豪',
    photo: img('Portrait photo of a young Chinese male graduate in a black suit jacket, confident smile, modern glass office building background, professional lighting, realistic photography'),
    message: '在数字教育的浪潮里，做踏实的工匠',
    college: '数字金融产业学院',
    gradYear: '2022届',
    current: '金融科技专员 / 上海',
    studentId: '201906007',
    timeline: [
      { year: '2019', event: '入读数字金融专业' },
      { year: '2020', event: '获校级奖学金一等奖' },
      { year: '2021', event: '获全国大学生电商创新大赛三等奖' },
      { year: '2021', event: '参加金融科技实训营' },
      { year: '2022', event: '完成量化分析课程项目' },
      { year: '2022', event: '毕业，进入金融科技公司' }
    ],
    awards: [
      { title: '全国大学生电商创新大赛三等奖', year: '2021', level: '国家级', category: '奖项' },
      { title: '校级奖学金一等奖', year: '2020', level: '校级', category: '奖学金' }
    ]
  },
  {
    name: '周雅琪',
    photo: img('Portrait photo of a young Chinese female graduate wearing a beige trench coat, castle style campus tower in background, autumn afternoon light, realistic photography'),
    message: '城堡教会我的是，慢慢来会更快',
    college: '数字影视传媒产业学院',
    gradYear: '2024届',
    current: '新媒体运营策划 / 杭州',
    studentId: '202104008',
    timeline: [
      { year: '2021', event: '入读数字媒体技术专业' },
      { year: '2022', event: '获福建省大学生文创设计优秀奖' },
      { year: '2023', event: '获校级短视频创作大赛一等奖' },
      { year: '2023', event: '参与短视频工厂校企项目' },
      { year: '2024', event: '主导校园账号矩阵运营实训' },
      { year: '2024', event: '毕业，任新媒体运营策划' }
    ],
    awards: [
      { title: '校级短视频创作大赛一等奖', year: '2023', level: '校级', category: '奖项' },
      { title: '福建省大学生文创设计优秀奖', year: '2022', level: '省级', category: '奖项' }
    ]
  },
  {
    name: '许晨曦',
    photo: img('Portrait photo of a young Chinese male graduate in a white t-shirt and denim jacket, standing in a bright computer lab with monitors, natural light from window, realistic photography'),
    message: '在实训室的每个深夜，都算数',
    college: '智能产业学院',
    gradYear: '2023届',
    current: '工业互联网工程师 / 苏州',
    studentId: '202002009',
    timeline: [
      { year: '2020', event: '入读软件技术专业' },
      { year: '2021', event: '获校级技能之星' },
      { year: '2022', event: '获福建省职业院校技能大赛三等奖' },
      { year: '2022', event: '参与工业互联网平台实训' },
      { year: '2023', event: '完成边缘计算课程设计' },
      { year: '2023', event: '毕业，入职工业互联网企业' }
    ],
    awards: [
      { title: '福建省职业院校技能大赛三等奖', year: '2022', level: '省级', category: '奖项' },
      { title: '校级技能之星', year: '2021', level: '校级', category: '三好学生' }
    ]
  },
  {
    name: '何俊霖',
    photo: img('Portrait photo of a young Chinese male graduate with short hair, wearing a dark green hoodie, castle style campus courtyard background, soft daylight, realistic photography'),
    message: '把热爱写进代码，把脚步留在城堡',
    college: '游戏产业学院',
    gradYear: '2025届',
    current: '游戏美术设计师 / 广州',
    studentId: '202201010',
    timeline: [
      { year: '2022', event: '入读游戏艺术设计专业' },
      { year: '2023', event: '入选游戏美术工作室' },
      { year: '2024', event: '获校园Game Jam最佳视觉奖' },
      { year: '2024', event: '取得游戏美术中级行业证书' },
      { year: '2025', event: '完成毕业设计游戏Demo' },
      { year: '2025', event: '毕业，加入游戏工作室' }
    ],
    awards: [
      { title: '校园Game Jam最佳视觉奖', year: '2024', level: '校级', category: '奖项' },
      { title: '游戏美术中级行业证书', year: '2024', level: '行业', category: '证书' }
    ]
  },
  {
    name: '苏婉清',
    photo: img('Portrait photo of a young Chinese female graduate with long straight hair, wearing a light blue knit sweater, walking on a campus path with trees, morning light, realistic photography'),
    message: '感谢母校，让我敢于想得更远',
    college: '数据产业学院',
    gradYear: '2026届',
    current: '大数据技术专业在读 / 福州',
    studentId: '202303011',
    timeline: [
      { year: '2023', event: '入读大数据技术专业' },
      { year: '2024', event: '获校级三好学生' },
      { year: '2024', event: '加入数据分析兴趣小组' },
      { year: '2025', event: '获院级奖学金二等奖' },
      { year: '2026', event: '参与智慧城市数据实训项目' }
    ],
    awards: [
      { title: '院级奖学金二等奖', year: '2025', level: '院级', category: '奖学金' },
      { title: '校级三好学生', year: '2024', level: '校级', category: '三好学生' }
    ]
  },
  {
    name: '罗志远',
    photo: img('Portrait photo of a young Chinese male graduate in a grey blazer, standing by a campus lake with a castle reflection, clear blue sky, realistic photography'),
    message: '从护城湖边出发，去看更大的世界',
    college: '现代通信产业学院',
    gradYear: '2022届',
    current: '网络优化工程师 / 南京',
    studentId: '201907012',
    timeline: [
      { year: '2019', event: '入读现代通信技术专业' },
      { year: '2020', event: '获校级技能竞赛入围奖' },
      { year: '2021', event: '进入5G网络优化岗位实习' },
      { year: '2021', event: '获校级优秀实习生' },
      { year: '2022', event: '获省毕业设计优秀奖' },
      { year: '2022', event: '毕业，任网络优化工程师' }
    ],
    awards: [
      { title: '校级优秀实习生', year: '2021', level: '校级', category: '三好学生' },
      { title: '省毕业设计优秀奖', year: '2022', level: '省级', category: '奖项' }
    ]
  },
  {
    name: '谢雨泽',
    photo: img('Portrait photo of a young Chinese male graduate wearing round glasses and a beige sweater, holding a notebook, university corridor background, warm indoor light, realistic photography'),
    message: '踏实做事，慢慢发光',
    college: '智能建造产业学院',
    gradYear: '2024届',
    current: 'BIM工程师 / 福州',
    studentId: '202105013',
    timeline: [
      { year: '2021', event: '入读工程造价专业' },
      { year: '2022', event: '获校级学习之星' },
      { year: '2023', event: '获福建省BIM应用大赛三等奖' },
      { year: '2023', event: '参与智慧工地实训' },
      { year: '2024', event: '完成BIM建模课程设计' },
      { year: '2024', event: '毕业，进入智能建造企业' }
    ],
    awards: [
      { title: '福建省BIM应用大赛三等奖', year: '2023', level: '省级', category: '奖项' },
      { title: '校级学习之星', year: '2022', level: '校级', category: '三好学生' }
    ]
  },
  {
    name: '蔡佳宁',
    photo: img('Portrait photo of a young Chinese female graduate with a ponytail, wearing a yellow casual jacket, campus sports field background, bright afternoon sunlight, realistic photography'),
    message: '年轻就要多折腾，多尝试',
    college: '数字影视传媒产业学院',
    gradYear: '2025届',
    current: '融媒体运营 / 厦门',
    studentId: '202204014',
    timeline: [
      { year: '2022', event: '入读数字媒体技术专业' },
      { year: '2023', event: '获校级优秀团干部' },
      { year: '2024', event: '获校主持人大赛冠军' },
      { year: '2024', event: '参与融媒体中心实训' },
      { year: '2025', event: '完成校园直播项目实践' },
      { year: '2025', event: '毕业，任融媒体运营' }
    ],
    awards: [
      { title: '校主持人大赛冠军', year: '2024', level: '校级', category: '奖项' },
      { title: '校级优秀团干部', year: '2023', level: '校级', category: '优秀干部' }
    ]
  },
  {
    name: '高子墨',
    photo: img('Portrait photo of a young Chinese male graduate in a black turtleneck, artistic studio workspace background with camera and computer, moody warm lighting, realistic photography'),
    message: '把兴趣做成事业，是最大的幸运',
    college: '游戏产业学院',
    gradYear: '2021届',
    current: '独立游戏制作人 / 成都',
    studentId: '201901015',
    timeline: [
      { year: '2019', event: '入读游戏艺术设计专业' },
      { year: '2020', event: '获校原创游戏角色设计一等奖' },
      { year: '2020', event: '加入独立游戏开发社团' },
      { year: '2021', event: '取得3D建模中级行业证书' },
      { year: '2021', event: '毕业，创立独立游戏小团队' }
    ],
    awards: [
      { title: '校原创游戏角色设计一等奖', year: '2020', level: '校级', category: '奖项' },
      { title: '3D建模中级行业证书', year: '2021', level: '行业', category: '证书' }
    ]
  },
  {
    name: '冯思彤',
    photo: img('Portrait photo of a young Chinese female graduate with wavy hair, wearing a white shirt and dark vest, modern university entrance plaza background, golden hour light, realistic photography'),
    message: '愿你我都在自己的赛道上闪闪发光',
    college: '数字金融产业学院',
    gradYear: '2026届',
    current: '数字金融专业在读 / 福州',
    studentId: '202306016',
    timeline: [
      { year: '2023', event: '入读数字金融专业' },
      { year: '2024', event: '获院级奖学金三等奖' },
      { year: '2024', event: '加入校辩论队' },
      { year: '2025', event: '获校辩论赛最佳辩手' },
      { year: '2026', event: '参与金融数据分析实训项目' }
    ],
    awards: [
      { title: '校辩论赛最佳辩手', year: '2025', level: '校级', category: '奖项' },
      { title: '院级奖学金三等奖', year: '2024', level: '院级', category: '奖学金' }
    ]
  }
];

// 预留：首页默认展示数量（环形展厅已可浏览全部校友，此常量供后续列表页使用）
export const VISIBLE_COUNT = 8;
