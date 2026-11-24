package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class OpenFoodFactsResponse(
    val code: String? = null,
    val status: Int = 0,
    @Json(name = "status_verbose") val statusVerbose: String? = null,
    val product: OpenFoodFactsProductDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenFoodFactsProductDto(
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "product_name_it") val productNameIt: String? = null,
    val brands: String? = null,
    val categories: String? = null,
    @Json(name = "categories_tags") val categoriesTags: List<String>? = null,
    val quantity: String? = null,
    @Json(name = "image_front_url") val imageFrontUrl: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "generic_name") val genericName: String? = null,
    @Json(name = "generic_name_it") val genericNameIt: String? = null
)

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String
    ): OpenFoodFactsResponse
}
