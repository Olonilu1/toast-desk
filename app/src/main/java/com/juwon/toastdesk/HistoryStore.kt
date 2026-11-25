package com.juwon.toastdesk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object HistoryStore {

    data class Session(
        val ts: Long,
        val her: String,
        val replies: List<Api.Reply>,
        val tip: String?,
        val opts: String,
        val model: String
    )

    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    fun load(ctx: Context): MutableList<Session> {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<Session>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val reps = mutableListOf<Api.Reply>()
                val ra = o.optJSONArray("replies") ?: JSONArray()
                for (k in 0 until ra.length()) {
                    val r = ra.optJSONObject(k) ?: continue
                    reps.add(Api.Reply(r.optString("label"), r.optString("text"), r.optString("why")))
                }
                out.add(Session(
                    o.optLong("ts"),
                    o.optString("her"),
                    reps,
                    o.optString("tip", "").ifBlank { null },
                    o.optString("opts"),
                    o.optString("model")))
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun add(ctx: Context, s: Session) {
        val list = load(ctx)
        list.add(0, s)
        while (list.size > 60) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (x in list) {
            val ra = JSONArray()
            for (r in x.replies) {
                ra.put(JSONObject().put("label", r.label).put("text", r.text).put("why", r.why))
            }
            arr.put(JSONObject()
                .put("ts", x.ts)
                .put("her", x.her)
                .put("tip", x.tip ?: "")
                .put("opts", x.opts)
                .put("model", x.model)
                .put("replies", ra))
        }
        file(ctx).writeText(arr.toString())
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
