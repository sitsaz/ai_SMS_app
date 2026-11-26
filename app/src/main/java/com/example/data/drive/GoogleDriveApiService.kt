package com.example.data.drive

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleDriveApiService {
    @GET("drive/v3/about")
    suspend fun getAboutUser(
        @Header("Authorization") bearerAuth: String,
        @Query("fields") fields: String = "user(emailAddress,displayName)"
    ): Response<ResponseBody>

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") bearerAuth: String,
        @Query("q") query: String,
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "files(id,name,size,modifiedTime,appProperties)",
        @Query("orderBy") orderBy: String = "modifiedTime desc",
        @Query("pageSize") pageSize: Int = 50
    ): Response<ResponseBody>

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Header("Authorization") bearerAuth: String,
        @Body metadataBody: RequestBody,
        @Query("fields") fields: String = "id,name"
    ): Response<ResponseBody>

    @Multipart
    @POST("upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime,appProperties")
    suspend fun uploadMultipartFile(
        @Header("Authorization") bearerAuth: String,
        @Part metadata: MultipartBody.Part,
        @Part media: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") bearerAuth: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") bearerAuth: String,
        @Path("fileId") fileId: String
    ): Response<ResponseBody>
}
