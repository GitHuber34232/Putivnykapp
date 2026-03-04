package ua.kyiv.putivnyk.data.remote.events

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import ua.kyiv.putivnyk.data.remote.events.model.EventDto

interface EventsApi {
    @GET("events")
    suspend fun getEvents(
        @Query("city") city: String = "kyiv",
        @Query("lang") language: String = "uk"
    ): Response<List<EventDto>>
}
