package me.rerere.rikkahub.data.codexvl

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-backed storage for Codex-VL settings, key material and thread mappings. */
class CodexVLConfigStore(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    @Synchronized
    fun read(): State = decrypt(file) ?: State()

    @Synchronized
    fun write(state: State) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(json.encodeToString(state).encodeToByteArray())
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(cipher.iv + ciphertext)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    fun update(block: (State) -> State): State = synchronized(this) {
        block(read()).also(::write)
    }

    private fun decrypt(source: File): State? {
        if (!source.exists()) return null
        return runCatching {
            val bytes = source.readBytes()
            require(bytes.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH, bytes.copyOfRange(0, IV_SIZE)),
            )
            json.decodeFromString<State>(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)).decodeToString())
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    @Serializable
    data class State(
        val provider: CodexVLProviderConfig = CodexVLProviderConfig(),
        val apiKey: String = "",
        val conversationThreads: Map<String, String> = emptyMap(),
    )

    private companion object {
        const val FILE_NAME = "codex_vl.enc"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rikka_codex_vl"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_LENGTH = 128
    }
}
