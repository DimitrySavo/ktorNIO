package com.example.data

import com.example.utils.FunctionResult
import com.example.daos.StorageItemsIds
import io.ktor.server.application.Application
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import java.io.ByteArrayInputStream
import java.util.UUID

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


fun createFileInMinio(uid: UUID, type: StorageItemsIds): FunctionResult<Unit> {
    return try {
        val bucket = MinIOFactory.bucketName
        val uidString = uid.toString()
        val emptyContent = ByteArrayInputStream("".toByteArray())

        MinIOFactory.minio.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(uidString)
                .stream(emptyContent, 0, -1)
                .contentType(type.mimeType)
                .build()
        )
        println("File created successfully")
        FunctionResult.Success(Unit)
    } catch (ex: Exception) {
        println("Get excpetion in file creation ${ex.message}")
        FunctionResult.Error("Get exception while creating file")
    }
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

        println("Updated file in minio successfully")
        FunctionResult.Success(Unit)
    } catch (ex: Exception) {
        println("Error while updating")
        FunctionResult.Error("Error while updating")
    }
}

fun readFromFile(uid: String) : FunctionResult<String> {
    return try {
        val stream = MinIOFactory.minio.getObject(
            GetObjectArgs.builder()
                .bucket(MinIOFactory.bucketName)
                .`object`(uid.toString())
                .build()
        )

        FunctionResult.Success(stream.bufferedReader().use { it.readText() })
    } catch (ex: Exception) {
        FunctionResult.Error("Error while reading from file")
    }
}


fun Application.configureMinio() {
    try {
        if (!MinIOFactory.isBucketExists()) {
            MinIOFactory.minio.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(MinIOFactory.bucketName)
                    .build()
            )
            println("Bucket created")
        } else {
            println("Bucket already exists")
        }
    } catch (e: Exception) {
        println("Error while creating bucket: ${e.message}")
    }
}

