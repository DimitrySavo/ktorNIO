package com.example

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

@Serializable
data class RegisterUser(
    val username: String,
    val userEmail: String? = null,
    val password: String? = null
)

@Serializable
data class LoginUser(
    val userEmail: String,
    val password: String
)

@Serializable
data class CreateObject(
    val type: String,
    val data: ItemObject
)

@Serializable
data class ItemObject(
    @Serializable(with = UUIDSerializer::class)
    val uid: UUID,
    @Serializable(with = UUIDSerializer::class)
    val parent_id: UUID? = null,
    val name: String
)