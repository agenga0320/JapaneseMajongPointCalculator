package com.mahjong.score.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mahjong.score.R
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
class RecordReport(private val game: Game, private val lang: AppLang, private val res: Resources) {
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

        // 跟 App 對局桌面同一組：暗色呢布頁面、深色面板、亮色字、金色標題與箭頭
        val INK = Color.rgb(0xF4, 0xEE, 0xDF)
        val MUTED = Color.rgb(0x9F, 0xC0, 0xB4)
        val PRIMARY = Color.rgb(0xE9, 0xC4, 0x6A)
        val PLUS = Color.rgb(0x7F, 0xD1, 0xB0)
        val MINUS = Color.rgb(0xFF, 0x86, 0x78)
        val DIVIDER = Color.rgb(0x2C, 0x5A, 0x4E)
        val ARROW = Color.rgb(0xE9, 0xC4, 0x6A)
        val GOLD = Color.rgb(0xE9, 0xC4, 0x6A)
        val RED = Color.rgb(0xE5, 0x58, 0x4B)
        val FELT = Color.rgb(0x1B, 0x55, 0x47)
        val FELT_EDGE = Color.rgb(0x0A, 0x24, 0x1E)
        val PANEL = Color.rgb(0x0F, 0x2C, 0x25)
        val PANEL_BORDER = Color.rgb(0x3B, 0x72, 0x62)
        val PLATE = Color.rgb(0x0A, 0x22, 0x1C)
    }

    private fun textPaint(size: Float, color: Int = INK, bold: Boolean = false, center: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (center) textAlign = Paint.Align.CENTER
        }

    private val pTitle = textPaint(22f, PRIMARY, bold = true)
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
            c.drawLine(M, top + h - 6, W - M, top + h - 6, Paint().apply { color = GOLD; strokeWidth = 1.2f })
        }
    }

    private fun recordBlock(rec: HandRecord): Block {
        val kyotaku = if (rec.before.kyotaku > 0) t("　供託 ${rec.before.kyotaku} 本", "　供託 ${rec.before.kyotaku} 本") else ""
        val title = layout(rec.title + kyotaku, pHeading)
        val desc = layout(GameLogic.describe(rec, names, game.rules, lang), pBody)
        val hands = rec.wins.filter { it.hand.isNotEmpty() }
        val handH = 12f + TILE_H + 11f
        val h = title.hf + 3 + desc.hf + 8 + hands.size * (handH + 4) + DIAG_H + 26
        return Block(h) { c, top ->
            drawPanel(c, RectF(M - 12, top - 8, W - M + 12, top + h - 10))
            var y = top
            c.drawLayout(title, M, y); y += title.hf + 3
            c.drawLayout(desc, M, y); y += desc.hf + 8
            hands.forEach { w ->
                c.drawText(t("${names[w.winner]} 的手牌", "${names[w.winner]} の手牌"), M, y + 9, pSmall)
                drawHand(c, M, y + 12, w)
                y += handH + 4
            }
            drawTransferDiagram(c, y, rec)
        }
    }

    private fun standingsBlock(): Block {
        val heading = layout(if (game.finished) t("最終排名", "最終順位") else t("目前排名", "現在の順位"), pHeading)
        val rows = GameLogic.standings(game)
        val note = if (game.state.kyotaku > 0) {
            layout(t("剩餘供託 ${game.state.kyotaku * 1000} 點歸第1名", "残り供託 ${game.state.kyotaku * 1000} 点はトップへ"), pSmall)
        } else null
        val rowH = 30f
        val h = heading.hf + 8 + rows.size * rowH + (note?.let { it.hf + 4 } ?: 0f) + 8
        val pName = textPaint(11f)
        val pScore = textPaint(11f).apply { textAlign = Paint.Align.RIGHT }
        val pPt = textPaint(11f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        return Block(h) { c, top ->
            var y = top
            c.drawLayout(heading, M, y); y += heading.hf + 8
            rows.forEach { s ->
                val plate = RectF(M - 4, y, W - M + 4, y + rowH - 6)
                pFill.color = PLATE
                c.drawRoundRect(plate, 6f, 6f, pFill)
                pStroke.color = if (s.rank == 1) GOLD else PANEL_BORDER; pStroke.strokeWidth = if (s.rank == 1) 1.2f else 0.7f
                c.drawRoundRect(plate, 6f, 6f, pStroke)
                val base = y + 16
                pRank.color = if (s.rank == 1) GOLD else INK
                c.drawText("${s.rank}位", M + 6, base, pRank)
                c.drawText(names[s.player], M + 50, base, pName)
                c.drawText("${s.score}", W - M - 90, base, pScore)
                pPt.color = if (s.pt >= 0) PLUS else MINUS
                c.drawText(String.format(Locale.US, "%+.1f", s.pt), W - M - 6, base, pPt)
                y += rowH
            }
            note?.let { c.drawLayout(it, M, y + 4) }
        }
    }

    // ---- 手牌圖 ----

    private val TILE_IDS = intArrayOf(R.drawable.tile_0, R.drawable.tile_1, R.drawable.tile_2, R.drawable.tile_3, R.drawable.tile_4, R.drawable.tile_5, R.drawable.tile_6, R.drawable.tile_7, R.drawable.tile_8, R.drawable.tile_9, R.drawable.tile_10, R.drawable.tile_11, R.drawable.tile_12, R.drawable.tile_13, R.drawable.tile_14, R.drawable.tile_15, R.drawable.tile_16, R.drawable.tile_17, R.drawable.tile_18, R.drawable.tile_19, R.drawable.tile_20, R.drawable.tile_21, R.drawable.tile_22, R.drawable.tile_23, R.drawable.tile_24, R.drawable.tile_25, R.drawable.tile_26, R.drawable.tile_27, R.drawable.tile_28, R.drawable.tile_29, R.drawable.tile_30, R.drawable.tile_31, R.drawable.tile_32, R.drawable.tile_33)

    // 牌面圖片與 App 內相同（res/drawable-nodpi/tile_0..33），用到才解碼
    private val tileBitmaps = arrayOfNulls<Bitmap>(Tiles.COUNT)
    private val pBitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun tileBitmap(tile: Int): Bitmap = tileBitmaps[tile] ?: BitmapFactory.decodeResource(
        res, TILE_IDS[tile],
    ).also { tileBitmaps[tile] = it }

    private fun drawTile(c: Canvas, x: Float, y: Float, tile: Int, highlight: Boolean) {
        val r = RectF(x, y, x + TILE_W, y + TILE_H)
        c.drawBitmap(tileBitmap(tile), null, r, pBitmap)
        if (highlight) {
            pStroke.color = MINUS
            pStroke.strokeWidth = 1.6f
            c.drawRoundRect(r, 2.5f, 2.5f, pStroke)
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

        // 桌面：中央亮、四周暗的呢布
        val table = RectF(cx - SEAT_DX + 20, top + 26, cx + SEAT_DX - 20, top + DIAG_H - 26)
        pFill.shader = RadialGradient(cx, cy, table.width() / 1.6f, FELT, FELT_EDGE, Shader.TileMode.CLAMP)
        c.drawRoundRect(table, 10f, 10f, pFill)
        pFill.shader = null
        pStroke.color = PANEL_BORDER; pStroke.strokeWidth = 0.7f
        c.drawRoundRect(table, 10f, 10f, pStroke)

        val transfers = GameLogic.transfers(game, rec)
        if (transfers != null && transfers.any { it.from == Transfer.POT || it.to == Transfer.POT }) {
            pFill.color = GOLD
            c.drawCircle(cx, cy, POT_R, pFill)
            c.drawText("供託", cx, cy + 3.5f, textPaint(8.5f, Color.rgb(0x2B, 0x20, 0x00), bold = true, center = true))
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
        pFill.color = PLATE
        c.drawRoundRect(r, 6f, 6f, pFill)
        pStroke.color = if (dealer) RED else PANEL_BORDER
        pStroke.strokeWidth = if (dealer) 1.2f else 0.8f
        c.drawRoundRect(r, 6f, 6f, pStroke)

        val pLine1 = textPaint(9.5f, if (dealer) RED else INK, bold = true, center = true)
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
        val pLabel = textPaint(8.5f, GOLD, bold = true, center = true)
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        val tw = pLabel.measureText(label) / 2 + 4f
        pFill.color = PLATE
        c.drawRoundRect(RectF(mx - tw, my - 7.5f, mx + tw, my + 5.5f), 6f, 6f, pFill)
        pStroke.color = GOLD; pStroke.strokeWidth = 0.6f
        c.drawRoundRect(RectF(mx - tw, my - 7.5f, mx + tw, my + 5.5f), 6f, 6f, pStroke)
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

    /** 每局的深色面板 */
    private fun drawPanel(c: Canvas, r: RectF) {
        pFill.color = PANEL
        c.drawRoundRect(r, 10f, 10f, pFill)
        pStroke.color = PANEL_BORDER; pStroke.strokeWidth = 0.7f
        c.drawRoundRect(r, 10f, 10f, pStroke)
    }

    private fun drawPage(c: Canvas, blocks: List<Block>, index: Int, count: Int) {
        // 整頁是暗色呢布，中央偏上最亮
        pFill.shader = RadialGradient(W / 2, H * 0.35f, H * 0.8f, FELT, FELT_EDGE, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, pFill)
        pFill.shader = null
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
