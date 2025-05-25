package com.example.data

import com.example.utils.FunctionResult
import com.example.daos.StorageItemsIds
import com.example.daos.StorageItemsTypesTable
import com.example.utils.logging.LogWriter
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.Application
import io.minio.*
import io.minio.errors.MinioException
import io.minio.http.Method
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object MinIOFactory {
    const val bucketName = "minio-storage-items"

    val minio = MinioClient.builder()
        .endpoint("http://minio:9000")
        .credentials("admin", "admin123")
        .build()

    fun isBucketExists(): Boolean {
        return minio.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucketName)
                .build()
        )
    }
}


fun createFileInMinio(
    uid: UUID,
    type: String,
    objectSize: Long = 0,
    partSize: Long = -1,
    stream: InputStream = ByteArrayInputStream("".toByteArray())
): FunctionResult<Long> {
    return try {
        val bucket = MinIOFactory.bucketName
        val uidString = uid.toString()

        MinIOFactory.minio.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(uidString)
                .stream(stream, objectSize, partSize)
                .contentType(type)
                .build()
        )
        LogWriter.log("createFileInMinio - File created successfully")

        FunctionResult.Success(getFileSize(uid))
    } catch (ex: Exception) {
        LogWriter.log("createFileInMinio - Get excpetion in file creation ${ex.message}")
        FunctionResult.Error("Get exception while creating file")
    }
}

fun getFileSize(uid: UUID): Long {
    val stat = MinIOFactory.minio.statObject(
        StatObjectArgs.builder()
            .bucket(MinIOFactory.bucketName)
            .`object`(uid.toString())
            .build()
    )
    return stat.size()
}

fun replaceFileMinio(uid: UUID, type: StorageItemsIds, content: String): FunctionResult<Unit> {
    return try {
        val bucket = MinIOFactory.bucketName
        val uidString = uid.toString()
        val contentBytes = content.toByteArray()

        MinIOFactory.minio.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(uidString)
                .stream(ByteArrayInputStream(contentBytes), contentBytes.size.toLong(), -1)
                .contentType(type.mimeType)
                .build()
        )

        LogWriter.log("replaceFileMinio - Updated file in minio successfully")
        FunctionResult.Success(Unit)
    } catch (ex: Exception) {
        LogWriter.log("replaceFileMinio - Error while updating file: $uid in minio")
        FunctionResult.Error("Error while updating file: $uid in minio")
    }
}

fun readFromFile(uid: String): FunctionResult<String> {
    return try {
        val stream = MinIOFactory.minio.getObject(
            GetObjectArgs.builder()
                .bucket(MinIOFactory.bucketName)
                .`object`(uid)
                .build()
        )

        FunctionResult.Success(stream.bufferedReader().use { it.readText() })
    } catch (ex: Exception) {
        FunctionResult.Error("Error while reading from file")
    }
}

fun deleteFileInMinio(uid: UUID): FunctionResult<Unit> {
    return try {
        val bucket = MinIOFactory.bucketName
        val uidString = uid.toString()

        MinIOFactory.minio.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucket)
                .`object`(uidString)
                .build()
        )

        LogWriter.log("deleteFileInMinio - Successfully removed object $uid")
        FunctionResult.Success(Unit)
    } catch (ex: MinioException) {
        LogWriter.log("deleteFileInMinio - Failed to delete $uid from MinIO $ex")
        FunctionResult.Error("MinIO error: ${ex.message}")
    } catch (ex: Exception) {
        LogWriter.log("deleteFileInMinio - I/O error on deleting $uid from MinIO $ex")
        FunctionResult.Error("I/O error: ${ex.message}")
    }
}

fun createPresignedUrl(method: Method, expiryMinutes: Int, fileUid: String) : String {
    val dotenv = dotenv()
    val externalUrl = dotenv["MINIO_EXTERNAL_URL"]

    val url = MinIOFactory.minio.getPresignedObjectUrl(
        GetPresignedObjectUrlArgs.builder()
            .method(method)
            .bucket(MinIOFactory.bucketName)
            .`object`(fileUid)
            .expiry(expiryMinutes, TimeUnit.MINUTES)
            .build()
    )

    return url.replace("http://minio:9000", externalUrl)
}

fun Application.configureMinio() {
    try {
        if (!MinIOFactory.isBucketExists()) {
            MinIOFactory.minio.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(MinIOFactory.bucketName)
                    .build()
            )
            LogWriter.log("Application.configureMinio() - Bucket created")
        } else {
            LogWriter.log("Application.configureMinio() - already exists")
        }
    } catch (e: Exception) {
        LogWriter.log("Application.configureMinio() - Error while creating bucket: ${e.message}")
    }
}

