package com.mahjong.score.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.Game
import com.mahjong.score.model.GameLogic
import com.mahjong.score.model.HandRecord
import com.mahjong.score.model.Tiles
import com.mahjong.score.model.Transfer
import com.mahjong.score.model.WINDS
import com.mahjong.score.model.Win
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * 半莊紀錄的報表（A4）。由上到下：標題・規則 → 各局（局數、結果、和了手牌圖、點數移動圖）→ 排名。
 * 輸出為 PDF，或每頁一張 PNG（Instagram 等不收 PDF 的 App 用）。
 */
class RecordReport(private val game: Game, private val lang: AppLang) {
    private val names = game.names

    private class Block(val height: Float, val draw: (Canvas, Float) -> Unit)

    // ---- 版面與樣式 ----

    private companion object {
        const val W = 595f
        const val H = 842f
        const val M = 36f
        const val CONTENT = W - 2 * M
        const val BOTTOM = H - M - 14f

        const val TILE_W = 18f
        const val TILE_H = 25f
        const val TILE_GAP = 1.5f

        const val DIAG_H = 200f
        const val SEAT_W = 116f
        const val SEAT_H = 40f
        const val SEAT_DX = 158f
        const val POT_R = 17f

        val INK = Color.rgb(0x21, 0x21, 0x21)
        val MUTED = Color.rgb(0x75, 0x75, 0x75)
        val PRIMARY = Color.rgb(0x2E, 0x7D, 0x32)
        val PLUS = Color.rgb(0x2E, 0x7D, 0x32)
        val MINUS = Color.rgb(0xC6, 0x28, 0x28)
        val DIVIDER = Color.rgb(0xE0, 0xE0, 0xE0)
        val ARROW = Color.rgb(0x54, 0x6E, 0x7A)
    }

    private fun textPaint(size: Float, color: Int = INK, bold: Boolean = false, center: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (center) textAlign = Paint.Align.CENTER
        }

    private val pTitle = textPaint(18f, bold = true)
    private val pHeading = textPaint(12.5f, PRIMARY, bold = true)
    private val pBody = textPaint(10.5f)
    private val pSmall = textPaint(8.5f, MUTED)
    private val pFooter = textPaint(8f, MUTED, center = true)
    private val pRank = textPaint(11f, bold = true)

