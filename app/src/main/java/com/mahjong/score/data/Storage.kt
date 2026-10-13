package com.mahjong.score.data

import android.content.Context
import com.mahjong.score.model.Game
import com.mahjong.score.model.HandRecord
import com.mahjong.score.model.RoundState
import com.mahjong.score.model.Rules
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

    private companion object {
        const val KEY_CURRENT = "current"
        const val KEY_HISTORY = "history"
        const val KEY_IGNORED_VERSION = "ignoredVersion"
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
    put("records", JSONArray().apply {
        g.records.forEach { r ->
            put(JSONObject().apply {
                put("before", stateToJson(r.before))
                put("title", r.title)
                put("detail", r.detail)
                put("deltas", JSONArray(r.deltas))
            })
        }
    })
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
        records = List(recs.length()) { i ->
            val x = recs.getJSONObject(i)
            HandRecord(
                stateFromJson(x.getJSONObject("before")),
                x.getString("title"),
                x.getString("detail"),
                x.getJSONArray("deltas").ints(),
            )
        },
        finished = o.optBoolean("finished", false),
        manualEnd = o.optBoolean("manualEnd", false),
    )
}
