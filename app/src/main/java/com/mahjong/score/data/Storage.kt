package com.mahjong.score.data

import android.content.Context
import com.mahjong.score.model.AppLang
import com.mahjong.score.model.FuItem
import com.mahjong.score.model.FuKind
import com.mahjong.score.model.Game
import com.mahjong.score.model.HandRecord
import com.mahjong.score.model.Meld
import com.mahjong.score.model.MeldType
import com.mahjong.score.model.RoundState
import com.mahjong.score.model.Rules
import com.mahjong.score.model.Win
import com.mahjong.score.model.Yaku
import com.mahjong.score.model.YakuItem
import org.json.JSONArray
import org.json.JSONObject

/** 以 SharedPreferences + JSON 保存進行中的半莊與歷史紀錄 */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("mahjong", Context.MODE_PRIVATE)

    fun loadCurrent(): Game? = prefs.getString(KEY_CURRENT, null)?.let {
        runCatching { gameFromJson(JSONObject(it)) }.getOrNull()
    }

    fun saveCurrent(game: Game?) {
        prefs.edit().apply {
            if (game == null) remove(KEY_CURRENT) else putString(KEY_CURRENT, gameToJson(game).toString())
        }.apply()
    }

    fun loadHistory(): List<Game> = prefs.getString(KEY_HISTORY, null)?.let {
        runCatching {
            val a = JSONArray(it)
            List(a.length()) { i -> gameFromJson(a.getJSONObject(i)) }
        }.getOrNull()
    } ?: emptyList()

    fun saveHistory(list: List<Game>) {
        val a = JSONArray()
        list.forEach { a.put(gameToJson(it)) }
        prefs.edit().putString(KEY_HISTORY, a.toString()).apply()
    }

    /** 使用者選擇忽略的版本 tag；出現更新的版本時會再次提示 */
    var ignoredVersion: String?
        get() = prefs.getString(KEY_IGNORED_VERSION, null)
        set(value) = prefs.edit().putString(KEY_IGNORED_VERSION, value).apply()

    /** 介面語言；未設定時為 null */
    var lang: AppLang?
        get() = AppLang.fromCode(prefs.getString(KEY_LANG, null))
        set(value) = prefs.edit().putString(KEY_LANG, value?.code).apply()

    private companion object {
        const val KEY_CURRENT = "current"
        const val KEY_HISTORY = "history"
        const val KEY_IGNORED_VERSION = "ignoredVersion"
        const val KEY_LANG = "lang"
    }
}

private fun JSONArray.ints() = List(length()) { getInt(it) }
private fun JSONArray.strings() = List(length()) { getString(it) }

private fun stateToJson(s: RoundState) = JSONObject().apply {
    put("kyoku", s.kyoku)
    put("honba", s.honba)
    put("kyotaku", s.kyotaku)
    put("scores", JSONArray(s.scores))
}

private fun stateFromJson(o: JSONObject) = RoundState(
    o.getInt("kyoku"), o.getInt("honba"), o.getInt("kyotaku"), o.getJSONArray("scores").ints(),
)

private fun gameToJson(g: Game) = JSONObject().apply {
    put("id", g.id)
    put("startedAt", g.startedAt)
    put("names", JSONArray(g.names))
    put("rules", JSONObject().apply {
        put("startPoints", g.rules.startPoints)
        put("returnPoints", g.rules.returnPoints)
        put("umaSmall", g.rules.umaSmall)
        put("umaLarge", g.rules.umaLarge)
        put("kiriage", g.rules.kiriage)
        put("tobi", g.rules.tobi)
        put("westEntry", g.rules.westEntry)
    })
    put("state", stateToJson(g.state))
    put("records", JSONArray().apply { g.records.forEach { put(recordToJson(it)) } })
    put("finished", g.finished)
    put("manualEnd", g.manualEnd)
}

private fun gameFromJson(o: JSONObject): Game {
    val r = o.getJSONObject("rules")
    val recs = o.getJSONArray("records")
    return Game(
        id = o.getLong("id"),
        startedAt = o.getLong("startedAt"),
        names = o.getJSONArray("names").strings(),
        rules = Rules(
            startPoints = r.getInt("startPoints"),
            returnPoints = r.getInt("returnPoints"),
            umaSmall = r.getInt("umaSmall"),
            umaLarge = r.getInt("umaLarge"),
            kiriage = r.optBoolean("kiriage", false),
            tobi = r.optBoolean("tobi", true),
            westEntry = r.optBoolean("westEntry", false),
        ),
        state = stateFromJson(o.getJSONObject("state")),
        records = List(recs.length()) { i -> recordFromJson(recs.getJSONObject(i)) },
        finished = o.optBoolean("finished", false),
        manualEnd = o.optBoolean("manualEnd", false),
    )
}

