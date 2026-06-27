package com.example.airlinereservationsapp

import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody

interface ApiService {
    @POST("register")
    suspend fun registerUser(@Body user: UserRequest): Response<Unit>

    @GET("flights/{flightId}/seats")
    suspend fun getSeats(@Path("flightId") flightId: Int): Response<List<SeatResponse>>

    @POST("bookings")
    suspend fun createBooking(@Body request: BookingRequest): Response<BookingResponse>

    @POST("bookings/{id}/pay")
    suspend fun payBooking(@Path("id") bookingId: Int): Response<SimpleResponse>

    @POST("updatePassengerDetails")
    suspend fun updatePassengerDetails(@Body request: PassengerDetailsRequest): Response<BookingResponse>

    @GET("flights")
    suspend fun searchFlights(
        @Query("from") origin: String,
        @Query("to") destination: String,
        @Query("date") date: String? = null
    ): Response<List<FlightItinerary>>

    @GET("bookings/{id}")
    suspend fun getBookingDetails(@Path("id") bookingId: Int): Response<BookingResponse>

    @GET("flights/calendar-prices")
    suspend fun getCalendarPrices(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("month") month: String
    ): Response<Map<String, Double>>

    @GET("airports")
    suspend fun getAirports(@Query("country") country: String? = null): Response<List<AirportResponse>>

    @GET("bookings/user/{userId}")
    suspend fun getUserBookings(@Path("userId") userId: Int): Response<List<BookingResponse>>

    @POST("bookings/{id}/checkin")
    suspend fun performCheckIn(
        @Path("id") bookingId: Int,
        @Query("paid") isPaid: Boolean,
        @Query("fee") fee: Double,
        @Query("seatId") seatId: Int? = null
    ): Response<Map<String, String>>

    @PUT("bookings/{id}/baggage")
    suspend fun updateBaggage(
        @Path("id") bookingId: Int,
        @Query("option") option: String,
        @Query("extra_price") extraPrice: Double
    ): Response<SimpleResponse>

    @POST("requests")
    suspend fun createModificationRequest(
        @Body request: ModificationRequestBody
    ): Response<SimpleResponse>

    @GET("requests/user/{userId}")
    suspend fun getUserRequests(
        @Path("userId") userId: Int
    ): Response<List<ModificationRequestResponse>>

    @GET("admin/users")
    suspend fun getAllUsers(): Response<List<AdminUserResponse>>

    @GET("admin/requests")
    suspend fun getAllRequests(): Response<List<ModificationRequestResponse>>

    @POST("admin/requests/{id}/resolve")
    suspend fun resolveRequest(
        @Path("id") requestId: Int,
        @Body body: ResolveRequestBody
    ): Response<SimpleResponse>

    @GET("cities/{iata}/posts")
    suspend fun getCityPosts(
        @Path("iata") iata: String,
        @Query("category") category: String? = null
    ): Response<List<TravelPostDTO>>

    @POST("cities/{iata}/posts")
    suspend fun createPost(
        @Path("iata") iata: String,
        @Body request: CreatePostRequest
    ): Response<Map<String, Int>>

    @GET("traveler-profile/{userId}")
    suspend fun getTravelerProfile(
        @Path("userId") userId: Int
    ): Response<TravelerProfileDTO>

    @PUT("traveler-profile")
    suspend fun updateTravelerProfile(
        @Body request: UpdateProfileRequest
    ): Response<Map<String, String>>

    @GET("cities/{iata}/travelers/compatible")
    suspend fun getCompatibleTravelers(
        @Path("iata") iata: String,
        @Query("userId") userId: Int
    ): Response<List<TravelerProfileDTO>>

    @GET("messages/{userId}")
    suspend fun getMessages(
        @Path("userId") userId: Int,
        @Query("with") withUserId: Int
    ): Response<List<PrivateMessageDTO>>

