package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToLong

private val Context.feathersDataStore by preferencesDataStore(name = "feathers")

/** One line in the Feathers ledger: when, how much (±), and why. */
data class FeatherEntry(val ms: Long, val amount: Int, val reason: String)

/**
 * The Feathers economy — the spendable gamification currency (separate from the XP/rank prestige).
 * Earned by driving and catching, spent in the store. A gentle idle decay nudges you to keep
 * driving: after a week without a drive the balance slips toward a target, never below a floor.
 * Local source of truth; a later phase mirrors the balance to the online Flock layer.
 */
class FeathersStore(private val context: Context) {

    private val balanceKey = longPreferencesKey("balance")
    private val ledgerKey = stringPreferencesKey("ledger_json")
    private val lastDriveMsKey = longPreferencesKey("last_drive_ms")
    private val balAtDriveKey = longPreferencesKey("bal_at_drive")
    private val lastDayKey = stringPreferencesKey("last_day")
    private val streakKey = intPreferencesKey("streak")

    private val ledgerCap = 40
    private val floor = 50L

    val balance: Flow<Long> = context.feathersDataStore.data.map { it[balanceKey] ?: 0L }

    val ledger: Flow<List<FeatherEntry>> =
        context.feathersDataStore.data.map { parseLedger(it[ledgerKey] ?: "[]") }

    /** Current driving streak in days (for the HUD). */
    val streak: Flow<Int> = context.feathersDataStore.data.map { it[streakKey] ?: 0 }

    /** Add (or, with a negative amount, remove) Feathers. [fromDrive] marks driving activity so the
     *  idle-decay baseline resets. Returns the new balance. */
    suspend fun award(amount: Int, reason: String, fromDrive: Boolean = false): Long {
        var newBal = 0L
        context.feathersDataStore.edit { p ->
            val cur = p[balanceKey] ?: 0L
            newBal = (cur + amount).coerceAtLeast(0L)
            p[balanceKey] = newBal
            p[ledgerKey] = appendLedger(p[ledgerKey] ?: "[]", FeatherEntry(System.currentTimeMillis(), amount, reason))
            if (fromDrive) {
                p[lastDriveMsKey] = System.currentTimeMillis()
                p[balAtDriveKey] = newBal
            }
        }
        return newBal
    }

    /** Spend [amount] if affordable; returns true on success. */
    suspend fun spend(amount: Int, reason: String): Boolean {
        var ok = false
        context.feathersDataStore.edit { p ->
            val cur = p[balanceKey] ?: 0L
            if (cur >= amount) {
                p[balanceKey] = cur - amount
                p[ledgerKey] = appendLedger(p[ledgerKey] ?: "[]", FeatherEntry(System.currentTimeMillis(), -amount, reason))
                ok = true
            }
        }
        return ok
    }

    /**
     * Advance the daily streak for a drive today. Returns (first-drive-of-day bonus, streak
     * multiplier). The bonus is 0 if you've already driven today.
     */
    suspend fun recordDriveDay(): Pair<Int, Float> {
        val today = dayKey(System.currentTimeMillis())
        val yesterday = dayKey(System.currentTimeMillis() - 86_400_000L)
        var firstOfDay = false
        var mult = 1f
        context.feathersDataStore.edit { p ->
            val last = p[lastDayKey] ?: ""
            val streak = p[streakKey] ?: 0
            firstOfDay = last != today
            val newStreak = when (last) { today -> streak.coerceAtLeast(1); yesterday -> streak + 1; else -> 1 }
            p[streakKey] = newStreak
            p[lastDayKey] = today
            mult = when { newStreak >= 14 -> 2f; newStreak >= 7 -> 1.5f; newStreak >= 3 -> 1.25f; else -> 1f }
        }
        return (if (firstOfDay) 20 else 0) to mult
    }

    /** Apply gentle idle decay if it's been over a week since the last drive. Idempotent between
     *  drives (decays toward a fixed target, never below the floor). */
    suspend fun applyDecayIfIdle() {
        val snap = context.feathersDataStore.data.first()
        val lastDrive = snap[lastDriveMsKey] ?: 0L
        if (lastDrive == 0L) return
        val idleDays = ((System.currentTimeMillis() - lastDrive) / 86_400_000L).toInt()
        if (idleDays <= 7) return
        val base = snap[balAtDriveKey] ?: (snap[balanceKey] ?: 0L)
        val target = (base * 0.98.pow(idleDays - 7)).roundToLong().coerceAtLeast(floor)
        val cur = snap[balanceKey] ?: 0L
        if (cur > target) {
            context.feathersDataStore.edit { p ->
                p[balanceKey] = target
                p[ledgerKey] = appendLedger(p[ledgerKey] ?: "[]",
                    FeatherEntry(System.currentTimeMillis(), (target - cur).toInt(), "Idle decay"))
            }
        }
    }

    suspend fun reset() {
        context.feathersDataStore.edit { p ->
            p[balanceKey] = 0L; p[ledgerKey] = "[]"; p[streakKey] = 0
            p[lastDayKey] = ""; p[lastDriveMsKey] = 0L; p[balAtDriveKey] = 0L
        }
    }

    private fun dayKey(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    private fun appendLedger(json: String, e: FeatherEntry): String {
        val list = (listOf(e) + parseLedger(json)).take(ledgerCap)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("ms", it.ms).put("amt", it.amount).put("why", it.reason)) }
        return arr.toString()
    }

    private fun parseLedger(json: String): List<FeatherEntry> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            FeatherEntry(o.optLong("ms"), o.optInt("amt"), o.optString("why"))
        }
    }.getOrDefault(emptyList())
}
