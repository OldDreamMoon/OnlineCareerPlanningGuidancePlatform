-- 社区宽屏浏览测试数据补充脚本
-- 目标：补足懒加载、关键词 / 场景 / 标签筛选、回复分布、导师参与、已解决 / 已关闭等前端人工测试样本
-- 特性：按“作者邮箱 + 标题”去重，可重复执行；不会覆盖现有业务数据

SET @seed_now = NOW();

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_community_seed_posts (
  seed_key          VARCHAR(32)  NOT NULL PRIMARY KEY,
  author_email      VARCHAR(255) NOT NULL,
  title             VARCHAR(200) NOT NULL,
  scenario_code     VARCHAR(60)  NOT NULL,
  resolved_status   VARCHAR(20)  NOT NULL,
  moderation_status VARCHAR(20)  NOT NULL,
  risk_level        VARCHAR(20)  NOT NULL,
  tags              VARCHAR(255) NULL,
  content           TEXT         NOT NULL,
  created_at        DATETIME     NOT NULL,
  updated_at        DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_community_seed_posts (
  seed_key, author_email, title, scenario_code, resolved_status, moderation_status, risk_level, tags, content, created_at, updated_at
) VALUES
  ('P001', 'student.liujianing@bishe.local', '简历问诊：实习项目只有后台管理系统，怎么写得更有业务感', 'RESUME_REVIEW', 'OPEN', 'PASS', 'LOW', '简历优化,后台项目,秋招', '我最近在整理一段实习经历，主要做的是后台管理系统里的订单流转和数据维护。问题是写出来总像“增删改查集合”，面试官和学长都说看不出业务价值。想请大家帮我看看，像这种偏内部系统的项目，应该怎么把业务场景、改动价值和个人贡献讲得更像真实产出？', DATE_SUB(@seed_now, INTERVAL 11 DAY), DATE_SUB(@seed_now, INTERVAL 11 DAY)),
  ('P002', 'student.xuanran@bishe.local', '面经复盘：一面被追问缓存一致性，我的回答卡住了', 'INTERVIEW_EXPERIENCE', 'RESOLVED', 'PASS', 'LOW', '缓存一致性,后端面试,复盘', '昨天一面聊到我做过的商城项目，面试官顺着 Redis 缓存问到了“双写不一致”和“延迟更新”。我脑子里有概念，但回答时一直在绕，没有把约束条件、风险边界和最终取舍讲清楚。想复盘一下这种题应该按什么顺序回答，才不会一紧张就变成背术语。', DATE_SUB(@seed_now, INTERVAL 10 DAY), DATE_SUB(@seed_now, INTERVAL 10 DAY)),
  ('P003', 'student.zhoumuyang@bishe.local', '笔试求助：行测和 SQL 混合卷总是来不及', 'WRITTEN_TEST_HELP', 'OPEN', 'PASS', 'LOW', '笔试准备,SQL,时间分配', '最近做了几场数据分析和测开方向的笔试，发现一旦题型里同时出现行测、SQL、逻辑和简答，我就很容易前面耗时过多，后面来不及。想问问大家这种混合卷一般怎么分配时间，会不会先扫一遍题再决定顺序？', DATE_SUB(@seed_now, INTERVAL 9 DAY), DATE_SUB(@seed_now, INTERVAL 9 DAY)),
  ('P004', 'student.heqingyan@bishe.local', 'Offer 比较：银行科技子公司和 SaaS 小厂该怎么选', 'OFFER_COMPARISON', 'OPEN', 'PASS', 'LOW', 'offer选择,后端开发,城市选择', '手上现在有两个方向比较接近的机会，一个是银行科技子公司的后端岗，流程规范、稳定但技术栈偏传统；另一个是做企业服务的 SaaS 小厂，业务更快但节奏会比较猛。我更看重前两年的成长速度，也担心以后跳槽时履历标签被限制，想听听大家会怎么拆这类选择。', DATE_SUB(@seed_now, INTERVAL 8 DAY), DATE_SUB(@seed_now, INTERVAL 8 DAY)),
  ('P005', 'mentor.guhang@bishe.local', '导师分享：群面复盘时一定要记下哪三类信息', 'CAREER_DIRECTION', 'OPEN', 'PASS', 'LOW', '群面,求职规划,导师建议', '很多同学群面结束后只记住了题目和结论，真正能帮下一次发挥更好的，往往是过程信息。我建议至少记录三类内容：每个关键节点是谁推动了讨论、自己什么时候介入最有效、最后方案为什么会被团队接受。把这三类信息记下来，后面写复盘和自我总结都会轻松很多。', DATE_SUB(@seed_now, INTERVAL 7 DAY), DATE_SUB(@seed_now, INTERVAL 7 DAY)),
  ('P006', 'mentor.hanxue@bishe.local', '综合求助：春招中段连续被挂，先改简历还是先补项目', 'GENERAL_HELP', 'OPEN', 'PASS', 'LOW', '春招,项目复盘,简历诊断', '如果你已经投了一段时间，面试反馈却始终集中在“项目浅”“表达虚”“亮点不够具体”，不要同时大面积推翻所有准备。通常更有效的做法，是先选两个最核心的项目把结构和故事线补完整，再回头改简历，不然很容易简历改了很多版，讲出来还是空的。', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 6 HOUR, DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 6 HOUR),
  ('P007', 'student.liujianing@bishe.local', '综合求助：第一次线下面试要提前准备哪些材料', 'GENERAL_HELP', 'CLOSED', 'PASS', 'LOW', '线下面试,材料准备,校招', '我下周要去参加第一次正式的线下面试，现在已经准备了简历和作品，但总觉得还漏了很多细节。想请教下大家一般会提前准备哪些材料和小物件，比如纸笔、成绩单、身份证复印件、项目图之类，有没有什么特别容易被忽略的地方？', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 12 HOUR, DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 12 HOUR),
  ('P008', 'student.xuanran@bishe.local', '简历问诊：校园项目没有真实用户数据，成果怎么表达', 'RESUME_REVIEW', 'OPEN', 'PASS', 'LOW', '简历优化,项目包装,成果表达', '我做过一个校园服务类项目，但实际使用范围比较小，没有真实 DAU、转化率这类数据。现在写简历时总觉得没有数字就会显得很虚。有没有人遇到过类似情况，通常可以从过程指标、效率提升或者协作结果里怎么找可写的点？', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 6 HOUR, DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 6 HOUR),
  ('P009', 'student.zhoumuyang@bishe.local', '面经分享：二面系统设计被问到降级策略，该怎么答', 'INTERVIEW_EXPERIENCE', 'OPEN', 'PASS', 'LOW', '系统设计,降级策略,面试经验', '我发现很多系统设计题最后都会被追问到降级、兜底和监控，但我自己回答时经常只会说“限流熔断降级”，很难结合真实场景展开。想请教下大家，遇到这种追问时，一般会从哪些角度去讲才会更具体？', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 16 HOUR, DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 16 HOUR),
  ('P010', 'student.heqingyan@bishe.local', '笔试求助：转 Java 岗前，C++ STL 还值得花很多时间补吗', 'WRITTEN_TEST_HELP', 'OPEN', 'PASS', 'LOW', 'C++,Java转岗,笔试准备', '我是从竞赛和算法基础转向 Java 后端的，最近刷笔试时发现有些公司题面还是偏 C++ 语境。现在有点纠结，应该继续把 STL 和模板语法补到很熟，还是把时间更多放在 SQL、网络、并发和项目表达上。想听听有类似路线的同学怎么权衡。', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 18 HOUR, DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 18 HOUR),
  ('P011', 'student.liujianing@bishe.local', 'Offer 比较：拿到口头 offer 后，确认清单应该怎么列', 'OFFER_COMPARISON', 'RESOLVED', 'PASS', 'LOW', 'offer选择,薪资沟通,入职时间', '最近第一次拿到口头 offer，开心之余也有点慌，不太清楚接下来应该主动确认哪些信息。除了薪资和入职时间，我还担心试用期、汇报对象、工作地点、培养机制这些点如果不提前问清楚，后面会踩坑。想收集一份更完整的确认清单。', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 8 HOUR, DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 8 HOUR),
  ('P012', 'student.xuanran@bishe.local', '求职规划：产品运营想转数据分析，三个月先补什么', 'CAREER_DIRECTION', 'OPEN', 'PASS', 'LOW', '转岗,数据分析,学习计划', '我现在是产品运营方向，想在下一轮实习前转到数据分析或商业分析岗位。手上能拿得出手的项目还不多，时间也只有三个月左右。想请教下，如果目标是尽快拿到第一份相关实习，应该优先补哪几块：SQL、统计、Excel、可视化、分析报告还是业务 case？', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 22 HOUR, DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 22 HOUR),
  ('P013', 'student.zhoumuyang@bishe.local', '综合求助：海投一周几乎没反馈，优先排查哪些问题', 'GENERAL_HELP', 'OPEN', 'PASS', 'LOW', '投递反馈,简历优化,海投', '最近集中海投了一周，几乎没什么笔试和面试反馈。我现在不太确定应该先查岗位匹配度、简历关键词、投递时间，还是项目内容本身太空。想请教下大家，如果只能优先排查两三件事，通常从哪里下手最有效？', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 10 HOUR, DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 10 HOUR),
  ('P014', 'student.heqingyan@bishe.local', '简历问诊：接口联调和排查问题，怎么写成个人贡献', 'RESUME_REVIEW', 'RESOLVED', 'PASS', 'LOW', '接口联调,项目包装,个人贡献', '我在项目里经常负责和前端、测试联调，也处理过不少线上和预发环境的问题排查。但写简历时总觉得这些事看起来像“配合工作”，没有那么像核心贡献。想问下大家，这类经历怎么写，才能体现自己的判断力和推进能力，而不是流水账。', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 20 HOUR, DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 20 HOUR),
  ('P015', 'mentor.guhang@bishe.local', '面经分享：导师最想听到的项目复盘，不只是技术细节', 'INTERVIEW_EXPERIENCE', 'OPEN', 'PASS', 'LOW', '导师建议,项目复盘,表达能力', '很多同学在复盘项目时，能讲清技术栈，却讲不清为什么当时那样选、出了问题后怎么修正。真正能拉开差距的项目表达，通常包含三个层次：背景和目标、关键决策、失败与修正。面试官更想听到的是你如何判断，而不是你记住了多少术语。', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 6 HOUR, DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 6 HOUR),
  ('P016', 'mentor.hanxue@bishe.local', 'Offer 比较：平台大厂边缘组和中厂核心岗，怎么看成长性', 'OFFER_COMPARISON', 'CLOSED', 'PASS', 'LOW', 'offer选择,成长路径,团队氛围', '如果你在纠结“大厂边缘组”和“中厂核心岗”，建议不要只看品牌或 title。前两年的成长速度，往往更受业务节奏、带教质量、任务密度和你能不能接触核心链路影响。把这些因素拆开比较，比一句“平台大 / 机会多”更有意义。', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 22 HOUR, DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 22 HOUR),
  ('P017', 'student.liujianing@bishe.local', '笔试求助：测开岗常见 SQL 题该怎么系统准备', 'WRITTEN_TEST_HELP', 'OPEN', 'PASS', 'LOW', '测试开发,SQL,笔试准备', '最近在看测开和质量平台相关岗位，发现很多笔试题里 SQL 占比很高。我现在会基础查询和 join，但一到窗口函数、复杂分组和业务场景题就容易卡住。想问下有没有一条更系统的准备路径，最好能兼顾面试和笔试。', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 8 HOUR, DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 8 HOUR),
  ('P018', 'student.xuanran@bishe.local', '求职规划：毕业设计和春招撞车，时间怎么拆更稳', 'CAREER_DIRECTION', 'OPEN', 'PASS', 'LOW', '时间管理,春招,毕业设计', '最近明显感觉毕业设计和春招准备在争时间，一边想把项目做得更完整，一边又怕错过投递和笔试节奏。现在每天都很忙，但又觉得两边都推进得不够深。想请教大家，如果只能保证每天固定几段高质量时间，该怎么拆，才不会一直处在焦虑里？', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 2 HOUR, DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 2 HOUR),
  ('P019', 'student.zhoumuyang@bishe.local', '综合求助：非科班转前端，第一份实习更看重什么', 'GENERAL_HELP', 'OPEN', 'PASS', 'LOW', '前端转岗,实习准备,非科班', '我是非科班转前端，最近在补 React、工程化和项目作品，但总担心自己和科班同学比会吃亏。想知道第一份前端实习通常更看重哪几个点：基础知识、作品完成度、沟通协作、还是学习速度？如果只能优先补两块，应该怎么选？', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 6 HOUR, DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 6 HOUR),
  ('P020', 'student.heqingyan@bishe.local', '简历问诊：项目里做过埋点分析，这段经历怎么写', 'RESUME_REVIEW', 'OPEN', 'PASS', 'LOW', '数据埋点,简历优化,项目经历', '我在实习里参与过埋点设计和数据回看，也基于看板提过一些页面优化建议，但这段经历写在简历里总觉得不够成体系。想请大家帮我看看，这种偏数据支持和分析判断的工作，怎么写才能体现价值，而不是像“做过一些埋点统计”。', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 12 HOUR, DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 12 HOUR),
  ('P021', 'student.liujianing@bishe.local', '收费内推渠道整理，需要的同学自取', 'GENERAL_HELP', 'OPEN', 'REVIEW', 'HIGH', '收费内推,导流信息,风险内容', '手里整理了几条收费内推渠道和带投入口，想了解的同学可以先在评论里留一下联系方式，我再统一发群和表单。', DATE_SUB(@seed_now, INTERVAL 20 HOUR), DATE_SUB(@seed_now, INTERVAL 20 HOUR)),
  ('P022', 'student.xuanran@bishe.local', '简历代投服务报价整理，留下联系方式可细聊', 'RESUME_REVIEW', 'OPEN', 'REVIEW', 'HIGH', '简历代投,导流信息,风险内容', '最近有人问代投服务怎么收费，我把几档“简历修改 + 代投跟进”的报价整理了一下。想看的可以先留外部联系方式，我这边再发完整说明。', DATE_SUB(@seed_now, INTERVAL 16 HOUR), DATE_SUB(@seed_now, INTERVAL 16 HOUR)),
  ('P023', 'student.zhoumuyang@bishe.local', '笔试原题售卖群入口汇总，想进的自行领取', 'WRITTEN_TEST_HELP', 'OPEN', 'REVIEW', 'HIGH', '笔试原题,导流信息,风险内容', '最近有人问原题群入口，我手里有几个更新比较快的群和资料包，想进的可以在评论区留言，我再统一拉群。', DATE_SUB(@seed_now, INTERVAL 12 HOUR), DATE_SUB(@seed_now, INTERVAL 12 HOUR)),
  ('P024', 'student.heqingyan@bishe.local', 'Offer 比较帖下有人私聊收费咨询，管理员可以帮看下吗', 'OFFER_COMPARISON', 'OPEN', 'REVIEW', 'MEDIUM', 'offer选择,收费咨询,风险判断', '我刚发了一条 offer 对比求助，结果有人私聊我说可以付费帮做决策分析。我不确定这类消息算不算正常咨询，先把情况同步出来，请管理员帮忙看看。', DATE_SUB(@seed_now, INTERVAL 8 HOUR), DATE_SUB(@seed_now, INTERVAL 8 HOUR));

INSERT INTO posts (
  user_id, title, scenario_code, resolved_status, content, tags, moderation_status, risk_level, last_moderation_event_id, is_deleted, created_at, updated_at
)
SELECT
  u.id,
  s.title,
  s.scenario_code,
  s.resolved_status,
  s.content,
  s.tags,
  s.moderation_status,
  s.risk_level,
  NULL,
  0,
  s.created_at,
  s.updated_at
FROM tmp_community_seed_posts s
JOIN users u ON u.email = s.author_email AND u.is_deleted = 0
LEFT JOIN posts p ON p.user_id = u.id AND p.title = s.title AND p.is_deleted = 0
WHERE p.id IS NULL;

CREATE TEMPORARY TABLE tmp_community_seed_post_ids AS
SELECT
  s.seed_key,
  p.id AS post_id
FROM tmp_community_seed_posts s
JOIN users u ON u.email = s.author_email AND u.is_deleted = 0
JOIN posts p ON p.user_id = u.id AND p.title = s.title AND p.is_deleted = 0;

CREATE TEMPORARY TABLE tmp_community_seed_comments (
  seed_key          VARCHAR(32)  NOT NULL,
  author_email      VARCHAR(255) NOT NULL,
  is_ai             TINYINT(1)   NOT NULL DEFAULT 0,
  moderation_status VARCHAR(20)  NOT NULL,
  risk_level        VARCHAR(20)  NOT NULL,
  content           TEXT         NOT NULL,
  created_at        DATETIME     NOT NULL,
  updated_at        DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_community_seed_comments (
  seed_key, author_email, is_ai, moderation_status, risk_level, content, created_at, updated_at
) VALUES
  ('P001', 'mentor.guhang@bishe.local', 0, 'PASS', 'LOW', '先把项目服务的对象和场景写出来，再补你负责的关键动作，最后用一两句结果指标收尾，会比直接写技术栈更像真实业务。', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 4 HOUR, DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 4 HOUR),
  ('P001', 'student.xuanran@bishe.local', 0, 'PASS', 'LOW', '我之前会把“把手工处理从半小时压到五分钟”这种结果单独拎出来，效果会比一段大段描述更直观。', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 6 HOUR, DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 6 HOUR),
  ('P001', 'mentor.guhang@bishe.local', 1, 'PASS', 'LOW', '可以先试一版“背景、任务、动作、结果”四句结构，再把每一句对应到一个真实细节上。', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 8 HOUR, DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 8 HOUR),
  ('P002', 'student.liujianing@bishe.local', 0, 'PASS', 'LOW', '我后来发现先交代一致性要求和业务约束，再讲为什么选这个方案，会比直接背概念更顺。', DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 3 HOUR, DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 3 HOUR),
  ('P002', 'mentor.hanxue@bishe.local', 0, 'PASS', 'LOW', '这类题别急着把答案说满，先把“允许多大延迟、是否能接受短时间脏读”这类前提抛出来。', DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 5 HOUR, DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 5 HOUR),
  ('P004', 'mentor.guhang@bishe.local', 0, 'PASS', 'LOW', '如果你更看重前两年成长速度，可以优先比较带教密度、业务复杂度和你能不能接触核心链路。', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 9 HOUR, DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 9 HOUR),
  ('P005', 'student.liujianing@bishe.local', 0, 'PASS', 'LOW', '我以前群面只记题目，后来开始补“谁推动了讨论、最后为什么定这个结论”，复盘价值确实高很多。', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 21 HOUR, DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 21 HOUR),
  ('P005', 'student.heqingyan@bishe.local', 0, 'PASS', 'LOW', '再加一列“如果重来一次我会怎么做”，后面写总结和自我介绍都会轻松很多。', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 23 HOUR, DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 23 HOUR),
  ('P006', 'student.zhoumuyang@bishe.local', 0, 'PASS', 'LOW', '我最近就是先集中改两个核心项目，再回头调简历结构，反馈会比以前更集中。', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 12 HOUR, DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 12 HOUR),
  ('P007', 'student.xuanran@bishe.local', 0, 'PASS', 'LOW', '我会提前准备简历、纸笔、身份证、成绩单电子版和一个简短的项目提纲，现场会安心很多。', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 15 HOUR, DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 15 HOUR),
  ('P008', 'mentor.hanxue@bishe.local', 0, 'PASS', 'LOW', '没有真实用户量也可以写过程指标，比如埋点覆盖率、排查效率、协作范围和交付节奏。', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 9 HOUR, DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 9 HOUR),
  ('P009', 'mentor.guhang@bishe.local', 0, 'PASS', 'LOW', '降级策略最好按“触发条件、保留什么、牺牲什么、如何恢复”四句讲清楚。', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 20 HOUR, DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 20 HOUR),
  ('P009', 'student.liujianing@bishe.local', 0, 'PASS', 'LOW', '我被问到时会先说核心链路不能断，再解释哪些功能可以延后或兜底，这样不容易发散。', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 21 HOUR, DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 21 HOUR),
  ('P011', 'mentor.hanxue@bishe.local', 0, 'PASS', 'LOW', '至少确认薪资结构、试用期、汇报对象、入职时间和工作地点这五项，很多人会漏掉其中两三项。', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 10 HOUR, DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 10 HOUR),
  ('P013', 'student.heqingyan@bishe.local', 0, 'PASS', 'LOW', '我之前排查出来主要是岗位不聚焦、简历标题太虚、项目关键词不够贴 JD。可以先从这三项查。', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 16 HOUR, DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 16 HOUR),
  ('P015', 'student.xuanran@bishe.local', 0, 'PASS', 'LOW', '我以前总是只讲做了什么，后来刻意补“为什么这样做、后来怎么看这个选择”，表达确实更完整了。', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 12 HOUR, DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 12 HOUR),
  ('P015', 'student.zhoumuyang@bishe.local', 0, 'PASS', 'LOW', '如果还能再补一两个“当时踩坑后怎么修正”的例子，面试官应该会更容易追问出深度。', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 13 HOUR, DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 13 HOUR),
  ('P016', 'student.liujianing@bishe.local', 0, 'PASS', 'LOW', '我最近也在纠结这个，感觉团队氛围和能不能被持续带着成长，比 title 本身更重要。', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 18 HOUR, DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 18 HOUR),
  ('P018', 'mentor.hanxue@bishe.local', 0, 'PASS', 'LOW', '先给春招留固定高质量时段，毕业设计拆成小里程碑推进，不要两边都想全天推进。', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 4 HOUR, DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 4 HOUR),
  ('P019', 'student.heqingyan@bishe.local', 0, 'PASS', 'LOW', '第一份实习更看重基础、沟通和可交付，不一定先追求技术栈多新，把一个作品讲透反而更重要。', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 10 HOUR, DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 10 HOUR),
  ('P021', 'student.zhoumuyang@bishe.local', 0, 'REVIEW', 'HIGH', '我这边也有付费内推群和外部表单入口，想要的同学可以直接私聊我。', DATE_SUB(@seed_now, INTERVAL 19 HOUR), DATE_SUB(@seed_now, INTERVAL 19 HOUR)),
  ('P022', 'student.heqingyan@bishe.local', 0, 'REVIEW', 'HIGH', '这类代投一般都走外部联系方式，先确认预算再聊具体方案。', DATE_SUB(@seed_now, INTERVAL 15 HOUR), DATE_SUB(@seed_now, INTERVAL 15 HOUR));

INSERT INTO comments (
  post_id, user_id, content, is_ai, moderation_status, risk_level, last_moderation_event_id, is_deleted, created_at, updated_at
)
SELECT
  ids.post_id,
  u.id,
  c.content,
  c.is_ai,
  c.moderation_status,
  c.risk_level,
  NULL,
  0,
  c.created_at,
  c.updated_at
FROM tmp_community_seed_comments c
JOIN tmp_community_seed_post_ids ids ON ids.seed_key = c.seed_key
JOIN users u ON u.email = c.author_email AND u.is_deleted = 0
LEFT JOIN comments existing
  ON existing.post_id = ids.post_id
 AND existing.user_id = u.id
 AND existing.is_ai = c.is_ai
 AND existing.content = c.content
 AND existing.is_deleted = 0
WHERE existing.id IS NULL;

CREATE TEMPORARY TABLE tmp_community_seed_likes (
  seed_key     VARCHAR(32)  NOT NULL,
  actor_email  VARCHAR(255) NOT NULL,
  created_at   DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_community_seed_likes (seed_key, actor_email, created_at) VALUES
  ('P001', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 9 HOUR),
  ('P001', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 10 HOUR),
  ('P001', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 11 HOUR),
  ('P001', 'mentor.guhang@bishe.local', DATE_SUB(@seed_now, INTERVAL 10 DAY) + INTERVAL 12 HOUR),
  ('P002', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 6 HOUR),
  ('P002', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 7 HOUR),
  ('P002', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 9 DAY) + INTERVAL 8 HOUR),
  ('P003', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 8 DAY) + INTERVAL 11 HOUR),
  ('P003', 'mentor.guhang@bishe.local', DATE_SUB(@seed_now, INTERVAL 8 DAY) + INTERVAL 12 HOUR),
  ('P004', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 11 HOUR),
  ('P004', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 12 HOUR),
  ('P004', 'mentor.guhang@bishe.local', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 13 HOUR),
  ('P004', 'mentor.hanxue@bishe.local', DATE_SUB(@seed_now, INTERVAL 7 DAY) + INTERVAL 14 HOUR),
  ('P005', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 23 HOUR),
  ('P005', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 23 HOUR + INTERVAL 20 MINUTE),
  ('P006', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 13 HOUR),
  ('P006', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 13 HOUR + INTERVAL 20 MINUTE),
  ('P006', 'student.linjiaqi@bishe.local', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 13 HOUR + INTERVAL 40 MINUTE),
  ('P007', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 6 DAY) + INTERVAL 16 HOUR),
  ('P008', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 10 HOUR),
  ('P008', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 11 HOUR),
  ('P008', 'mentor.guhang@bishe.local', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 12 HOUR),
  ('P009', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 22 HOUR),
  ('P009', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 22 HOUR + INTERVAL 20 MINUTE),
  ('P009', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 5 DAY) + INTERVAL 22 HOUR + INTERVAL 40 MINUTE),
  ('P010', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 2 HOUR),
  ('P011', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 11 HOUR),
  ('P011', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 12 HOUR),
  ('P011', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 13 HOUR),
  ('P011', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 4 DAY) + INTERVAL 14 HOUR),
  ('P012', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 1 HOUR),
  ('P012', 'mentor.guhang@bishe.local', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 2 HOUR),
  ('P013', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 17 HOUR),
  ('P013', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 18 HOUR),
  ('P014', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 11 HOUR),
  ('P014', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 3 DAY) + INTERVAL 12 HOUR),
  ('P015', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 14 HOUR),
  ('P015', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 15 HOUR),
  ('P015', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 16 HOUR),
  ('P016', 'student.linjiaqi@bishe.local', DATE_SUB(@seed_now, INTERVAL 2 DAY) + INTERVAL 19 HOUR),
  ('P017', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 2 HOUR),
  ('P017', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 3 HOUR),
  ('P017', 'student.heqingyan@bishe.local', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 4 HOUR),
  ('P018', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 5 HOUR),
  ('P018', 'student.linjiaqi@bishe.local', DATE_SUB(@seed_now, INTERVAL 1 DAY) + INTERVAL 6 HOUR),
  ('P019', 'student.liujianing@bishe.local', DATE_SUB(@seed_now, INTERVAL 20 HOUR)),
  ('P020', 'student.xuanran@bishe.local', DATE_SUB(@seed_now, INTERVAL 14 HOUR)),
  ('P020', 'student.zhoumuyang@bishe.local', DATE_SUB(@seed_now, INTERVAL 13 HOUR));

INSERT IGNORE INTO post_likes (post_id, user_id, created_at)
SELECT
  ids.post_id,
  u.id,
  l.created_at
FROM tmp_community_seed_likes l
JOIN tmp_community_seed_post_ids ids ON ids.seed_key = l.seed_key
JOIN users u ON u.email = l.actor_email AND u.is_deleted = 0;

COMMIT;

SELECT
  COUNT(*) AS posts_total,
  SUM(CASE WHEN moderation_status = 'PASS' THEN 1 ELSE 0 END) AS posts_visible,
  SUM(CASE WHEN moderation_status = 'REVIEW' THEN 1 ELSE 0 END) AS posts_in_review
FROM posts;

SELECT
  scenario_code,
  moderation_status,
  resolved_status,
  COUNT(*) AS count_per_bucket
FROM posts
GROUP BY scenario_code, moderation_status, resolved_status
ORDER BY scenario_code, moderation_status, resolved_status;

SELECT COUNT(*) AS comments_total FROM comments;
SELECT COUNT(*) AS likes_total FROM post_likes;
