package com.example.airlinereservationsapp

data class TravelPostDTO(
    val id: Int,
    val userId: Int,
    val userFullName: String,
    val cityIata: String,
    val category: String,
    val content: String,
    val createdAt: String,
    val photoUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val ratingSum: Int = 0,
    val ratingCount: Int = 0,
    val averageRating: Double = 0.0
)

data class CreatePostRequest(
    val userId: Int,
    val cityIata: String,
    val category: String,
    val content: String
)

data class TravelerProfileDTO(
    val userId: Int,
    val userFullName: String,
    val travelStyle: String,
    val interests: List<String>,
    val bio: String?,
    val compatibilityScore: Int = 0,
    val openToGroup: Boolean = false,
    val profilePhotoUrl: String? = null,
    val age: Int? = null,
    val languages: List<String> = emptyList(),
    val travelStartDate: String? = null,
    val travelEndDate: String? = null,
    val plannedCities: List<String> = emptyList()
)

data class UpdateProfileRequest(
    val userId: Int,
    val travelStyle: String,
    val interests: List<String>,
    val bio: String? = null,
    val openToGroup: Boolean = false,
    val age: Int? = null,
    val languages: List<String> = emptyList(),
    val travelStartDate: String? = null,
    val travelEndDate: String? = null,
    val plannedCities: List<String> = emptyList()
)

data class PrivateMessageDTO(
    val id: Int,
    val fromUserId: Int,
    val fromUserName: String,
    val toUserId: Int,
    val content: String,
    val isRead: Boolean,
    val createdAt: String
)

data class SendMessageRequest(
    val fromUserId: Int,
    val toUserId: Int,
    val content: String
)

data class InboxItem(
    val userId: Int,
    val userName: String,
    val lastMessage: String,
    val isRead: Boolean,
    val createdAt: String,
    val profilePhotoUrl: String? = null
)