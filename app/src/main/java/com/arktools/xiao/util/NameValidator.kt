package com.arktools.xiao.util

/**
 * 玩家自定义名称（学校名 / 校长名 / 学生名等）的合规校验。
 *
 * 为什么要做：
 * - 玩家可以把学校命名成现实中的真实高校、机构、政治人物或敏感词，
 *   这些内容会进入截图、排行榜和社区分享，属于必须拦下的合规风险；
 * - 同时也要挡住联系方式（QQ/微信/手机号）这类导流信息。
 *
 * 判定策略（宁可宽松，不要误伤正常取名）：
 * 1. 直接命中敏感词表 → 拦截；
 * 2. 归一化后再判定一次（去空格/标点、全角转半角、去重复字），
 *    防止用"习 近 平"、"习-近-平"、"习近平！"这类写法绕过；
 * 3. 中英文混排的联系方式用正则拦截。
 */
object NameValidator {

    /** 校验结果 */
    data class Result(val ok: Boolean, val reason: String = "") {
        companion object {
            val OK = Result(true)
            fun reject(reason: String) = Result(false, reason)
        }
    }

    /**
     * 敏感词表。
     *
     * 说明：这里只收录"命名场景下绝不可能合法"的词——政治人物与领导人、
     * 现当代政治敏感组织/事件、真实高校与政府机构、以及明确的社会敏感词。
     * 不收录普通词汇，避免玩家给学校起"清华大学附属"这类半截名字时被误伤
     * （"清华大学"本身仍会被拦，因为它是真实高校全名）。
     */
    private val BLOCKED_WORDS: List<String> = listOf(
        // ===== 中国领导人与重要政治人物 =====
        "习近平", "习近", "近平", "毛泽东", "毛泽东", "周恩来", "朱德", "刘少奇",
        "邓小平", "陈云", "叶剑英", "李先念", "杨尚昆", "江泽民", "胡锦涛",
        "赵紫阳", "华国锋", "林彪", "四人帮", "江青", "张春桥", "姚文元", "王洪文",
        "李克强", "李鹏", "朱镕基", "温家宝", "吴邦国", "贾庆林", "曾庆红",
        "李岚清", "罗干", "吴官正", "李长春", "周永康", "令计划", "薄熙来",
        "徐才厚", "郭伯雄", "孙政才", "房峰辉", "张阳",
        "孙中山", "蒋介石", "蒋中正", "蒋经国", "宋美龄", "袁世凯", "李鸿章",
        "张学良", "阎锡山", "冯玉祥", "汪精卫", "溥仪",
        // ===== 其他国家和地区领导人（真实姓名，命名场景无必要） =====
        "普京", "拜登", "特朗普", "奥巴马", "克林顿", "布什", "林肯", "华盛顿",
        "罗斯福", "肯尼迪", "尼克松", "里根", "丘吉尔", "撒切尔", "默克尔",
        "马克龙", "安倍晋三", "岸田文雄", "金正恩", "金日成", "金正日",
        "泽连斯基", "普京",
        // ===== 现当代政治敏感组织 / 事件 =====
        "法轮功", "法轮", "六四", "天安门事件", "八九", "反右", "文化大革命",
        "文革", "红卫兵", "台独", "港独", "藏独", "疆独", "东突", "民运",
        "颜色革命", "邪教", "全能神", "藏青会", "世维会",
        // ===== 真实高校（玩家自建大学不应与真实院校同名） =====
        "清华大学", "北京大学", "复旦", "上海交通大学", "浙江大学", "南京大学",
        "中国科学技术大学", "哈尔滨工业大学", "西安交通大学", "中山大学",
        "武汉大学", "华中科技大学", "四川大学", "山东大学", "吉林大学",
        "南开大学", "天津大学", "东南大学", "同济大学", "北京师范大学",
        "中国人民大学", "北京航空航天大学", "北京理工大学", "西北工业大学",
        "厦门大学", "中南大学", "湖南大学", "重庆大学", "兰州大学", "东北大学",
        "电子科技大学", "华南理工大学", "大连理工大学", "中国农业大学",
        "中央民族大学", "国防科技大学", "华东师范大学", "中国海洋大学",
        "暨南大学", "苏州大学", "郑州大学", "深圳大学", "上海大学",
        "香港大学", "香港中文大学", "香港科技大学", "台湾大学", "国立台湾大学",
        "澳门大学", "哈佛", "耶鲁", "斯坦福", "麻省理工", "剑桥大学", "牛津大学",
        "普林斯顿", "哥伦比亚大学", "加州大学", "东京大学", "早稻田",
        // ===== 真实中小学 / 机构 =====
        "衡水中学", "人大附中", "北京四中", "新东方", "学而思", "猿辅导",
        // ===== 政府与党政机构 =====
        "中共中央", "国务院", "教育部", "公安部", "安全部", "国防部",
        "中央军委", "政治局", "中央委员会", "人民代表大会", "政协",
        "纪委", "监察委", "最高人民法院", "最高人民检察院",
        "共产党", "国民党", "民进党", "共和党", "民主党",
        // ===== 军事与暴力敏感 =====
        "解放军", "中国人民解放军", "武警", "国安局", "军情", "中央情报局",
        "伊斯兰国", "基地组织", "塔利班", "恐怖组织", "圣战",
        // ===== 宗教敏感 =====
        "达赖", "班禅", "喇嘛", "伊斯兰", "圣战组织",
        // ===== 社会敏感 / 违法内容 =====
        "毒品", "冰毒", "海洛因", "大麻", "摇头丸", "枪支", "军火",
        "赌博", "博彩", "色情", "成人", "嫖娼", "卖淫", "约炮",
        "自杀", "自残", "暴恐", "恐怖袭击", "爆炸物", "炸弹",
        "诈骗", "洗钱", "传销", "高利贷", "走私", "贩毒",
        // ===== 侮辱性词汇 =====
        "傻逼", "煞笔", "沙比", "弱智", "智障", "脑残", "白痴", "废物",
        "滚蛋", "去死", "你妈", "他妈", "操你", "草你", "干你", "贱人",
        "贱货", "婊子", "妓女", "杂种", "畜生", "狗东西", "王八蛋",
        "死全家", "全家死", "垃圾学校", "野鸡大学",
        // ===== 英文敏感词 =====
        "fuck", "fucking", "shit", "bitch", "asshole", "bastard", "dick",
        "cunt", "whore", "slut", "nigger", "nigga", "retard", "porn",
        "sex", "nazi", "hitler", "isis", "terrorist", "taliban",
        "xi jinping", "mao zedong", "putin", "trump", "biden",
        "tiananmen", "falun", "falungong",
        // ===== 导流 / 违规用途 =====
        "加微信", "加qq", "扫码", "私聊", "代练", "外挂", "脚本挂",
        "刷单", "兼职日结", "加群"
    )

