package co.censo.censowalletintegration

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.Response as RetrofitResponse

interface ApiService {
    @GET("import/{encodedChannel}")
    suspend fun getImportData(
        @Path("encodedChannel") encodedChannel: String
    ): RetrofitResponse<GetImportEncryptedDataApiResponse>

    @POST("import/{encodedChannel}/encrypted")
    suspend fun setImportEncryptedData(
        @Path("encodedChannel") encodedChannel: String,
        @Body encryptedData: SetImportEncryptedDataApiRequest
    ): RetrofitResponse<ResponseBody>
}