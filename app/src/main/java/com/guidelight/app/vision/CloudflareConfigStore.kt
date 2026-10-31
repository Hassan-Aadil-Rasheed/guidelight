package com.guidelight.app.vision

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal test credentials: hardware/OS keystore encryption, no-backup storage. */
class CloudflareConfigStore(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"cloudflare.config"))
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun read(): CloudflareCredentials? = runCatching {
        if(!file.baseFile.exists())return null
        val wrapper=Json.parseToJsonElement(file.readFully().toString(Charsets.UTF_8)).jsonObject
        val decoder=Base64.getDecoder()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),
            GCMParameterSpec(128,decoder.decode(wrapper.getValue("iv").jsonPrimitive.content)))}
        val value=Json.parseToJsonElement(cipher.doFinal(decoder.decode(wrapper.getValue("data").jsonPrimitive.content)).toString(Charsets.UTF_8)).jsonObject
        CloudflareCredentials(value.getValue("account").jsonPrimitive.content,value.getValue("token").jsonPrimitive.content)
    }.getOrNull()
    fun save(credentials: CloudflareCredentials) {
        val payload=buildJsonObject {put("account",credentials.accountId);put("token",credentials.token)}.toString().toByteArray()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val encoder=Base64.getEncoder()
        val wrapper=buildJsonObject {put("iv",encoder.encodeToString(cipher.iv));put("data",encoder.encodeToString(cipher.doFinal(payload)))}
        val output=file.startWrite()
        try {output.write(wrapper.toString().toByteArray());file.finishWrite(output)}
        catch(error: Exception){file.failWrite(output);throw error}
    }
    fun clear()=file.delete()
    companion object {private const val alias="guidelight-cloudflare-v1"}
}