    /**
     * 归一化：全角转半角、去空白与常见分隔符、转小写、去重复相邻字。
     * 用于绕过检测（"习 近 平" / "习-近-平" / "习习近近平平"）。
     */
    private fun normalize(input: String): String {
        val halfWidth = buildString(input.length) {
            input.forEach { ch ->
                when {
                    ch.code == 0x3000 -> append(' ')                       // 全角空格
                    ch.code in 0xFF01..0xFF5E -> append(ch.code - 0xFEE0)  // 全角 ASCII
                    else -> append(ch)
                }
            }
        }
        val stripped = halfWidth.filterNot { it.isWhitespace() || it in SEPARATORS }
        val lowered = stripped.lowercase()
        // 折叠连续重复字符：习习近近平平 -> 习近平
        val folded = StringBuilder(lowered.length)
        lowered.forEach { ch ->
            if (folded.isEmpty() || folded.last() != ch) folded.append(ch)
        }
        return folded.toString()
    }

    private val SEPARATORS = charArrayOf(
        '-', '_', '.', ',', '·', '•', '*', '/', '\\', '|', '+', '=', '~', '^',
        '(', ')', '[', ']', '{', '}', '<', '>', '!', '?', ':', ';', '"', '\'',
        '、', '。', '，', '！', '？', '：', '；', '「', '」', '『', '』', '《', '》',
        '（', '）', '【', '】', '～', '＠', '@', '#', '&', '$', '%'
    )

    /** 联系方式正则：手机号、QQ、微信、邮箱 */
    private val CONTACT_PATTERNS = listOf(
        Regex("""1[3-9]\d{9}"""),                 // 手机号
        Regex("""\d{5,12}"""),                    // 纯长数字（QQ 号等）
        Regex("""(?i)(qq|wx|weixin|wechat)\s*\d{3,}"""),
        Regex("""[\w.\-]+@[\w\-]+\.[a-z]{2,}"""),  // 邮箱
        Regex("""(?i)(https?://|www\.)""")
    )

    /** 名称长度上限（学校名 / 校长名共用，具体由调用方再收紧） */
    const val MAX_SCHOOL_NAME = 12
    const val MAX_PRINCIPAL_NAME = 6

    /**
     * 校验玩家输入的名称。
     * @param text 待校验文本
     * @param fieldName 字段中文名（用于错误提示，如"大学名称"）
     */
    fun validate(text: String, fieldName: String = "名称"): Result {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.reject("$fieldName不能为空")
        }
        if (trimmed.length < 2) {
            return Result.reject("$fieldName至少 2 个字")
        }

        val normalized = normalize(trimmed)
        if (normalized.isEmpty()) {
            return Result.reject("$fieldName含有无效字符")
        }

        BLOCKED_WORDS.firstOrNull { word ->
            val w = normalize(word)
            w.isNotEmpty() && (normalized.contains(w) || trimmed.lowercase().contains(word.lowercase()))
        }?.let { hit ->
            return Result.reject("$fieldName含有不允许的词语，请换一个")
        }

        CONTACT_PATTERNS.firstOrNull { it.containsMatchIn(trimmed) }?.let {
            return Result.reject("$fieldName不能包含联系方式或网址")
        }

        // 全符号 / 无意义输入
        if (trimmed.none { it.isLetterOrDigit() }) {
            return Result.reject("$fieldName请包含文字或数字")
        }

        return Result.OK
    }

    /** 便捷方法：只判断是否通过 */
    fun isValid(text: String, fieldName: String = "名称"): Boolean =
        validate(text, fieldName).ok
}
