package com.mtt.guildhome.api.gateway

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.JsonObject
import io.vertx.kotlin.core.json.get

data class UserProfile(val id: String, val username: String, var handle: String, var email: String, var guildIds: List<String>) {
    fun toJson() = JsonObject("id" to id, "username" to username, "handle" to handle, "email" to email, "guildIds" to JsonArray(guildIds))
}

fun parseUserProfileFromJson(json: JsonObject): UserProfile {
    val id: String = json["id"]
    val username: String = json["username"]
    val handle: String = json["handle"]
    val email: String = json["email"]
    val guildIds: List<String> = (json.getJsonArray("guildIds").toList() as List<String>)

    return UserProfile(id, username, handle, email, guildIds)
}