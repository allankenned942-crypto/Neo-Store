package com.machiav3lli.fdroid.data.index

object ForgeReadme {
    private val linkedBadge = Regex("""\[!\[[^\]]*\]\([^)]*\)\]\([^)]*\)""")
    private val imgMd = Regex("""!\[[^\]]*\]\(([^)\s]+)[^)]*\)""")
    private val imgHtml = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val imgTag = Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE)
    private val comment = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val heading = Regex("""^#{1,6}\s*(.+)$""", RegexOption.MULTILINE)
    private val bold = Regex("""\*\*(.+?)\*\*""")
    private val link = Regex("""\[([^\]]+)\]\(([^)\s]+)[^)]*\)""")
    private val code = Regex("""`([^`\n]+)`""")
    private val bullet = Regex("""^\s*[-*]\s+""", RegexOption.MULTILINE)
    private val blank = Regex("""\n{3,}""")

    private fun resolve(url: String, rawBase: String): String =
        if (url.startsWith("http")) url
        else rawBase + "/" + url.removePrefix("./").removePrefix("/")

    fun images(md: String, rawBase: String): List<String> =
        (imgMd.findAll(md).map { it.groupValues[1] } + imgHtml.findAll(md).map { it.groupValues[1] })
            .map { resolve(it, rawBase) }
            .filter {
                val l = it.lowercase()
                l.startsWith("http") && !l.contains("shields.io") && !l.contains("badge") &&
                        !l.endsWith(".svg") && !l.contains("logo") && !l.contains("fdroid") &&
                        !l.contains("get-it-on")
            }
            .distinct().take(8).toList()

    private fun mdToHtml(src: String): String {
        var t = src.replace("\r", "")
        t = comment.replace(t, "")
        t = linkedBadge.replace(t, "")
        t = imgMd.replace(t, "")
        t = imgTag.replace(t, "")
        t = heading.replace(t) { "<b>" + it.groupValues[1] + "</b>" }
        t = bold.replace(t) { "<b>" + it.groupValues[1] + "</b>" }
        t = link.replace(t) { "<a href=\"" + it.groupValues[2] + "\">" + it.groupValues[1] + "</a>" }
        t = code.replace(t) { "<tt>" + it.groupValues[1] + "</tt>" }
        t = bullet.replace(t, "• ")
        t = blank.replace(t, "\n\n")
        return t.trim().replace("\n", "<br/>")
    }

    fun describe(repoDesc: String, readme: String, body: String): String {
        val parts = mutableListOf<String>()
        if (repoDesc.isNotBlank()) parts.add(repoDesc)
        if (readme.isNotBlank()) parts.add(mdToHtml(readme.take(6000)))
        else if (body.isNotBlank()) parts.add(body.replace("\n", "<br/>"))
        return parts.joinToString("<br/><br/>")
    }
}
