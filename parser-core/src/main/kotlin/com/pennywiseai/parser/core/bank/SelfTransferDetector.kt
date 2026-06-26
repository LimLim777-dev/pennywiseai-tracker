package com.pennywiseai.parser.core.bank

/**
 * 判断一笔交易的对方姓名是不是户主本人——
 * 用于在马来西亚多个银行/钱包之间互转时，
 * 把交易标成 TRANSFER（内部转账）而不是 INCOME/EXPENSE，
 * 避免左手倒右手的钱被重复计入月度收支统计。
 * 每个账户自己的交易记录和余额完全不受影响，只影响统计分类。
 */
object SelfTransferDetector {

    // 在这里维护户主本人在各银行/钱包通知里出现的姓名写法。
    // 如果以后发现某个银行用了不同大小写、缩写或带称谓的写法，加进这个列表即可。
    private val OWNER_NAMES = setOf(
        "MAH GUO REN"
    )

    fun isOwnerName(name: String?): Boolean {
        if (name == null) return false
        val normalized = name.trim().uppercase()
        return OWNER_NAMES.any { normalized == it.uppercase() }
    }
}