    @POST("messages")
    suspend fun sendMessage(
        @Body request: SendMessageRequest
    ): Response<Map<String, Int>>

    @GET("messages/{userId}/inbox")
    suspend fun getInbox(
        @Path("userId") userId: Int
    ): Response<List<InboxItem>>

    @Multipart
    @POST("upload/profile-photo/{userId}")
    suspend fun uploadProfilePhoto(
        @Path("userId") userId: Int,
        @Part photo: MultipartBody.Part
    ): Response<Map<String, String>>

    @POST("posts/{id}/rate")
    suspend fun ratePost(
        @Path("id") postId: Int,
        @Body request: RatePostRequest
    ): Response<Map<String, String>>

    @Multipart
    @POST("posts/{id}/photos")
    suspend fun uploadPostPhoto(
        @Path("id") postId: Int,
        @Part photo: MultipartBody.Part
    ): Response<Map<String, List<String>>>

    @GET("posts/explore")
    suspend fun explorePosts(
        @Query("limit") limit: Int = 20,
        @Query("iata") iata: String? = null
    ): Response<List<TravelPostDTO>>

    @GET("flights/connecting")
    suspend fun searchConnectingFlights(
        @Query("from") origin: String,
        @Query("to") destination: String,
        @Query("date") date: String? = null
    ): Response<List<FlightItinerary>>

    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<LoginResponse>

    @GET("admin/aircrafts")
    suspend fun getAdminAircrafts(): Response<List<AircraftDTO>>

    @GET("admin/flights")
    suspend fun getAdminFlights(): Response<List<FlightDTO>>

    @DELETE("admin/aircrafts/{id}")
    suspend fun deleteAircraft(@Path("id") id: Int): Response<SimpleResponse>

    @DELETE("admin/flights/{id}")
    suspend fun deleteFlight(@Path("id") id: Int): Response<Unit>

    @POST("admin/aircrafts")
    suspend fun createAircraft(@Body request: CreateAircraftRequest): Response<Map<String, Int>>

    @POST("admin/flights")
    suspend fun createFlight(@Body request: CreateFlightRequest): Response<Map<String, Int>>

    @GET("admin/gates")
    suspend fun getGates(
        @Query("flightId") flightId: Int? = null
    ): Response<List<GateDTO>>

    @PUT("admin/flights/{id}/gate")
    suspend fun assignGate(
        @Path("id") flightId: Int,
        @Query("gateId") gateId: Int
    ): Response<Map<String, String>>

    @POST("users/block")
    suspend fun blockUser(@Body request: BlockUserRequest): Response<Map<String, String>>

    @POST("users/unblock")
    suspend fun unblockUser(@Body request: BlockUserRequest): Response<Map<String, String>>

    @GET("users/{userId}/blocked")
    suspend fun getBlockedUsers(@Path("userId") userId: Int): Response<List<Int>>

    @PUT("admin/flights/{id}/status")
    suspend fun updateFlightStatus(
        @Path("id") flightId: Int,
        @Query("status") status: String,
        @Query("delay_minutes") delayMinutes: Int = 0
    ): Response<Map<String, String>>

    @GET("bookings/{id}/alternatives")
    suspend fun getAlternatives(
        @Path("id") bookingId: Int
    ): Response<List<AlternativeFlightDTO>>

    @POST("bookings/{id}/rebook")
    suspend fun rebookBooking(
        @Path("id") bookingId: Int,
        @Body request: RebookRequest
    ): Response<RebookResponse>

    @POST("bookings/{id}/boarding-pass")
    suspend fun generateBoardingPass(@Path("id") bookingId: Int): Response<Map<String, String>>

    @POST("admin/boarding/scan")
    suspend fun scanBoarding(@Query("token") token: String): Response<Map<String, String>>

    @GET("flights/cheap-destinations")
    suspend fun getCheapDestinations(@Query("from") from: String): Response<List<CheapDestinationDTO>>
}