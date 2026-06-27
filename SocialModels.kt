package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.javatime.CurrentDateTime

object TravelPosts : Table("travel_posts") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id") references Users.id
    val cityIata = varchar("city_iata", 10)
    val category = varchar("category", 50)
    val content = text("content")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val photoUrls = text("photo_urls").default("")
    val videoUrl = varchar("video_url", 500).nullable()
    val ratingSum = integer("rating_sum").default(0)
    val ratingCount = integer("rating_count").default(0)
    override val primaryKey = PrimaryKey(id)
}

object PostRatings : Table("post_ratings") {
    val id = integer("id").autoIncrement()
    val postId = integer("post_id") references TravelPosts.id
    val userId = integer("user_id") references Users.id
    val rating = integer("rating")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}

object TravelerProfiles : Table("traveler_profiles") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id") references Users.id
    val travelStyle = varchar("travel_style", 20)
    val interests = varchar("interests", 200)
    val bio = text("bio").nullable()
    val openToGroup = bool("open_to_group").default(false)
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    val profilePhotoUrl = varchar("profile_photo_url", 255).nullable()
    val age = integer("age").nullable()
    val languages = varchar("languages", 100).default("")
    val travelStartDate = varchar("travel_start_date", 10).nullable()
    val travelEndDate = varchar("travel_end_date", 10).nullable()
    val plannedCities = varchar("planned_cities", 200).default("")
    override val primaryKey = PrimaryKey(id)

}

object PrivateMessages : Table("private_messages") {
    val id = integer("id").autoIncrement()
    val fromUserId = integer("from_user_id") references Users.id
    val toUserId = integer("to_user_id") references Users.id
    val content = text("content")
    val isRead = bool("is_read").default(false)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

@Serializable
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
@Serializable
data class CreatePostRequest(
    val userId: Int,
    val cityIata: String,
    val category: String,
    val content: String,
    val photoUrls: List<String> = emptyList(),
    val videoUrl: String? = null
)
@Serializable
data class RatePostRequest(
    val postId: Int,
    val userId: Int,
    val rating: Int
)
@Serializable
data class TravelerProfileDTO(
    val userId: Int,
    val userFullName: String,
    val travelStyle: String,
    val interests: List<String>,
    val bio: String?,
    val openToGroup: Boolean = false,
    val compatibilityScore: Int = 0,
    val profilePhotoUrl: String? = null,
    val age: Int? = null,
    val languages: List<String> = emptyList(),
    val travelStartDate: String? = null,
    val travelEndDate: String? = null,
    val plannedCities: List<String> = emptyList(),
)

@Serializable
data class UpdateProfileRequest(
    val userId: Int,
    val travelStyle: String,
    val interests: List<String>,
    val openToGroup: Boolean = false,
    val bio: String? = null,
    val profilePhotoUrl: String? = null,
    val age: Int? = null,
    val languages: List<String> = emptyList(),
    val travelStartDate: String? = null,
    val travelEndDate: String? = null,
    val plannedCities: List<String> = emptyList(),
)

@Serializable
data class PrivateMessageDTO(
    val id: Int,
    val fromUserId: Int,
    val fromUserName: String,
    val toUserId: Int,
    val content: String,
    val isRead: Boolean,
    val createdAt: String
)

@Serializable
data class SendMessageRequest(
    val fromUserId: Int,
    val toUserId: Int,
    val content: String
)

@Serializable
data class InboxItemDTO(
    val userId: Int,
    val userName: String,
    val lastMessage: String,
    val isRead: Boolean,
    val createdAt: String,
    val profilePhotoUrl: String? = null
)

object BlockedUsers : Table("blocked_users") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id") references Users.id
    val blockedUserId = integer("blocked_user_id") references Users.id
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class BlockUserRequest(
    val userId: Int,
    val blockedUserId: Int
)