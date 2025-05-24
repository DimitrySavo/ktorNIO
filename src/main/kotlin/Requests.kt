package com.example

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

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
data class RegisterUserWithOAuth(
    val username: String,
    val email: String? = null,
    val type: String,
    val accountId: String
)

@Serializable
data class LoginUser(
    val userEmail: String,
    val password: String
)

@Serializable
data class LoginUserOAuth(
    val accountId: String,
    val type: String
)

@Serializable
data class ResetPasswordEmail(
    val email: String?
)

@Serializable
data class ResetPasswordOtp(
    val otp: String?,
    val userEmail: String?
)

@Serializable
data class ResetPasswordNew(
    val newPassword: String?
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

@Serializable
sealed class UpdateRequest

@Serializable
@SerialName("metadata")
data class MetadataUpdateRequest(
    val name: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val parentUid: UUID? = null
) : UpdateRequest()

@Serializable
@SerialName("text")
data class TextUpdateRequest(
    val version: String?,
    val baseline: String,
    val content: String
) : UpdateRequest()

@Serializable
data class StorageItemResponse (
    @Serializable(with = UUIDSerializer::class)
    val uid: UUID,
    @Serializable(with = UUIDSerializer::class)
    val parent_id: UUID?,
    val name: String,
    val type: String,
    val version: String?,
    val created_at: Long,
    val updated_at: Long,
    val deleted_at: Long? = null
)

@Serializable
data class FileDownloadUrl(
    val url: String,
    val size: Long
)

@Serializable
data class ItemContent(
    val version: String,
    val content: String
)