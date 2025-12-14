package io.github.sslpinning.model

import org.json.JSONArray
import org.json.JSONObject

internal data class KeysResponse(
    val payload: Payload,
    val signatureBase64: String,
    val payloadRawJson: String,
) {
    data class Payload(val keys: List<KeyItem>)

    data class KeyItem(
        val domainName: String,
        val keyBase64: String,
        val fqdn: String? = null,
        val expire: Long? = null,
        val date: String? = null,
    )

    companion object {
        fun fromJson(json: String): KeysResponse {
            val root = JSONObject(json)

            val signature = root.getString("signature")
            val payloadObj = root.getJSONObject("payload")

            val keysArray = payloadObj.getJSONArray("keys")
            val keys = parseKeys(keysArray)

            val payloadRawJson = payloadObj.toString()

            return KeysResponse(
                payload = Payload(keys),
                signatureBase64 = signature,
                payloadRawJson = payloadRawJson,
            )
        }

        private fun parseKeys(arr: JSONArray): List<KeyItem> =
            List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                KeyItem(
                    domainName = o.getString("domainName"),
                    keyBase64 = o.getString("key"),
                    fqdn = o.optString("fqdn").takeIf { it.isNotBlank() },
                    expire = o.optLong("expire").takeIf { o.has("expire") },
                    date = o.optString("date").takeIf { it.isNotBlank() },
                )
            }
    }
}