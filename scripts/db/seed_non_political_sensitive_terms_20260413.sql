SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_sensitive_term_seed_20260413 (
  term         VARCHAR(200) NOT NULL,
  term_type    VARCHAR(50)  NOT NULL,
  risk_level   VARCHAR(20)  NOT NULL,
  action       VARCHAR(20)  NOT NULL,
  source_scope VARCHAR(50)  NOT NULL,
  is_whitelist TINYINT(1)   NOT NULL DEFAULT 0,
  enabled      TINYINT(1)   NOT NULL DEFAULT 1
);

INSERT INTO tmp_sensitive_term_seed_20260413(term, term_type, risk_level, action, source_scope, is_whitelist, enabled)
VALUES
  ('约炮', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('成人视频', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('成人视频资源', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('成人视频链接', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('成人视频下载', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('成人直播', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('裸聊', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('招嫖', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('嫖娼', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('援交', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('一夜情', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('性交易', 'PORNOGRAPHY', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),

  ('爆炸物制作', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('自制炸弹', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('恐怖袭击', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('极端组织', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('圣战组织', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('袭击平民', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('制造爆炸装置', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('恐袭预告', 'TERROR', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),

  ('买卖枪支', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('出售枪支', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('自制枪支', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('贩卖毒品', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('出售毒品', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('冰毒出售', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('代开发票', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('假证办理', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('办假证', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('洗钱通道', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('银行卡四件套', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('跑分平台', 'ILLEGAL', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),

  ('杀了你', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('砍死你', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('捅死你', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('灭门', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('买凶', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('报复社会', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('持刀伤人', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('校园枪击', 'VIOLENCE', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),

  ('刷单返利', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('代办贷款', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('无抵押秒批', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('投资内幕群', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('高收益稳赚', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('带你赚钱', 'FRAUD', 'HIGH', 'REVIEW', 'COMMUNITY_POST', 0, 1),
  ('带你赚钱', 'FRAUD', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('稳赚不赔', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('先交保证金', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('代提现吗', 'FRAUD', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('跑分接单', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),
  ('兼职日结刷单', 'FRAUD', 'CRITICAL', 'BLOCK', 'ALL', 0, 1),

  ('加微信领取资料', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_POST', 0, 1),
  ('加微信领取资料', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('扫码进群', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_POST', 0, 1),
  ('扫码进群', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('点击链接购买', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_POST', 0, 1),
  ('点击链接购买', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('私聊发你资源', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('VX联系', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_POST', 0, 1),
  ('VX联系', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('Telegram联系', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_POST', 0, 1),
  ('Telegram联系', 'ADVERTISEMENT', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),

  ('废物', 'ABUSE', 'MEDIUM', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('滚出去', 'ABUSE', 'MEDIUM', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('脑残', 'ABUSE', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('去死吧', 'ABUSE', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('人渣', 'ABUSE', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('垃圾东西', 'ABUSE', 'MEDIUM', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('傻逼', 'ABUSE', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('傻叉', 'ABUSE', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('蠢货', 'ABUSE', 'MEDIUM', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),
  ('狗东西', 'ABUSE', 'HIGH', 'REVIEW', 'COMMUNITY_COMMENT', 0, 1),

  ('反诈中心', 'FRAUD', 'LOW', 'PASS', 'ALL', 1, 1),
  ('扫黄打非', 'ILLEGAL', 'LOW', 'PASS', 'ALL', 1, 1),
  ('禁毒宣传', 'ILLEGAL', 'LOW', 'PASS', 'ALL', 1, 1),
  ('反诈骗提醒', 'FRAUD', 'LOW', 'PASS', 'ALL', 1, 1),
  ('anti-fraud', 'FRAUD', 'LOW', 'PASS', 'ALL', 1, 1),
  ('fraud warning', 'FRAUD', 'LOW', 'PASS', 'ALL', 1, 1);

INSERT INTO sensitive_terms(term, term_type, risk_level, action, source_scope, is_whitelist, enabled, created_at, updated_at)
SELECT seed.term,
       seed.term_type,
       seed.risk_level,
       seed.action,
       seed.source_scope,
       seed.is_whitelist,
       seed.enabled,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
  FROM tmp_sensitive_term_seed_20260413 seed
 WHERE NOT EXISTS (
       SELECT 1
         FROM sensitive_terms existing
        WHERE existing.term = seed.term
          AND existing.source_scope = seed.source_scope
          AND existing.is_whitelist = seed.is_whitelist
 );

DROP TEMPORARY TABLE tmp_sensitive_term_seed_20260413;