    private val pDivider = Paint().apply { color = DIVIDER; strokeWidth = 0.6f }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f }

    private fun t(zh: String, ja: String) = lang.t(zh, ja)

    private fun layout(text: String, paint: TextPaint, width: Float = CONTENT): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt()).setLineSpacing(0f, 1.15f).build()

    private val StaticLayout.hf: Float get() = height.toFloat()

    private fun Canvas.drawLayout(l: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        l.draw(this)
        restore()
    }

    private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

    // ---- 區塊 ----

    private fun header(): Block {
        val r = game.rules
        val title = layout(t("半莊紀錄", "半荘記録"), pTitle)
        val date = layout(
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(game.startedAt)) +
                "　" + t("共 ${game.records.size} 局", "全 ${game.records.size} 局"),
            pSmall,
        )
        val players = layout(names.indices.joinToString("　") { "${WINDS[it]}家 ${names[it]}" }, pBody)
        val rules = buildString {
            append(t("${r.startPoints}點持 ${r.returnPoints}點返", "${r.startPoints}点持ち ${r.returnPoints}点返し"))
            append(t("　馬 ", "　ウマ "))
            append(if (r.umaLarge == 0) t("無", "なし") else "${r.umaSmall}-${r.umaLarge}")
            if (r.kiriage) append(t("　切上滿貫", "　切り上げ満貫"))
            if (r.tobi) append(t("　擊飛", "　飛びあり"))
            if (r.westEntry) append("　西入")
        }.let { layout(it, pSmall) }
        val h = title.hf + 4 + date.hf + 8 + players.hf + 2 + rules.hf + 16
        return Block(h) { c, top ->
            var y = top
            c.drawLayout(title, M, y); y += title.hf + 4
            c.drawLayout(date, M, y); y += date.hf + 8
            c.drawLayout(players, M, y); y += players.hf + 2
            c.drawLayout(rules, M, y)
            c.drawLine(M, top + h - 6, W - M, top + h - 6, pDivider)
        }
    }

    private fun recordBlock(rec: HandRecord): Block {
        val kyotaku = if (rec.before.kyotaku > 0) t("　供託 ${rec.before.kyotaku} 本", "　供託 ${rec.before.kyotaku} 本") else ""
        val title = layout(rec.title + kyotaku, pHeading)
        val desc = layout(GameLogic.describe(rec, names, game.rules, lang), pBody)
        val hands = rec.wins.filter { it.hand.isNotEmpty() }
        val handH = 12f + TILE_H + 11f
        val h = title.hf + 3 + desc.hf + 8 + hands.size * (handH + 4) + DIAG_H + 16
        return Block(h) { c, top ->
            var y = top
            c.drawLayout(title, M, y); y += title.hf + 3
            c.drawLayout(desc, M, y); y += desc.hf + 8
            hands.forEach { w ->
                c.drawText(t("${names[w.winner]} 的手牌", "${names[w.winner]} の手牌"), M, y + 9, pSmall)
                drawHand(c, M, y + 12, w)
                y += handH + 4
            }
            drawTransferDiagram(c, y, rec)
            c.drawLine(M, top + h - 6, W - M, top + h - 6, pDivider)
        }
    }

    private fun standingsBlock(): Block {
        val heading = layout(if (game.finished) t("最終排名", "最終順位") else t("目前排名", "現在の順位"), pHeading)
        val rows = GameLogic.standings(game)
        val note = if (game.state.kyotaku > 0) {
            layout(t("剩餘供託 ${game.state.kyotaku * 1000} 點歸第1名", "残り供託 ${game.state.kyotaku * 1000} 点はトップへ"), pSmall)
        } else null
        val rowH = 20f
        val h = heading.hf + 8 + rows.size * rowH + (note?.let { it.hf + 4 } ?: 0f) + 8
        val pName = textPaint(11f)
        val pScore = textPaint(11f).apply { textAlign = Paint.Align.RIGHT }
        val pPt = textPaint(11f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        return Block(h) { c, top ->
            var y = top
            c.drawLayout(heading, M, y); y += heading.hf + 8
            rows.forEach { s ->
                val base = y + 14
                c.drawText("${s.rank}位", M, base, pRank)
                c.drawText(names[s.player], M + 44, base, pName)
                c.drawText("${s.score}", W - M - 90, base, pScore)
                pPt.color = if (s.pt >= 0) PLUS else MINUS
                c.drawText(String.format(Locale.US, "%+.1f", s.pt), W - M, base, pPt)
                y += rowH
            }
            note?.let { c.drawLayout(it, M, y + 4) }
        }
    }

    // ---- 手牌圖 ----

    private fun tileColor(t: Int): Int = when {
        t < 9 -> Color.rgb(0xC6, 0x28, 0x28)
        t < 18 -> Color.rgb(0x15, 0x65, 0xC0)
        t < 27 -> Color.rgb(0x2E, 0x7D, 0x32)
        t == 31 -> Color.rgb(0x75, 0x75, 0x75)
        t == 32 -> Color.rgb(0x2E, 0x7D, 0x32)
        t == 33 -> Color.rgb(0xC6, 0x28, 0x28)
        else -> INK
    }

    private val pTileRank = textPaint(11f, bold = true, center = true)
    private val pTileSuit = textPaint(6.5f, center = true)

    private fun drawTile(c: Canvas, x: Float, y: Float, tile: Int, highlight: Boolean) {
        val r = RectF(x, y, x + TILE_W, y + TILE_H)
        pFill.color = Color.rgb(0xFF, 0xFB, 0xF0)
        c.drawRoundRect(r, 2.5f, 2.5f, pFill)
        pStroke.color = if (highlight) MINUS else Color.rgb(0xB0, 0xA8, 0x98)
        pStroke.strokeWidth = if (highlight) 1.6f else 0.8f
        c.drawRoundRect(r, 2.5f, 2.5f, pStroke)
        val cx = x + TILE_W / 2
        pTileRank.color = tileColor(tile)
        if (Tiles.isHonor(tile)) {
            pTileRank.textSize = 12f
            c.drawText(Tiles.rankText(tile), cx, y + TILE_H / 2 + 4.5f, pTileRank)
        } else {
            pTileRank.textSize = 11f
            c.drawText(Tiles.rankText(tile), cx, y + 12f, pTileRank)
            pTileSuit.color = tileColor(tile)
            c.drawText(Tiles.suitText(tile), cx, y + TILE_H - 4f, pTileSuit)
        }
    }

    /** 門前牌（排序）＋和了牌（紅框）＋副露 */
    private fun drawHand(c: Canvas, x0: Float, y: Float, w: Win) {
        var x = x0
        w.hand.dropLast(1).sorted().forEach { drawTile(c, x, y, it, false); x += TILE_W + TILE_GAP }
        x += 5f
        drawTile(c, x, y, w.hand.last(), true)
        x += TILE_W + TILE_GAP
        val pLabel = textPaint(7f, MUTED, center = true)
        w.melds.forEach { m ->
            x += 9f
            val start = x
            m.tiles.forEach { drawTile(c, x, y, it, false); x += TILE_W + TILE_GAP }
            c.drawText(m.type.label(lang), (start + x - TILE_GAP) / 2, y + TILE_H + 9f, pLabel)
        }
    }

    // ---- 點數移動圖 ----

    private class Node(val cx: Float, val cy: Float, val halfW: Float, val halfH: Float, val round: Boolean)

    /** 四家依該局座風坐在桌子四邊（東下・南右・西上・北左），箭頭表示誰付給誰 */
    private fun drawTransferDiagram(c: Canvas, top: Float, rec: HandRecord) {
        val dealer = rec.before.kyoku % 4
        val cx = W / 2
        val cy = top + DIAG_H / 2
        fun seatNode(p: Int): Node {
            val (x, y) = when ((p - dealer + 4) % 4) {
                0 -> cx to top + DIAG_H - SEAT_H / 2 - 2
                1 -> cx + SEAT_DX to cy
                2 -> cx to top + SEAT_H / 2 + 2
                else -> cx - SEAT_DX to cy
            }
            return Node(x, y, SEAT_W / 2, SEAT_H / 2, false)
        }
        val pot = Node(cx, cy, POT_R, POT_R, true)
        fun node(i: Int) = if (i == Transfer.POT) pot else seatNode(i)

        // 桌面
        pFill.color = Color.rgb(0xF1, 0xF8, 0xF1)
        c.drawRoundRect(RectF(cx - SEAT_DX + 20, top + 26, cx + SEAT_DX - 20, top + DIAG_H - 26), 8f, 8f, pFill)

        val transfers = GameLogic.transfers(game, rec)
        if (transfers != null && transfers.any { it.from == Transfer.POT || it.to == Transfer.POT }) {
            pFill.color = Color.rgb(0xFF, 0xF8, 0xE1)
            c.drawCircle(cx, cy, POT_R, pFill)
            pStroke.color = Color.rgb(0xF9, 0xA8, 0x25); pStroke.strokeWidth = 0.8f
            c.drawCircle(cx, cy, POT_R, pStroke)
            c.drawText("供託", cx, cy + 3.5f, textPaint(8.5f, INK, bold = true, center = true))
        }
        transfers?.forEach { tr ->
            val reverse = transfers.any { it.from == tr.to && it.to == tr.from }
            drawArrow(c, node(tr.from), node(tr.to), tr.amount, if (reverse) 6f else 0f)
        }

        val after = rec.before.scores.mapIndexed { i, s -> s + rec.deltas[i] }
        for (p in 0 until 4) {
            drawSeat(c, seatNode(p), p, WINDS[(p - dealer + 4) % 4], p == dealer, rec.deltas[p], after[p])
        }
    }

    private fun drawSeat(c: Canvas, n: Node, p: Int, wind: String, dealer: Boolean, delta: Int, after: Int) {
        val r = RectF(n.cx - n.halfW, n.cy - n.halfH, n.cx + n.halfW, n.cy + n.halfH)
        pFill.color = if (dealer) Color.rgb(0xFF, 0xEB, 0xEE) else Color.rgb(0xF5, 0xF5, 0xF5)
        c.drawRoundRect(r, 5f, 5f, pFill)
        pStroke.color = if (dealer) Color.rgb(0xEF, 0x9A, 0x9A) else Color.rgb(0xBD, 0xBD, 0xBD)
        pStroke.strokeWidth = 0.8f
        c.drawRoundRect(r, 5f, 5f, pStroke)

        val pLine1 = textPaint(9.5f, INK, bold = true, center = true)
        val label = "$wind${if (dealer) "・親" else ""}　" +
            TextUtils.ellipsize(names[p], pLine1, SEAT_W - 44, TextUtils.TruncateAt.END)
        c.drawText(label, n.cx, n.cy - 4, pLine1)
        val pDelta = textPaint(10f, if (delta > 0) PLUS else if (delta < 0) MINUS else MUTED, bold = true)
        val pAfter = textPaint(8f, MUTED)
        val d = signed(delta)
        val a = "  → $after"
        val total = pDelta.measureText(d) + pAfter.measureText(a)
        val x = n.cx - total / 2
        c.drawText(d, x, n.cy + 12, pDelta)
        c.drawText(a, x + pDelta.measureText(d), n.cy + 12, pAfter)
    }

    private fun drawArrow(c: Canvas, from: Node, to: Node, amount: Int, offset: Float) {
        var dx = to.cx - from.cx
        var dy = to.cy - from.cy
        val len = hypot(dx, dy)
        if (len < 1f) return
        dx /= len; dy /= len
        // 法線方向偏移，避免雙向箭頭重疊
        val nx = -dy * offset
        val ny = dx * offset
        val s = edge(from, dx, dy) + 3f
        val e = edge(to, -dx, -dy) + 3f
        val x1 = from.cx + dx * s + nx
        val y1 = from.cy + dy * s + ny
        val x2 = to.cx - dx * e + nx
        val y2 = to.cy - dy * e + ny

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ARROW; strokeWidth = 1.3f; style = Paint.Style.STROKE }
        c.drawLine(x1, y1, x2 - dx * 5f, y2 - dy * 5f, line)
        val head = Path().apply {
            moveTo(x2, y2)
            lineTo(x2 - dx * 7f - dy * 3.5f, y2 - dy * 7f + dx * 3.5f)
            lineTo(x2 - dx * 7f + dy * 3.5f, y2 - dy * 7f - dx * 3.5f)
            close()
        }
        c.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ARROW; style = Paint.Style.FILL })

        // 金額標籤
        val label = "$amount"
        val pLabel = textPaint(8.5f, INK, bold = true, center = true)
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        val tw = pLabel.measureText(label) / 2 + 3f
        pFill.color = Color.WHITE
        c.drawRoundRect(RectF(mx - tw, my - 7f, mx + tw, my + 5f), 3f, 3f, pFill)
        pStroke.color = DIVIDER; pStroke.strokeWidth = 0.6f
        c.drawRoundRect(RectF(mx - tw, my - 7f, mx + tw, my + 5f), 3f, 3f, pStroke)
        c.drawText(label, mx, my + 2.5f, pLabel)
    }

    /** 從節點中心沿 (dx, dy) 到邊緣的距離 */
    private fun edge(n: Node, dx: Float, dy: Float): Float {
        if (n.round) return n.halfW
        val tx = if (abs(dx) < 1e-4f) Float.MAX_VALUE else n.halfW / abs(dx)
        val ty = if (abs(dy) < 1e-4f) Float.MAX_VALUE else n.halfH / abs(dy)
        return min(tx, ty)
    }

    // ---- 分頁與輸出 ----

    private fun pages(): List<List<Block>> {
        val blocks = listOf(header()) + game.records.map { recordBlock(it) } + standingsBlock()
        val pages = mutableListOf(mutableListOf<Block>())
        var y = M
        for (b in blocks) {
            if (y + b.height > BOTTOM && pages.last().isNotEmpty()) {
                pages += mutableListOf<Block>()
                y = M
            }
            pages.last() += b
            y += b.height
        }
        return pages
    }

    private fun drawPage(c: Canvas, blocks: List<Block>, index: Int, count: Int) {
        var y = M
        blocks.forEach { b ->
            b.draw(c, y)
            y += b.height
        }
        c.drawText("日本麻雀点数計算機　${index + 1} / $count", W / 2, H - M / 2, pFooter)
    }

    fun writePdf(file: File) {
        val pages = pages()
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, blocks ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), i + 1).create())
                drawPage(page.canvas, blocks, i, pages.size)
                doc.finishPage(page)
            }
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** 每頁輸出一張 PNG（2 倍解析度），回傳檔案清單 */
    fun writePngs(dir: File, baseName: String): List<File> {
        val pages = pages()
        val scale = 2f
        return pages.mapIndexed { i, blocks ->
            val bmp = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                c.scale(scale, scale)
                drawPage(c, blocks, i, pages.size)
                File(dir, "$baseName-${i + 1}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            } finally {
                bmp.recycle()
            }
        }
    }
}
