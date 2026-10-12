/api/v1
│
├── 第一章  通用规范
│
├── 第二章  /auth              用户认证 # Finish
│
├── 第三章  /dicts             基础字典 # Finish
│
├── 第四章  /archives          校友档案
│
├── 第五章  /honors            校友荣誉 # Finish
│
├── 第六章  /attachments       附件管理 # Finish
│
├── 第七章  /reviews           审核中心 # Finish
│
├── 第八章  /showcase  档案上架管理（写端） # Finish
│          /screen    大屏数据展示（读端）        展览馆/大屏
│
├── 第九章  /consents          隐私授权 # Finish
│
├── 第十章  /stats             数据统计 # Finish
│
├── 第十一章 /imports          批量导入
│
├── 第十二章 /drafts           草稿管理 # Finish
│
└── 扩展模块  /announcements   公告管理 # Finish
             （写：college_admin/school_admin；读：登录角色，非管理员仅见 published）
              POST/GET/PATCH/DELETE /announcements，status: draft→published↔withdrawn
             [M13]

             /alumni            优秀校友 + 校友档案索引（公开读端） # Finish
             （免认证；隐私红线同 /screen/feed——仅 approved+上架+consents(screen) 数据）
              GET /alumni/outstanding?limit=      优秀校友展厅（仅 published，display_order 升序）
              GET /alumni/directory               校友档案索引检索
                 （keyword 命中姓名/学号/学院/职位/荣誉；name/grad_year/college_id/student_id 组合 + 分页）
              GET /alumni/directory/{student_id}   公开档案详情（弹窗共用；精选校友出编辑时间线，
                                                   非精选由公开档案聚合 + approved 荣誉）
             [M14] outstanding（优秀校友精选位）/ [M15] directory（档案索引聚合，不建表）

             /admin/outstanding-alumni  优秀校友精选位管理（写端） # Finish
             （仅 school_admin；一人一位 uk_outstanding_user；重复添加 409 INVALID_STATE）
              GET    /admin/outstanding-alumni?status=&page=&page_size=   管理列表（含 draft/withdrawn）
              POST   /admin/outstanding-alumni          新增精选位（姓名/学院/届次缺省从学籍自动补全）
              PATCH  /admin/outstanding-alumni/{id}     修改展示信息/排序/上下架（status）
              DELETE /admin/outstanding-alumni/{id}     移除精选位
             [M14]

             /wall/messages     留言墙（公开读 + 登录投递/撤回） # Finish
             （读：免登录，仅展示 approved；写：登录角色，新留言一律 pending）
              GET    /wall/messages?page=&page_size=         公开列表（默认 50 上限 100）
              GET    /wall/messages/mine                    我的留言（全部状态，登录）
              POST   /wall/messages                          提交留言（content 必填，201）
              DELETE /wall/messages/{id}                     撤回自己的留言
             /admin/wall/messages  留言墙审核管理（写端） # Finish
             （仅 school_admin/system_admin）
              GET    /admin/wall/messages?status=&keyword=&page=&page_size=   审核队列
              PUT    /admin/wall/messages/{id}/review       审核（action=approve|reject，reject 需 note）
             [M16]

十三章增加Announcement 公告管理模块
