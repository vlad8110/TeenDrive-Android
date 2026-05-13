package com.vlad8110.teendrive.model

import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import java.util.Locale

enum class AccountRole(val title: String) {
    TEEN("Teen"),
    PARENT("Parent");

    val storageValue: String = name.lowercase()

    companion object {
        fun fromStorageValue(value: String?): AccountRole =
            entries.firstOrNull { it.storageValue == value } ?: TEEN
    }
}

data class ConnectedTeen(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val pairingCode: String,
    val teenProfileId: String = "",
    val familyGroupId: String = "",
) {
    fun encode(): String =
        listOf(id, name, pairingCode, teenProfileId, familyGroupId).joinToString("|")

    companion object {
        fun decode(encoded: String): ConnectedTeen? {
            val parts = encoded.split("|")
            if (parts.size < 5) return null
            return ConnectedTeen(
                id = parts[0],
                name = parts[1],
                pairingCode = parts[2],
                teenProfileId = parts[3],
                familyGroupId = parts[4],
            )
        }
    }
}

data class ConnectedParent(
    val id: String,
    val displayName: String,
)

data class PairingPayload(
    val code: String,
    val token: String,
    val teenName: String,
    val teenProfileId: String,
    val familyGroupId: String,
) {
    fun toUriString(): String {
        val params = listOf(
            "code" to code,
            "token" to token,
            "teen" to teenName,
            "teenID" to teenProfileId,
            "familyGroupID" to familyGroupId,
        ).joinToString("&") { (key, value) -> "$key=${java.net.URLEncoder.encode(value, Charsets.UTF_8.name())}" }
        return "teendrive://pair?$params"
    }

    companion object {
        fun parse(rawPayload: String): PairingPayload? {
            val fallback = rawPayload.trim().takeIf { it.isNotBlank() }?.let {
                PairingPayload(
                    code = it.uppercase(Locale.US),
                    token = "",
                    teenName = "",
                    teenProfileId = "",
                    familyGroupId = "",
                )
            }
            val uri = runCatching { URI(rawPayload) }.getOrNull() ?: return fallback
            if (uri.scheme != "teendrive" || uri.host != "pair") {
                return if (uri.scheme.isNullOrBlank()) fallback else null
            }
            val query = uri.rawQuery.orEmpty()
            val params = query
                .split("&")
                .filter { it.isNotBlank() }
                .mapNotNull { part ->
                    val pieces = part.split("=", limit = 2)
                    val key = pieces.firstOrNull() ?: return@mapNotNull null
                    val value = pieces.getOrNull(1).orEmpty()
                    URLDecoder.decode(key, Charsets.UTF_8.name()) to
                        URLDecoder.decode(value, Charsets.UTF_8.name())
                }
                .toMap()
            return PairingPayload(
                code = params["code"].orEmpty().uppercase(Locale.US),
                token = params["token"].orEmpty(),
                teenName = params["teen"].orEmpty(),
                teenProfileId = params["teenID"].orEmpty(),
                familyGroupId = params["familyGroupID"].orEmpty(),
            ).takeIf { it.code.isNotBlank() }
                ?: fallback
        }
    }
}
