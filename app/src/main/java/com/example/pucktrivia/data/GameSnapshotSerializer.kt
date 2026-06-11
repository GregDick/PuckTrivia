package com.example.pucktrivia.data

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Converts [GameSnapshot] to and from a [ByteArray] for storage in
 * [androidx.lifecycle.SavedStateHandle].
 *
 * The snapshot is deliberately NOT stored in the handle as a [java.io.Serializable] directly:
 * `SavedStateHandle` eagerly reads every Bundle entry during its construction — *before* any
 * ViewModel code runs — so a stale snapshot that fails deserialisation there (see the
 * `serialVersionUID` note on [GameSnapshot]) would crash the app with no opportunity to recover. A
 * `ByteArray` is Bundle-native and can never fail at that stage; deserialisation happens here, on
 * demand, where [fromBytes] can catch the failure and report "no active game" instead.
 */
internal object GameSnapshotSerializer {

    fun toBytes(snapshot: GameSnapshot): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(snapshot) }
            buffer.toByteArray()
        }

    /**
     * Returns the decoded snapshot, or `null` for a null input or any payload that fails to
     * deserialise (corrupt bytes, or a snapshot from an older class shape) — fail-safe, mirroring
     * [HighScoreCodec]'s decode contract.
     */
    fun fromBytes(bytes: ByteArray?): GameSnapshot? {
        if (bytes == null) return null
        return try {
            ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as? GameSnapshot
        } catch (e: Exception) {
            Log.e("GameSnapshotSerializer", "Discarding unreadable game snapshot", e)
            null
        }
    }
}