private fun recordToJson(r: HandRecord) = JSONObject().apply {
    put("before", stateToJson(r.before))
    put("deltas", JSONArray(r.deltas))
    put("wins", JSONArray().apply {
        r.wins.forEach { w ->
            put(JSONObject().apply {
                put("winner", w.winner)
                put("han", w.han)
                put("fu", w.fu)
                put("yakuman", w.yakuman)
                put("yaku", JSONArray().apply {
                    w.yaku.forEach { y ->
                        put(JSONObject().apply {
                            put("k", y.yaku.name)
                            put("han", y.han)
                            put("ym", y.yakuman)
                            put("tile", y.tile)
                        })
                    }
                })
                put("fu_items", JSONArray().apply {
                    w.fuItems.forEach { f ->
                        put(JSONObject().apply {
                            put("k", f.kind.name)
                            put("fu", f.fu)
                            put("tile", f.tile)
                        })
                    }
                })
                put("hand", JSONArray(w.hand))
                put("melds", JSONArray().apply {
                    w.melds.forEach { m ->
                        put(JSONObject().apply {
                            put("type", m.type.name)
                            put("tile", m.tile)
                        })
                    }
                })
            })
        }
    })
    r.loser?.let { put("loser", it) }
    put("tenpai", JSONArray(r.tenpai.toList()))
    put("riichi", JSONArray(r.riichi.toList()))
    put("abortive", r.abortive)
    r.legacyDetail?.let { put("detail", it) }
}

private fun recordFromJson(x: JSONObject): HandRecord {
    val before = stateFromJson(x.getJSONObject("before"))
    val deltas = x.getJSONArray("deltas").ints()
    // v1.0.0 的紀錄只有文字說明
    if (!x.has("wins")) return HandRecord(before, deltas, legacyDetail = x.optString("detail", ""))
    val wins = x.getJSONArray("wins").let { a ->
        List(a.length()) { i ->
            val w = a.getJSONObject(i)
            val ys = w.optJSONArray("yaku") ?: JSONArray()
            val fs = w.optJSONArray("fu_items") ?: JSONArray()
            Win(
                winner = w.getInt("winner"),
                han = w.getInt("han"),
                fu = w.getInt("fu"),
                yakuman = w.getInt("yakuman"),
                yaku = List(ys.length()) { j -> ys.getJSONObject(j) }.mapNotNull { y ->
                    val key = runCatching { Yaku.valueOf(y.getString("k")) }.getOrNull() ?: return@mapNotNull null
                    YakuItem(key, y.getInt("han"), y.optInt("ym", 0), y.optInt("tile", -1))
                },
                fuItems = List(fs.length()) { j -> fs.getJSONObject(j) }.mapNotNull { f ->
                    val key = runCatching { FuKind.valueOf(f.getString("k")) }.getOrNull() ?: return@mapNotNull null
                    FuItem(key, f.getInt("fu"), f.optInt("tile", -1))
                },
                hand = w.optJSONArray("hand")?.ints() ?: emptyList(),
                melds = (w.optJSONArray("melds") ?: JSONArray()).let { ms ->
                    List(ms.length()) { j -> ms.getJSONObject(j) }.mapNotNull { m ->
                        val type = runCatching { MeldType.valueOf(m.getString("type")) }.getOrNull() ?: return@mapNotNull null
                        Meld(type, m.getInt("tile"))
                    }
                },
            )
        }
    }
    return HandRecord(
        before = before,
        deltas = deltas,
        wins = wins,
        loser = if (x.has("loser")) x.getInt("loser") else null,
        tenpai = x.getJSONArray("tenpai").ints().toSet(),
        riichi = x.getJSONArray("riichi").ints().toSet(),
        abortive = x.optBoolean("abortive", false),
        legacyDetail = if (x.has("detail")) x.getString("detail") else null,
    )
}
