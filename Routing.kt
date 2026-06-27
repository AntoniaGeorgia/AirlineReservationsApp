package com.example.plugins
import java.io.File
import io.ktor.server.http.content.*
import com.example.database.DatabaseFactory.dbQuery
import com.example.models.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.IntegerColumnType
import java.time.LocalDateTime
import io.ktor.utils.io.toByteArray
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.JWT_AUDIENCE
import com.example.JWT_ISSUER
import com.example.JWT_SECRET
import io.ktor.server.auth.*
import java.util.Date
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.SortOrder
import io.ktor.server.auth.jwt.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

fun calculateDynamicPrice(basePrice: Double, totalSeats: Int, occupiedSeats: Int): Double {
    if (totalSeats <= 0) return basePrice
    val occupationPercentage = (occupiedSeats.toDouble() / totalSeats.toDouble()) * 100.0
    return when {
        occupationPercentage >= 80.0 -> basePrice * 1.5
        occupationPercentage >= 50.0 -> basePrice * 1.2
        else -> basePrice
    }
}

fun ApplicationCall.isAdmin(): Boolean {
    val role = principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
    return role == "ADMIN"
}
fun ApplicationCall.currentUserId(): Int? {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
}
fun Application.configureRouting() {
    routing {
        staticFiles("/uploads", File("uploads"))
        get("/") {
            call.respondText("Serverul Airline este Online!")
        }

        post("/register") {
            try {
                val req = call.receive<User>()
                val created = dbQuery {
                    val existing = Users.selectAll()
                        .where { Users.email eq req.email }
                        .singleOrNull()
                    if (existing != null) return@dbQuery null

                    Users.insert {
                        it[fullName] = req.fullName ?: ""
                        it[email] = req.email
                        it[passwordHash] = org.mindrot.jbcrypt.BCrypt.hashpw(req.password, org.mindrot.jbcrypt.BCrypt.gensalt())
                        it[role] = req.role ?: "PASSENGER"
                    } get Users.id
                }

                if (created == null) {
                    call.respond(HttpStatusCode.Conflict, "Email deja folosit")
                } else {
                    call.respond(HttpStatusCode.Created, mapOf("id" to created))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
            }
        }


        post("/login") {
            try {
                val req = call.receive<LoginRequest>()
                val user = dbQuery {
                    Users.selectAll().where { Users.email eq req.email }
                        .singleOrNull()?.let { row ->
                            val stored = row[Users.passwordHash]

                            val passwordOk = if (stored.startsWith("$2a$")) {
                                org.mindrot.jbcrypt.BCrypt.checkpw(req.password, stored)
                            } else {
                                stored == req.password
                            }

                            if (passwordOk) {

                                if (!stored.startsWith("$2a$")) {
                                    Users.update({ Users.id eq row[Users.id] }) {
                                        it[passwordHash] = org.mindrot.jbcrypt.BCrypt.hashpw(req.password, org.mindrot.jbcrypt.BCrypt.gensalt())
                                    }
                                }
                                User(
                                    id = row[Users.id],
                                    fullName = row[Users.fullName],
                                    email = row[Users.email],
                                    password = "",
                                    role = row[Users.role]
                                )
                            } else null
                        }
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Email sau parolă incorectă")
                } else {
                    val token = JWT.create()
                        .withAudience(JWT_AUDIENCE)
                        .withIssuer(JWT_ISSUER)
                        .withClaim("userId", user.id)
                        .withClaim("role", user.role)
                        .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 24h
                        .sign(Algorithm.HMAC256(JWT_SECRET))

                    call.respond(HttpStatusCode.OK, LoginResponse(
                        token = token,
                        userId = user.id,
                        fullName = user.fullName,
                        role = user.role
                    ))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
            }
        }

        get("/flights") {
            try {
                val from = call.request.queryParameters["from"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Originea lipseste")
                val to = call.request.queryParameters["to"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Destinatia lipseste")
                val date = call.request.queryParameters["date"] // opțional

                val allFlights = dbQuery {
                    var query = Flights.selectAll()
                        .where { (Flights.origin eq from) and (Flights.destination eq to) }

                    if (!date.isNullOrEmpty()) {
                        query = query.andWhere {
                            Flights.departureTime.castTo<String>(VarCharColumnType()).like("$date%")
                        }
                    }

                    query.map { row ->
                        val flight = Flight(
                            id = row[Flights.id],
                            flightNumber = row[Flights.flightNumber],
                            origin = row[Flights.origin],
                            destination = row[Flights.destination],
                            departureTime = row[Flights.departureTime].toString(),
                            arrivalTime = row[Flights.arrivalTime].toString(),
                            currentPrice = row[Flights.currentPrice].toDouble()
                        )
                        FlightItinerary("DIR-${flight.id}", listOf(flight), flight.currentPrice, true)
                    }
                }
                call.respond(allFlights.sortedBy { it.totalPrice })
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        get("/flights/connecting") {
            try {
                val from = call.request.queryParameters["from"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val to = call.request.queryParameters["to"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val date = call.request.queryParameters["date"]

                val itineraries = dbQuery {
                    var firstLegs = Flights.selectAll()
                        .where { Flights.origin eq from }

                    if (!date.isNullOrEmpty()) {
                        firstLegs = firstLegs.andWhere {
                            Flights.departureTime.castTo<String>(VarCharColumnType()).like("$date%")
                        }
                    }

                    val result = mutableListOf<FlightItinerary>()

                    firstLegs.forEach { firstRow ->
                        val hubIata = firstRow[Flights.destination]
                        val firstArrival = firstRow[Flights.arrivalTime]

                        Flights.selectAll()
                            .where {
                                (Flights.origin eq hubIata) and
                                        (Flights.destination eq to) and
                                        (Flights.departureTime greater firstArrival)
                            }
                            .forEach { secondRow ->
                                val secondDep = secondRow[Flights.departureTime]
                                val layoverMinutes = java.time.Duration.between(
                                    firstArrival, secondDep
                                ).toMinutes()

                                if (layoverMinutes in 60..480) {
                                    val flight1 = Flight(
                                        id = firstRow[Flights.id],
                                        flightNumber = firstRow[Flights.flightNumber],
                                        origin = firstRow[Flights.origin],
                                        destination = firstRow[Flights.destination],
                                        departureTime = firstRow[Flights.departureTime].toString(),
                                        arrivalTime = firstRow[Flights.arrivalTime].toString(),
                                        currentPrice = firstRow[Flights.currentPrice].toDouble()
                                    )
                                    val flight2 = Flight(
                                        id = secondRow[Flights.id],
                                        flightNumber = secondRow[Flights.flightNumber],
                                        origin = secondRow[Flights.origin],
                                        destination = secondRow[Flights.destination],
                                        departureTime = secondRow[Flights.departureTime].toString(),
                                        arrivalTime = secondRow[Flights.arrivalTime].toString(),
                                        currentPrice = secondRow[Flights.currentPrice].toDouble()
                                    )
                                    result.add(FlightItinerary(
                                        itineraryId = "CON-${flight1.id}-${flight2.id}",
                                        flights = listOf(flight1, flight2),
                                        totalPrice = flight1.currentPrice + flight2.currentPrice,
                                        isDirect = false,
                                        layoverAirport = hubIata
                                    ))
                                }
                            }
                    }
                    result.sortedBy { it.totalPrice }.take(10)
                }
                call.respond(itineraries)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        get("/flights/cheap-destinations") {
            try {
                val from = call.request.queryParameters["from"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Originea lipseste")

                val now = java.time.LocalDateTime.now()

                val results = dbQuery {
                    Flights.selectAll()
                        .where { (Flights.origin eq from) and (Flights.departureTime greater now) }
                        .map { row ->
                            CheapDestinationDTO(
                                destination = row[Flights.destination],
                                originCity = row[Flights.origin],
                                price = row[Flights.currentPrice].toDouble(),
                                flightId = row[Flights.id],
                                departureDate = row[Flights.departureTime].toString().split("T")[0]
                            )
                        }
                        .groupBy { it.destination }
                        .map { (_, flights) -> flights.minByOrNull { it.price }!! }
                        .sortedBy { it.price }
                        .take(10)
                }
                call.respond(results)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        get("/flights/{flightId}/seats") {
            try {
                val flightId = call.parameters["flightId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val result = dbQuery {
                    val flightInfo = Flights
                        .join(Aircrafts, JoinType.INNER, onColumn = Flights.aircraftId, otherColumn = Aircrafts.id)
                        .selectAll().where { Flights.id eq flightId }
                        .singleOrNull() ?: return@dbQuery null

                    val basePrice = flightInfo[Flights.basePrice].toDouble()
                    val totalSeats = flightInfo[Aircrafts.totalSeats]
                    val occupiedCount = SeatsTable.selectAll()
                        .where { (SeatsTable.flightId eq flightId) and (SeatsTable.isOccupied eq true) }
                        .count().toInt()

                    val dynamicMultiplier = calculateDynamicPrice(1.0, totalSeats, occupiedCount)

                    SeatsTable.selectAll().where { SeatsTable.flightId eq flightId }
                        .orderBy(SeatsTable.id to SortOrder.ASC)
                        .map { row ->
                            SeatResponse(
                                id = row[SeatsTable.id],
                                seatNumber = row[SeatsTable.seatNumber],
                                isOccupied = row[SeatsTable.isOccupied],
                                seatClass = row[SeatsTable.seatClass],
                                currentPrice = basePrice * row[SeatsTable.priceMultiplier].toDouble() * dynamicMultiplier
                            )
                        }
                }
                if (result == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
            }
        }

        post("/upload/profile-photo/{userId}") {
            try {
                val userId = call.parameters["userId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                val multipart = call.receiveMultipart()
                var photoUrl: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            // Ktor 3.0.0 Way: Folosește provider() și citește pachetul complet de bytes
                            val bytes = part.provider().readRemaining().readByteArray()

                            val fileName = "profile_${userId}_${System.currentTimeMillis()}.jpg"
                            val uploadsDir = File(System.getProperty("user.dir"), "uploads")
                            if (!uploadsDir.exists()) {
                                uploadsDir.mkdirs()
                            }

                            val file = File(uploadsDir, fileName)
                            file.writeBytes(bytes)

                            photoUrl = "http://10.0.2.2:8081/uploads/$fileName"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (photoUrl != null) {
                    dbQuery {
                        TravelerProfiles.update({ TravelerProfiles.userId eq userId }) {
                            it[TravelerProfiles.profilePhotoUrl] = photoUrl!!
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("url" to photoUrl!!))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Nicio poză primită"))
                }
            } catch (e: Exception) {
                application.log.error("Upload error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        authenticate("auth-jwt") {



            get("/admin/gates") {
                if (!call.isAdmin()) return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val flightId = call.request.queryParameters["flightId"]?.toIntOrNull()

                    val gates = dbQuery {
                        if (flightId == null) {

                            Gates.selectAll().map { row ->
                                GateDTO(
                                    id = row[Gates.id],
                                    gateNumber = row[Gates.gateNumber],
                                    terminal = row[Gates.terminal]
                                )
                            }
                        } else {
                            val flight = Flights.selectAll()
                                .where { Flights.id eq flightId }
                                .singleOrNull() ?: return@dbQuery emptyList()

                            val dep = flight[Flights.departureTime]


                            val occupiedGateIds = Flights.selectAll()
                                .where {
                                    (Flights.id neq flightId) and
                                            (Flights.gateId.isNotNull()) and
                                            (Flights.departureTime greaterEq dep.minusHours(2)) and
                                            (Flights.departureTime lessEq dep.plusHours(2))
                                }
                                .mapNotNull { it[Flights.gateId] }
                                .toSet()

                            Gates.selectAll()
                                .where { Gates.id notInList occupiedGateIds.toList() }
                                .map { row ->
                                    GateDTO(
                                        id = row[Gates.id],
                                        gateNumber = row[Gates.gateNumber],
                                        terminal = row[Gates.terminal]
                                    )
                                }
                        }
                    }
                    call.respond(gates)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }


            put("/admin/flights/{id}/gate") {
                if (!call.isAdmin()) return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val flightId = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val gateId = call.request.queryParameters["gateId"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest)

                    val result = dbQuery {
                        // Ia zborul curent
                        val flight = Flights.selectAll()
                            .where { Flights.id eq flightId }
                            .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        val dep = flight[Flights.departureTime]

                        val conflict = Flights.selectAll()
                            .where {
                                (Flights.gateId eq gateId) and
                                        (Flights.id neq flightId) and
                                        (Flights.departureTime greaterEq dep.minusHours(2)) and
                                        (Flights.departureTime lessEq dep.plusHours(2))
                            }
                            .singleOrNull()

                        if (conflict != null) {
                            return@dbQuery "CONFLICT:${conflict[Flights.flightNumber]}"
                        }

                        Flights.update({ Flights.id eq flightId }) {
                            it[Flights.gateId] = gateId
                        }

                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[actionType] = "ASSIGN_GATE"
                            it[newValue] = "flightId=$flightId, gateId=$gateId"
                        }
                        "SUCCESS"
                    }

                    when {
                        result == "SUCCESS" -> call.respond(HttpStatusCode.OK, mapOf("message" to "Poartă asignată"))
                        result == "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Zborul nu există")
                        result.startsWith("CONFLICT") -> {
                            val conflictFlight = result.split(":")[1]
                            call.respond(HttpStatusCode.Conflict, mapOf("error" to "Poarta e ocupată de zborul $conflictFlight în interval de 2 ore"))
                        }
                        else -> call.respond(HttpStatusCode.InternalServerError)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }



            get("/admin/aircrafts") {
                if (!call.isAdmin()) return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val aircrafts = dbQuery {
                        Aircrafts.selectAll().map { row ->
                            AircraftDTO(
                                id = row[Aircrafts.id],
                                model = row[Aircrafts.model],
                                totalSeats = row[Aircrafts.totalSeats],
                                layoutRows = row[Aircrafts.layoutRows],
                                layoutCols = row[Aircrafts.layoutCols]
                            )
                        }
                    }
                    call.respond(aircrafts)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/admin/aircrafts") {
                if (!call.isAdmin()) return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val req = call.receive<CreateAircraftRequest>()
                    val totalSeats = req.layoutRows * req.layoutCols
                    val newId = dbQuery {
                        val id = Aircrafts.insert {
                            it[model] = req.model
                            it[layoutRows] = req.layoutRows
                            it[layoutCols] = req.layoutCols
                            it[Aircrafts.totalSeats] = req.layoutRows * req.layoutCols
                        } get Aircrafts.id

                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[actionType] = "CREATE_AIRCRAFT"
                            it[newValue] = "model=${req.model}, ${req.layoutRows}x${req.layoutCols}"
                        }
                        id
                    }
                    call.respond(HttpStatusCode.Created, mapOf("id" to newId))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            delete("/admin/aircrafts/{id}") {
                if (!call.isAdmin()) return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    val result = dbQuery {
                        val aircraft = Aircrafts.selectAll().where { Aircrafts.id eq id }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND"

                        val activeFlightIds = Flights.selectAll()
                            .where { (Flights.aircraftId eq id) and (Flights.status neq "CANCELLED") }
                            .map { it[Flights.id] }

                        if (activeFlightIds.isNotEmpty()) {
                            val replacement = Aircrafts.selectAll()
                                .where {
                                    (Aircrafts.id neq id) and
                                            (Aircrafts.layoutRows eq aircraft[Aircrafts.layoutRows]) and
                                            (Aircrafts.layoutCols eq aircraft[Aircrafts.layoutCols])
                                }
                                .firstOrNull()
                                ?: Aircrafts.selectAll().where { Aircrafts.id neq id }.firstOrNull()

                            if (replacement == null) {
                                return@dbQuery "NO_REPLACEMENT"
                            }

                            val replacementId = replacement[Aircrafts.id]
                            Flights.update({ Flights.id inList activeFlightIds }) {
                                it[Flights.aircraftId] = replacementId
                            }
                        }
                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[actionType] = "DELETE_AIRCRAFT"
                            it[oldValue] = "model=${aircraft[Aircrafts.model]}, id=$id"
                            it[newValue] = "reassigned ${activeFlightIds.size} flights"
                        }

                        Aircrafts.deleteWhere { Aircrafts.id eq id }
                        "SUCCESS:${activeFlightIds.size}"
                    }

                    when {
                        result == "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Aeronava nu există"))
                        result == "NO_REPLACEMENT" -> call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "Nu există altă aeronavă disponibilă pentru a prelua zborurile active — adaugă mai întâi o aeronavă nouă")
                        )
                        result.startsWith("SUCCESS") -> {
                            val count = result.split(":")[1]
                            call.respond(HttpStatusCode.OK, mapOf("message" to "Aeronavă ștearsă. $count zboruri reasignate."))
                        }
                        else -> call.respond(HttpStatusCode.InternalServerError)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }


            get("/admin/flights") {
                if (!call.isAdmin()) return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val flights = dbQuery {
                        (Flights innerJoin Aircrafts).selectAll().map { row ->
                            FlightDTO(
                                id = row[Flights.id],
                                flightNumber = row[Flights.flightNumber],
                                origin = row[Flights.origin],
                                destination = row[Flights.destination],
                                departureTime = row[Flights.departureTime].toString(),
                                arrivalTime = row[Flights.arrivalTime].toString(),
                                basePrice = row[Flights.basePrice].toDouble(),
                                aircraftId = row[Flights.aircraftId],
                                aircraftModel = row[Aircrafts.model]
                            )
                        }
                    }
                    call.respond(flights)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/admin/flights") {
                if (!call.isAdmin()) return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val req = call.receive<CreateFlightRequest>()
                    val newFlightId = dbQuery {
                        val existing = Flights.selectAll()
                            .where { Flights.flightNumber eq req.flightNumber }
                            .singleOrNull()
                        if (existing != null) {
                            return@dbQuery "DUPLICATE"
                        }
                        val flightId = Flights.insert {
                            it[flightNumber] = req.flightNumber
                            it[origin] = req.origin
                            it[destination] = req.destination
                            it[departureTime] = java.time.LocalDateTime.parse(req.departureTime)
                            it[arrivalTime] = java.time.LocalDateTime.parse(req.arrivalTime)
                            it[basePrice] = req.basePrice.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP)
                            it[currentPrice] = req.basePrice.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP)
                            it[aircraftId] = req.aircraftId
                        } get Flights.id
                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[actionType] = "CREATE_FLIGHT"
                            it[newValue] = "flightNumber=${req.flightNumber}, ${req.origin}->${req.destination}"
                        }

                        val departureTime = java.time.LocalDateTime.parse(req.departureTime)

                        val occupiedGateIds = Flights.selectAll()
                            .where {
                                (Flights.id neq flightId) and
                                        (Flights.gateId.isNotNull()) and
                                        (Flights.departureTime greaterEq departureTime.minusHours(2)) and
                                        (Flights.departureTime lessEq departureTime.plusHours(2))
                            }
                            .mapNotNull { it[Flights.gateId] }
                            .toSet()

                        val availableGate = Gates.selectAll()
                            .where { Gates.id notInList occupiedGateIds.toList() }
                            .firstOrNull()

                        if (availableGate != null) {
                            Flights.update({ Flights.id eq flightId }) {
                                it[Flights.gateId] = availableGate[Gates.id]
                            }
                        }
                        val aircraft = Aircrafts.selectAll()
                            .where { Aircrafts.id eq req.aircraftId }
                            .single()
                        val rows = aircraft[Aircrafts.layoutRows]
                        val cols = aircraft[Aircrafts.layoutCols]

                        for (row in 1..rows) {
                            for (col in 0 until cols) {
                                val colLetter = ('A' + col).toString()
                                val seatNumber = "$row$colLetter"
                                val seatClass = when {
                                    row <= 2 -> "BUSINESS"
                                    row <= 4 -> "PREMIUM_ECONOMY"
                                    else -> "ECONOMY"
                                }
                                val priceMultiplier = when (seatClass) {
                                    "BUSINESS" -> 2.5
                                    "PREMIUM_ECONOMY" -> 1.5
                                    else -> when (colLetter) {
                                        "A", "F" -> 1.2
                                        "C", "D" -> 1.1
                                        else -> 1.05
                                    }
                                }

                                SeatsTable.insert {
                                    it[SeatsTable.flightId] = flightId
                                    it[SeatsTable.seatNumber] = seatNumber
                                    it[SeatsTable.isOccupied] = false
                                    it[SeatsTable.seatClass] = seatClass
                                    it[SeatsTable.priceMultiplier] = priceMultiplier.toBigDecimal()
                                }
                            }
                        }
                        flightId.toString()
                    }
                    when (newFlightId) {
                        "DUPLICATE" -> call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "Numărul de zbor există deja")
                        )
                        else -> call.respond(
                            HttpStatusCode.Created,
                            mapOf("id" to newFlightId)
                        )
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            delete("/admin/flights/{id}") {
                if (!call.isAdmin()) return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    dbQuery {
                        SeatsTable.deleteWhere { SeatsTable.flightId eq id }
                        Flights.deleteWhere { Flights.id eq id }
                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[actionType] = "DELETE_FLIGHT"
                            it[oldValue] = "flightId=$id"
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Zbor șters"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            put("/admin/flights/{id}/status") {
                if (!call.isAdmin()) return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Acces interzis — necesită rol ADMIN"))
                try {
                    val flightId = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val newStatus = call.request.queryParameters["status"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val delayMinutes = call.request.queryParameters["delay_minutes"]?.toIntOrNull() ?: 0

                    val result = dbQuery {
                        val flight = Flights.selectAll()
                            .where { Flights.id eq flightId }
                            .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        val newDepartureTime = if (newStatus == "DELAYED") {
                            flight[Flights.departureTime].plusMinutes(delayMinutes.toLong())
                        } else {
                            flight[Flights.departureTime]
                        }

                        if (newStatus == "DELAYED") {
                            val currentGateId = flight[Flights.gateId]
                            if (currentGateId != null) {
                                val conflict = Flights.selectAll()
                                    .where {
                                        (Flights.gateId eq currentGateId) and
                                                (Flights.id neq flightId) and
                                                (Flights.departureTime greaterEq newDepartureTime.minusHours(2)) and
                                                (Flights.departureTime lessEq newDepartureTime.plusHours(2))
                                    }
                                    .singleOrNull()

                                if (conflict != null) {
                                    val occupiedGateIds = Flights.selectAll()
                                        .where {
                                            (Flights.id neq flightId) and
                                                    (Flights.gateId.isNotNull()) and
                                                    (Flights.departureTime greaterEq newDepartureTime.minusHours(2)) and
                                                    (Flights.departureTime lessEq newDepartureTime.plusHours(2))
                                        }
                                        .mapNotNull { it[Flights.gateId] }
                                        .toSet()

                                    val newGate = Gates.selectAll()
                                        .where { Gates.id notInList occupiedGateIds.toList() }
                                        .firstOrNull()

                                    if (newGate != null) {
                                        Flights.update({ Flights.id eq flightId }) {
                                            it[Flights.gateId] = newGate[Gates.id]
                                        }
                                    }
                                }
                            }
                        }

                        Flights.update({ Flights.id eq flightId }) {
                            it[Flights.status] = newStatus
                            it[Flights.delayMinutes] = delayMinutes
                            if (newStatus == "DELAYED") {
                                it[Flights.departureTime] = newDepartureTime
                            }
                        }

                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[actionType] = "UPDATE_FLIGHT_STATUS"
                            it[oldValue] = flight[Flights.status]
                            it[newValue] = newStatus
                        }

                        if (newStatus == "CANCELLED") {
                            BookingsTable.update({
                                (BookingsTable.flightId eq flightId) and
                                        (BookingsTable.status inList listOf("PAID", "CHECKED_IN"))
                            }) {
                                it[BookingsTable.status] = "REFUND_PENDING"
                            }
                        }

                        "SUCCESS"
                    }

                    when (result) {
                        "SUCCESS" -> call.respond(HttpStatusCode.OK, mapOf("message" to "Status actualizat"))
                        "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Zborul nu există")
                        else -> call.respond(HttpStatusCode.InternalServerError)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/bookings") {
                try {
                    val request = call.receive<BookingRequest>()
                    application.log.info("BookingRequest: flightId=${request.flightId}, seatId=${request.seatId}, firstSeatId=${request.firstSeatId}, secondFlightId=${request.secondFlightId}")

                    val bookingResult = dbQuery {
                        val isRoundTrip = (request.secondFlightId ?: 0) > 0

                        val outboundSeatId = if (isRoundTrip) request.firstSeatId else request.seatId
                        val returnSeatId   = if (isRoundTrip) request.seatId else null

                        fun occupySeat(seatId: Int): String {
                            val conn = TransactionManager.current().connection
                            val stmt = conn.prepareStatement(
                                "SELECT id, seat_number FROM seats WHERE id = ? AND is_occupied = false FOR UPDATE",
                                false
                            )
                            stmt.fillParameters(listOf(Pair(IntegerColumnType(), seatId)))
                            val rs = stmt.executeQuery()
                            if (!rs.next()) throw IllegalStateException("Locul este deja ocupat")
                            val label = rs.getString("seat_number")
                            SeatsTable.update({ SeatsTable.id eq seatId }) {
                                it[isOccupied] = true
                                it[userId] = request.userId
                            }
                            return label
                        }

                        fun checkOverbooking(fId: Int) {
                            val flightInfo = (Flights innerJoin Aircrafts).selectAll()
                                .where { Flights.id eq fId }
                                .singleOrNull() ?: throw IllegalStateException("Zborul nu există")
                            val totalSeats = flightInfo[Aircrafts.totalSeats]
                            val maxSellable = totalSeats + (totalSeats * flightInfo[Flights.overbookingPercent]) / 100
                            val active = BookingsTable.selectAll()
                                .where {
                                    (BookingsTable.flightId eq fId) and
                                            (BookingsTable.status notInList listOf("CANCELLED", "REFUND_PENDING"))
                                }
                                .count().toInt()
                            if (active >= maxSellable) throw IllegalStateException("Zborul este complet vândut")
                        }

                        fun insertBooking(fId: Int, sId: Int?, label: String, price: Double, linkedTo: Int?, baggageOpt: String): Int =
                            BookingsTable.insert {
                                it[userId] = request.userId
                                it[BookingsTable.flightId] = fId
                                if (sId != null) it[BookingsTable.seatId] = sId
                                it[seatLabel] = label
                                it[status] = "PENDING"
                                it[finalPrice] = price.toBigDecimal()
                                it[seatClass] = request.seatClass ?: "ECONOMY"
                                it[BookingsTable.baggageOption] = baggageOpt
                                if (linkedTo != null) it[linkedBookingId] = linkedTo
                            } get BookingsTable.id

                        val total = request.finalPrice ?: 0.0
                        var outboundPrice = total
                        var returnPrice = 0.0
                        if (isRoundTrip) {
                            val pOut = Flights.selectAll().where { Flights.id eq request.flightId }
                                .single()[Flights.currentPrice].toDouble()
                            val pRet = Flights.selectAll().where { Flights.id eq request.secondFlightId!! }
                                .single()[Flights.currentPrice].toDouble()
                            returnPrice = if (pOut + pRet > 0) total * pRet / (pOut + pRet) else total / 2
                            outboundPrice = total - returnPrice
                        }

                        // ── DUS ──
                        val outboundLabel = if (outboundSeatId != null && outboundSeatId > 0) {
                            occupySeat(outboundSeatId)
                        } else {
                            checkOverbooking(request.flightId)
                            ""
                        }
                        val mainId = insertBooking(
                            request.flightId,
                            outboundSeatId?.takeIf { it > 0 },
                            outboundLabel, outboundPrice, null,
                            request.baggageOption ?: "HAND_ONLY"
                        )

                        // ── RETUR ──
                        if (isRoundTrip) {
                            val retLabel = if (returnSeatId != null && returnSeatId > 0) {
                                occupySeat(returnSeatId)
                            } else {
                                checkOverbooking(request.secondFlightId!!)
                                ""
                            }
                            insertBooking(
                                request.secondFlightId!!,
                                returnSeatId?.takeIf { it > 0 },
                                retLabel, returnPrice, mainId,
                                request.secondBaggageOption ?: request.baggageOption ?: "HAND_ONLY"
                            )
                        }

                        Pair(mainId, outboundLabel.ifEmpty { null })
                    }

                    call.respond(HttpStatusCode.Created, BookingResponse(
                        bookingId = bookingResult.first,
                        status = "PENDING",
                        seatLabel = bookingResult.second,
                        message = "Rezervare creată"
                    ))
                } catch (e: Exception) {
                    application.log.error("POST /bookings a crapat", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.localizedMessage))
                }
            }

            post("/bookings/{id}/pay") {
                try {
                    val bookingId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "ID invalid")

                    val result = dbQuery {
                        val booking = BookingsTable.selectAll()
                            .where { BookingsTable.id eq bookingId }
                            .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        if (!BookingStateMachine.isValidTransition(booking[BookingsTable.status], "PAID"))
                            return@dbQuery "INVALID_STATUS"

                        val linkedBookings = BookingsTable.selectAll()
                            .where { (BookingsTable.id eq bookingId) or (BookingsTable.linkedBookingId eq bookingId) }
                            .toList()

                        BookingsTable.update({
                            (BookingsTable.id eq bookingId) or (BookingsTable.linkedBookingId eq bookingId)
                        }) {
                            it[status] = "PAID"
                        }

                        linkedBookings.forEach { row ->
                            TransactionsTable.insert {
                                it[TransactionsTable.bookingId] = row[BookingsTable.id]
                                it[amount] = row[BookingsTable.finalPrice]
                                it[paymentMethod] = "CARD"
                            }
                        }

                        "SUCCESS"
                    }

                    when (result) {
                        "SUCCESS" -> call.respond(
                            HttpStatusCode.OK,
                            BookingResponse(bookingId = bookingId, status = "PAID", message = "Plată efectuată cu succes")
                        )
                        "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Rezervarea nu există")
                        else -> call.respond(HttpStatusCode.BadRequest, "Rezervarea nu este în stare PENDING")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }


        authenticate("auth-jwt") {
            put("/traveler-profile") {
                try {
                    val req = call.receive<UpdateProfileRequest>()
                    val result = dbQuery {
                        val existing = TravelerProfiles.selectAll()
                            .where { TravelerProfiles.userId eq req.userId }
                            .singleOrNull()

                        if (existing == null) {
                            TravelerProfiles.insert {
                                it[userId] = req.userId
                                it[travelStyle] = req.travelStyle
                                it[interests] = req.interests.joinToString(",")
                                it[bio] = req.bio
                                it[openToGroup] = req.openToGroup
                                it[age] = req.age
                                it[languages] = req.languages.joinToString(",")
                                it[travelStartDate] = req.travelStartDate
                                it[travelEndDate] = req.travelEndDate
                                it[plannedCities] = req.plannedCities.joinToString(",")
                            }
                        } else {
                            TravelerProfiles.update({ TravelerProfiles.userId eq req.userId }) {
                                it[travelStyle] = req.travelStyle
                                it[interests] = req.interests.joinToString(",")
                                it[bio] = req.bio
                                it[openToGroup] = req.openToGroup
                                it[age] = req.age
                                it[languages] = req.languages.joinToString(",")
                                it[travelStartDate] = req.travelStartDate
                                it[travelEndDate] = req.travelEndDate
                                it[plannedCities] = req.plannedCities.joinToString(",")
                            }
                        }
                        "SUCCESS"
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Profil salvat"))
                } catch (e: Exception) {
                    application.log.error("Update profile error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
            get("/traveler-profile/{userId}") {
                try {
                    val userId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                    val profile = dbQuery {
                        (TravelerProfiles innerJoin Users)
                            .selectAll()
                            .where { TravelerProfiles.userId eq userId }
                            .singleOrNull()?.let { row ->
                                TravelerProfileDTO(
                                    userId = row[TravelerProfiles.userId],
                                    userFullName = row[Users.fullName],
                                    travelStyle = row[TravelerProfiles.travelStyle],
                                    interests = row[TravelerProfiles.interests]
                                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    bio = row[TravelerProfiles.bio],
                                    openToGroup = row[TravelerProfiles.openToGroup],
                                    profilePhotoUrl = row[TravelerProfiles.profilePhotoUrl],
                                    age = row[TravelerProfiles.age],
                                    languages = row[TravelerProfiles.languages]
                                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    travelStartDate = row[TravelerProfiles.travelStartDate],
                                    travelEndDate = row[TravelerProfiles.travelEndDate],
                                    plannedCities = row[TravelerProfiles.plannedCities]
                                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                )
                            }
                    }

                    if (profile == null) call.respond(HttpStatusCode.NotFound)
                    else call.respond(profile)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/cities/{iata}/travelers/compatible") {
                try {
                    val iata = call.parameters["iata"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val userId = call.request.queryParameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                    val result = dbQuery {
                        // 1. Interesele și orașele mele
                        val myProfile = TravelerProfiles.selectAll()
                            .where { TravelerProfiles.userId eq userId }
                            .singleOrNull()
                        val myInterests = myProfile?.get(TravelerProfiles.interests)
                            ?.split(",")?.map { it.trim() } ?: emptyList()
                        val myPlannedCities = myProfile?.get(TravelerProfiles.plannedCities)
                            ?.split(",")?.map { it.trim() } ?: emptyList()

                        // 2. Datele mele de zbor din rezervări active
                        val myFlightDates = (BookingsTable innerJoin Flights)
                            .selectAll()
                            .where {
                                (BookingsTable.userId eq userId) and
                                        (BookingsTable.status inList listOf("PAID", "CHECKED_IN"))
                            }
                            .map { row ->
                                Pair(
                                    row[Flights.departureTime].toString().take(10),
                                    row[Flights.arrivalTime].toString().take(10)
                                )
                            }

                        // 3. Useri cu rezervări active pe destinația iata
                        val usersWithBookings = (BookingsTable innerJoin Flights)
                            .selectAll()
                            .where {
                                (Flights.destination eq iata) and
                                        (BookingsTable.status inList listOf("PAID", "CHECKED_IN")) and
                                        (BookingsTable.userId neq userId)
                            }
                            .map { it[BookingsTable.userId] }
                            .toSet()

                        if (usersWithBookings.isEmpty()) return@dbQuery emptyList()

                        // 4. Profilele lor cu scor compatibilitate
                        (TravelerProfiles innerJoin Users)
                            .selectAll()
                            .where { TravelerProfiles.userId inList usersWithBookings.toList() }
                            .map { row ->
                                val theirUserId = row[TravelerProfiles.userId]
                                val theirInterests = row[TravelerProfiles.interests]
                                    .split(",").map { it.trim() }
                                val theirCities = row[TravelerProfiles.plannedCities]
                                    .split(",").map { it.trim() }

                                // Datele lor de zbor din rezervări active
                                val theirFlightDates = (BookingsTable innerJoin Flights)
                                    .selectAll()
                                    .where {
                                        (BookingsTable.userId eq theirUserId) and
                                                (BookingsTable.status inList listOf("PAID", "CHECKED_IN"))
                                    }
                                    .map { r ->
                                        Pair(
                                            r[Flights.departureTime].toString().take(10),
                                            r[Flights.arrivalTime].toString().take(10)
                                        )
                                    }

                                // Scor: interese comune + orașe comune + suprapunere perioade zbor
                                var score = myInterests.intersect(theirInterests.toSet()).size
                                score += myPlannedCities.intersect(theirCities.toSet()).size

                                val periodsOverlap = myFlightDates.any { (myStart, myEnd) ->
                                    theirFlightDates.any { (theirStart, theirEnd) ->
                                        myStart <= theirEnd && theirStart <= myEnd
                                    }
                                }
                                if (periodsOverlap) score += 1

                                TravelerProfileDTO(
                                    userId = theirUserId,
                                    userFullName = row[Users.fullName],
                                    travelStyle = row[TravelerProfiles.travelStyle],
                                    interests = theirInterests,
                                    bio = row[TravelerProfiles.bio],
                                    openToGroup = row[TravelerProfiles.openToGroup],
                                    compatibilityScore = score,
                                    profilePhotoUrl = row[TravelerProfiles.profilePhotoUrl],
                                    age = row[TravelerProfiles.age],
                                    languages = row[TravelerProfiles.languages]
                                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    travelStartDate = null,
                                    travelEndDate = null,
                                    plannedCities = theirCities.filter { it.isNotEmpty() }
                                )
                            }
                            .sortedByDescending { it.compatibilityScore }
                    }
                    call.respond(result)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }


            post("/users/block") {
                try {
                    val req = call.receive<BlockUserRequest>()
                    dbQuery {
                        val existing = BlockedUsers.selectAll()
                            .where {
                                (BlockedUsers.userId eq req.userId) and
                                        (BlockedUsers.blockedUserId eq req.blockedUserId)
                            }.singleOrNull()

                        if (existing == null) {
                            BlockedUsers.insert {
                                it[userId] = req.userId
                                it[blockedUserId] = req.blockedUserId
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Utilizator blocat"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/users/unblock") {
                try {
                    val req = call.receive<BlockUserRequest>()
                    dbQuery {
                        BlockedUsers.deleteWhere {
                            (BlockedUsers.userId eq req.userId) and
                                    (BlockedUsers.blockedUserId eq req.blockedUserId)
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Utilizator deblocat"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/users/{userId}/blocked") {
                try {
                    val userId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val blockedIds = dbQuery {
                        BlockedUsers.selectAll()
                            .where { BlockedUsers.userId eq userId }
                            .map { it[BlockedUsers.blockedUserId] }
                    }
                    call.respond(blockedIds)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

        post("/updatePassengerDetails") {
            try {
                val request = call.receive<PassengerDetailsRequest>()

                val result = dbQuery {
                    val current = BookingsTable.selectAll()
                        .where { BookingsTable.id eq request.bookingId }
                        .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                    if (current[BookingsTable.status] !in listOf("PENDING", "PAID"))
                        return@dbQuery "NEED_PAYMENT"

                    BookingsTable.update({ BookingsTable.id eq request.bookingId }) {
                        it[firstNamePassenger] = request.firstName
                        it[lastNamePassenger] = request.lastName
                    }
                    "SUCCESS"
                }

                when (result) {
                    "SUCCESS" -> call.respond(
                        HttpStatusCode.OK,
                        BookingResponse(
                            bookingId = request.bookingId,
                            status = "PAID",
                            message = "Date pasager salvate"
                        )
                    )
                    "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Rezervarea nu există")
                    else -> call.respond(HttpStatusCode.BadRequest, "Trebuie să plătești înainte")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
            }
        }


        get("/bookings/{id}") {
            try {
                val bookingId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val booking = dbQuery {
                    (BookingsTable innerJoin Flights)
                        .selectAll()
                        .where { BookingsTable.id eq bookingId }
                        .singleOrNull()?.let { row ->
                            BookingResponse(
                                flightId = row[BookingsTable.flightId],
                                bookingId = row[BookingsTable.id],
                                status = row[BookingsTable.status],
                                seatLabel = row[BookingsTable.seatLabel],
                                boardingToken = row[BookingsTable.boardingToken],
                                message = "OK",
                                origin = row[Flights.origin],
                                destination = row[Flights.destination],
                                flightNumber = row[Flights.flightNumber],
                                departureTime = row[Flights.departureTime].toString(),
                                finalPrice = row[BookingsTable.finalPrice].toDouble(),
                                seatClass = row[BookingsTable.seatClass],
                                baggageOption = row[BookingsTable.baggageOption],
                                firstNamePassenger = row[BookingsTable.firstNamePassenger],  // ← adaugă
                                lastNamePassenger = row[BookingsTable.lastNamePassenger],     // ← adaugă
                                qrCode = row[BookingsTable.boardingToken]
                            )
                        }
                }
                if (booking == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(booking)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
            }
        }}

        get("/flights/calendar-prices") {
            try {
                val fromParam = call.request.queryParameters["from"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val toParam = call.request.queryParameters["to"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val monthParam = call.request.queryParameters["month"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val pricesMap = dbQuery {
                    val result = mutableMapOf<String, Double>()

                    (Flights innerJoin Aircrafts).selectAll().where {
                        (Flights.origin eq fromParam) and
                                (Flights.destination eq toParam) and
                                (Flights.departureTime.castTo<String>(VarCharColumnType()).like("$monthParam%"))
                    }.forEach { row ->
                        val fId = row[Flights.id]
                        val base = row[Flights.basePrice].toDouble()
                        val cap = row[Aircrafts.totalSeats]
                        val occ = BookingsTable.selectAll().where {
                            (BookingsTable.flightId eq fId) and
                                    (BookingsTable.status neq "CANCELLED")
                        }.count().toInt()
                        val price = calculateDynamicPrice(base, cap, occ)
                        val dateStr = row[Flights.departureTime].toString().split("T")[0]
                        result[dateStr] = minOf(result[dateStr] ?: Double.MAX_VALUE, price)
                    }

                    Flights.selectAll().where {
                        (Flights.origin eq fromParam) and
                                (Flights.departureTime.castTo<String>(VarCharColumnType()).like("$monthParam%"))
                    }.forEach { firstRow ->
                        val hubIata = firstRow[Flights.destination]
                        val firstArrival = firstRow[Flights.arrivalTime]
                        val firstPrice = firstRow[Flights.basePrice].toDouble()
                        val dateStr = firstRow[Flights.departureTime].toString().split("T")[0]

                        Flights.selectAll().where {
                            (Flights.origin eq hubIata) and
                                    (Flights.destination eq toParam) and
                                    (Flights.departureTime greater firstArrival)
                        }.forEach { secondRow ->
                            val secondDep = secondRow[Flights.departureTime]
                            val layoverMinutes = java.time.Duration.between(firstArrival, secondDep).toMinutes()

                            if (layoverMinutes in 60..480) {
                                val totalPrice = firstPrice + secondRow[Flights.basePrice].toDouble()
                                result[dateStr] = minOf(result[dateStr] ?: Double.MAX_VALUE, totalPrice)
                            }
                        }
                    }

                    result
                }
                application.log.info("Calendar prices result: $pricesMap")
                call.respond(pricesMap)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        authenticate("auth-jwt") {
            get("/bookings/user/{userId}") {
                try {
                    val userId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "ID invalid")

                    val bookings = dbQuery {
                        (BookingsTable innerJoin Flights)
                            .selectAll()
                            .where { BookingsTable.userId eq userId }
                            .orderBy(Flights.departureTime to SortOrder.DESC)
                            .map { row ->
                                BookingResponse(
                                    flightId = row[BookingsTable.flightId],
                                    bookingId = row[BookingsTable.id],
                                    status = row[BookingsTable.status],
                                    seatLabel = row[BookingsTable.seatLabel],
                                    boardingToken = row[BookingsTable.boardingToken],
                                    message = "OK",
                                    origin = row[Flights.origin],
                                    destination = row[Flights.destination],
                                    flightNumber = row[Flights.flightNumber],
                                    departureTime = row[Flights.departureTime].toString(),
                                    finalPrice = row[BookingsTable.finalPrice].toDouble(),
                                    seatClass = row[BookingsTable.seatClass],
                                    baggageOption = row[BookingsTable.baggageOption],
                                    flightStatus = row[Flights.status],
                                    delayMinutes = row[Flights.delayMinutes]
                                )
                            }
                    }
                    call.respond(bookings)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }


            post("/bookings/{id}/checkin") {
                try {
                    val bookingId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "ID invalid")
                    val isPaid = call.request.queryParameters["paid"]?.toBoolean() ?: false
                    val fee = call.request.queryParameters["fee"]?.toDoubleOrNull() ?: 0.0
                    val chosenSeatId = call.request.queryParameters["seatId"]?.toIntOrNull()

                    val result = dbQuery {
                        val booking = BookingsTable.selectAll()
                            .where { BookingsTable.id eq bookingId }
                            .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        if (!BookingStateMachine.isValidTransition(booking[BookingsTable.status], "CHECKED_IN"))
                            return@dbQuery "INVALID_STATUS"

                        val currentSeatId = booking[BookingsTable.seatId]
                        val seatClass = booking[BookingsTable.seatClass] ?: "ECONOMY"
                        val flightId = booking[BookingsTable.flightId]


                        if (chosenSeatId != null && chosenSeatId > 0) {
                            val chosenSeat = SeatsTable.selectAll()
                                .where { (SeatsTable.id eq chosenSeatId) and (SeatsTable.isOccupied eq false) }
                                .singleOrNull() ?: return@dbQuery "SEAT_TAKEN"


                            if (currentSeatId != null && currentSeatId > 0) {
                                SeatsTable.update({ SeatsTable.id eq currentSeatId }) {
                                    it[isOccupied] = false
                                    it[userId] = null
                                }
                            }

                            SeatsTable.update({ SeatsTable.id eq chosenSeatId }) {
                                it[isOccupied] = true
                                it[userId] = booking[BookingsTable.userId]
                            }

                            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                                it[status] = "CHECKED_IN"
                                it[checkInStatus] = "CHECKED_IN"
                                it[checkInTime] = java.time.LocalDateTime.now()
                                it[BookingsTable.seatId] = chosenSeatId
                                it[seatLabel] = chosenSeat[SeatsTable.seatNumber]
                                if (fee > 0) {
                                    it[finalPrice] = (booking[BookingsTable.finalPrice].toDouble() + fee).toBigDecimal()
                                }
                            }
                        } else if (currentSeatId == null || currentSeatId == -1) {

                            val availableSeat = SeatsTable.selectAll()
                                .where {
                                    (SeatsTable.flightId eq flightId) and
                                            (SeatsTable.isOccupied eq false) and
                                            (SeatsTable.seatClass eq seatClass)
                                }
                                .shuffled()
                                .firstOrNull()

                            if (availableSeat == null) {

                                val voucherCode = "VCH-" + UUID.randomUUID().toString().take(8).uppercase()
                                BookingsTable.update({ BookingsTable.id eq bookingId }) {
                                    it[status] = "REFUND_PENDING"
                                    it[BookingsTable.voucherCode] = voucherCode
                                    it[BookingsTable.voucherAmount] = 250.toBigDecimal()
                                }
                                return@dbQuery "OVERBOOKED:$voucherCode"
                            }

                            SeatsTable.update({ SeatsTable.id eq availableSeat[SeatsTable.id] }) {
                                it[isOccupied] = true
                                it[userId] = booking[BookingsTable.userId]
                            }

                            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                                it[status] = "CHECKED_IN"
                                it[checkInStatus] = "CHECKED_IN"
                                it[checkInTime] = java.time.LocalDateTime.now()
                                it[BookingsTable.seatId] = availableSeat[SeatsTable.id]
                                it[seatLabel] = availableSeat[SeatsTable.seatNumber]
                                if (isPaid && fee > 0) {
                                    it[finalPrice] = (booking[BookingsTable.finalPrice].toDouble() + fee).toBigDecimal()
                                }
                            }
                        } else {

                            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                                it[status] = "CHECKED_IN"
                                it[checkInStatus] = "CHECKED_IN"
                                it[checkInTime] = java.time.LocalDateTime.now()
                                if (isPaid && fee > 0) {
                                    it[finalPrice] = (booking[BookingsTable.finalPrice].toDouble() + fee).toBigDecimal()
                                }
                            }
                        }
                        "SUCCESS"
                    }

                    when {
                        result == "SUCCESS" -> call.respond(HttpStatusCode.OK, mapOf("message" to "Check-in efectuat"))
                        result == "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Rezervarea nu exista")
                        result == "NO_SEATS" -> call.respond(HttpStatusCode.Conflict, "Nu sunt locuri disponibile")
                        result == "SEAT_TAKEN" -> call.respond(HttpStatusCode.Conflict, "Locul ales este ocupat")
                        result.startsWith("OVERBOOKED") -> call.respond(
                            HttpStatusCode.Conflict,
                            mapOf(
                                "overbooked" to "true",
                                "voucherCode" to result.split(":")[1],
                                "voucherAmount" to "250"
                            )
                        )

                        else -> call.respond(HttpStatusCode.BadRequest, "Statusul nu permite check-in")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }


            put("/bookings/{id}/baggage") {
                try {
                    val bookingId = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, "ID invalid")
                    val option = call.request.queryParameters["option"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, "Optiune lipsa")
                    val extraPrice = call.request.queryParameters["extra_price"]?.toDoubleOrNull() ?: 0.0

                    val result = dbQuery {
                        val booking = BookingsTable.selectAll()
                            .where { BookingsTable.id eq bookingId }
                            .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        if (booking[BookingsTable.status] in listOf("CHECKED_IN", "BOARDED", "CANCELLED"))
                            return@dbQuery "INVALID_STATUS"

                        BookingsTable.update({ BookingsTable.id eq bookingId }) {
                            it[baggageOption] = option
                            it[baggagePrice] = extraPrice.toBigDecimal()
                            if (extraPrice > 0) {
                                it[finalPrice] =
                                    (booking[BookingsTable.finalPrice].toDouble() + extraPrice).toBigDecimal()
                            }
                        }
                        "SUCCESS"
                    }

                    when (result) {
                        "SUCCESS" -> call.respond(HttpStatusCode.OK, mapOf("message" to "Bagaj actualizat"))
                        "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Rezervarea nu exista")
                        else -> call.respond(HttpStatusCode.BadRequest, "Nu poti modifica bagajul dupa check-in")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }

            get("/bookings/{id}/alternatives") {
                try {
                    val bookingId = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)

                    val result = dbQuery {
                        val booking = BookingsTable.selectAll().where { BookingsTable.id eq bookingId }
                            .singleOrNull() ?: return@dbQuery null

                        val currentFlight = Flights.selectAll().where { Flights.id eq booking[BookingsTable.flightId] }
                            .singleOrNull() ?: return@dbQuery null

                        val isRefundPending = booking[BookingsTable.status] == "REFUND_PENDING"

                        val hasApprovedDestinationChange = !isRefundPending && ModificationRequests.selectAll()
                            .where {
                                (ModificationRequests.bookingId eq bookingId) and
                                        (ModificationRequests.status eq "APPROVED") and
                                        (ModificationRequests.requestType eq "DESTINATION")
                            }
                            .count() > 0

                        var baseQuery = (Flights innerJoin Aircrafts).selectAll()
                            .where {
                                (Flights.origin eq currentFlight[Flights.origin]) and
                                        (Flights.id neq currentFlight[Flights.id]) and
                                        (Flights.status neq "CANCELLED") and
                                        (Flights.departureTime greater java.time.LocalDateTime.now().plusHours(2))
                            }

                        baseQuery = if (hasApprovedDestinationChange) {
                            baseQuery.andWhere { Flights.destination neq currentFlight[Flights.destination] }
                        } else {
                            baseQuery.andWhere { Flights.destination eq currentFlight[Flights.destination] }
                        }

                        baseQuery.map { row ->
                            val flightId = row[Flights.id]
                            val totalSeats = row[Aircrafts.totalSeats]
                            val occupied = SeatsTable.selectAll()
                                .where { (SeatsTable.flightId eq flightId) and (SeatsTable.isOccupied eq true) }
                                .count().toInt()
                            AlternativeFlightDTO(
                                flightId = flightId,
                                flightNumber = row[Flights.flightNumber],
                                origin = row[Flights.origin],
                                destination = row[Flights.destination],
                                departureTime = row[Flights.departureTime].toString(),
                                currentPrice = row[Flights.currentPrice].toDouble(),
                                priceDifference = row[Flights.currentPrice].toDouble() - booking[BookingsTable.finalPrice].toDouble(),
                                freeSeats = totalSeats - occupied,
                                isFree = !hasApprovedDestinationChange
                            )
                        }
                            .filter { it.freeSeats > 0 }
                            .sortedBy { it.departureTime }
                    }

                    if (result == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rezervarea nu exista"))
                    else call.respond(result)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }


            post("/bookings/{id}/rebook") {
                try {
                    val bookingId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val req = call.receive<RebookRequest>()

                    val booking = dbQuery {
                        BookingsTable.selectAll().where { BookingsTable.id eq bookingId }.singleOrNull()
                    } ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rezervarea nu exista"))

                    val isRefundPending = booking[BookingsTable.status] == "REFUND_PENDING"

                    val approvedRequest = if (!isRefundPending) {
                        dbQuery {
                            ModificationRequests.selectAll()
                                .where {
                                    (ModificationRequests.bookingId eq bookingId) and
                                            (ModificationRequests.status eq "APPROVED") and
                                            (ModificationRequests.requestType inList listOf("DESTINATION", "TIME"))
                                }
                                .orderBy(ModificationRequests.resolvedAt to SortOrder.DESC)
                                .firstOrNull()
                        }
                    } else null

                    if (!isRefundPending && approvedRequest == null) {
                        return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nu ai un request aprobat pentru schimbare de zbor"))
                    }

                    val isPaidChange = approvedRequest?.get(ModificationRequests.requestType) == "DESTINATION"

                    var resultCode = "NOT_FOUND"
                    var response: RebookResponse? = null

                    dbQuery {
                        val newFlight = Flights.selectAll().where { Flights.id eq req.newFlightId }.singleOrNull()
                        if (newFlight == null) { resultCode = "FLIGHT_NOT_FOUND"; return@dbQuery }

                        val oldSeatId = booking[BookingsTable.seatId]
                        if (oldSeatId != null) {
                            SeatsTable.update({ SeatsTable.id eq oldSeatId }) {
                                it[isOccupied] = false
                                it[userId] = null
                            }
                        }

                        val priceDiff = newFlight[Flights.currentPrice].toDouble() - booking[BookingsTable.finalPrice].toDouble()
                        val newFinalPrice = if (isPaidChange)
                            (booking[BookingsTable.finalPrice].toDouble() + priceDiff).toBigDecimal()
                        else booking[BookingsTable.finalPrice]

                        BookingsTable.update({ BookingsTable.id eq bookingId }) {
                            it[flightId] = req.newFlightId
                            it[seatId] = null
                            it[seatLabel] = ""
                            it[checkInStatus] = "NOT_CHECKED_IN"
                            it[checkInTime] = null
                            if (isRefundPending) it[status] = "PAID"
                            if (isPaidChange) it[finalPrice] = newFinalPrice
                        }

                        if (approvedRequest != null) {
                            ModificationRequests.update({ ModificationRequests.id eq approvedRequest[ModificationRequests.id] }) {
                                it[status] = "COMPLETED"
                            }
                        }

                        val message = when {
                            !isPaidChange -> "Rezervare mutată cu succes, fără cost suplimentar"
                            priceDiff > 0 -> "Rezervare mutată — diferență de %.2f € adăugată la rezervare".format(priceDiff)
                            priceDiff < 0 -> "Rezervare mutată — preț redus cu %.2f €".format(-priceDiff)
                            else -> "Rezervare mutată, fără diferență de preț"
                        }

                        response = RebookResponse(
                            bookingId = bookingId,
                            newFlightId = req.newFlightId,
                            newFlightNumber = newFlight[Flights.flightNumber],
                            newSeatLabel = "Va fi alocat la check-in",
                            newDepartureTime = newFlight[Flights.departureTime].toString(),
                            status = if (isRefundPending) "PAID" else booking[BookingsTable.status],
                            message = message
                        )
                        resultCode = "SUCCESS"
                    }

                    when (resultCode) {
                        "SUCCESS" -> call.respond(HttpStatusCode.OK, response!!)
                        "FLIGHT_NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Zborul nou nu exista"))
                        else -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rezervarea nu exista"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/bookings/{id}/boarding-pass") {
                try {
                    val bookingId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest)

                    val token = dbQuery {
                        val booking = BookingsTable.selectAll()
                            .where { BookingsTable.id eq bookingId }
                            .singleOrNull() ?: return@dbQuery null

                        if (booking[BookingsTable.status] != "CHECKED_IN")
                            return@dbQuery null

                        val existingToken = booking[BookingsTable.boardingToken]
                        if (!existingToken.isNullOrEmpty()) return@dbQuery existingToken

                        val newToken = java.util.UUID.randomUUID().toString()

                        newToken
                    } ?: return@post call.respond(HttpStatusCode.BadRequest, "Rezervarea nu e în stare CHECKED_IN")

                    call.respond(HttpStatusCode.OK, mapOf("token" to token))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/admin/boarding/scan") {
                if (!call.isAdmin()) return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Acces interzis")
                )
                try {
                    val token = call.request.queryParameters["token"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Token lipsă"))

                    val result = dbQuery {
                        val booking = BookingsTable.selectAll()
                            .where { BookingsTable.boardingToken eq token }
                            .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        when (booking[BookingsTable.status]) {
                            "BOARDED" -> "ALREADY_BOARDED"
                            "CHECKED_IN" -> {
                                BookingsTable.update({ BookingsTable.id eq booking[BookingsTable.id] }) {
                                    it[status] = "BOARDED"
                                }
                                "SUCCESS:${booking[BookingsTable.seatLabel]}"
                            }

                            else -> "NOT_CHECKED_IN"
                        }
                    }
                    when {
                        result.startsWith("SUCCESS") -> call.respond(
                            HttpStatusCode.OK,
                            mapOf("message" to "Îmbarcat — loc ${result.split(":")[1]}")
                        )

                        result == "ALREADY_BOARDED" -> call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "Pasager deja îmbarcat")
                        )

                        result == "NOT_FOUND" -> call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Boarding pass invalid")
                        )

                        else -> call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Pasagerul nu a făcut check-in")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/admin/users") {
                if (!call.isAdmin()) return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Acces interzis — necesită rol ADMIN")
                )
                try {
                    val users = dbQuery {
                        Users.selectAll().map { row ->
                            AdminUserDTO(
                                id = row[Users.id],
                                fullName = row[Users.fullName],
                                email = row[Users.email],
                                role = row[Users.role],
                                createdAt = row[Users.createdAt].toString()
                            )
                        }
                    }
                    call.respond(users)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }


            get("/admin/requests") {
                if (!call.isAdmin()) return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Acces interzis — necesită rol ADMIN")
                )
                try {
                    val requests = dbQuery {
                        (ModificationRequests innerJoin Users)
                            .join(
                                BookingsTable, JoinType.INNER,
                                onColumn = ModificationRequests.bookingId,
                                otherColumn = BookingsTable.id
                            )
                            .join(
                                Flights, JoinType.INNER,
                                onColumn = BookingsTable.flightId,
                                otherColumn = Flights.id
                            )
                            .selectAll()
                            .orderBy(ModificationRequests.createdAt to SortOrder.DESC)
                            .map { row ->
                                ModificationRequestDTO(
                                    id = row[ModificationRequests.id],
                                    userId = row[ModificationRequests.userId],
                                    userFullName = row[Users.fullName],
                                    userEmail = row[Users.email],
                                    bookingId = row[ModificationRequests.bookingId],
                                    origin = row[Flights.origin],
                                    destination = row[Flights.destination],
                                    requestType = row[ModificationRequests.requestType],
                                    description = row[ModificationRequests.description],
                                    status = row[ModificationRequests.status],
                                    adminNote = row[ModificationRequests.adminNote],
                                    createdAt = row[ModificationRequests.createdAt].toString()
                                )
                            }
                    }
                    call.respond(requests)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/requests") {
                try {
                    val req = call.receive<CreateModificationRequest>()
                    val newId = dbQuery {
                        ModificationRequests.insert {
                            it[userId] = req.userId
                            it[bookingId] = req.bookingId
                            it[requestType] = req.requestType
                            it[description] = req.description
                            it[status] = "PENDING"
                        } get ModificationRequests.id
                    }
                    call.respond(HttpStatusCode.Created, mapOf("id" to newId.toString(), "message" to "Request trimis"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/requests/user/{userId}") {
                try {
                    val userId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val requests = dbQuery {
                        (ModificationRequests innerJoin BookingsTable)
                            .join(
                                Flights, JoinType.INNER,
                                onColumn = BookingsTable.flightId,
                                otherColumn = Flights.id
                            )
                            .selectAll()
                            .where { ModificationRequests.userId eq userId }
                            .orderBy(ModificationRequests.createdAt to SortOrder.DESC)
                            .map { row ->
                                ModificationRequestDTO(
                                    id = row[ModificationRequests.id],
                                    userId = userId,
                                    userFullName = "",
                                    userEmail = "",
                                    bookingId = row[ModificationRequests.bookingId],
                                    origin = row[Flights.origin],
                                    destination = row[Flights.destination],
                                    requestType = row[ModificationRequests.requestType],
                                    description = row[ModificationRequests.description],
                                    status = row[ModificationRequests.status],
                                    adminNote = row[ModificationRequests.adminNote],
                                    createdAt = row[ModificationRequests.createdAt].toString()
                                )
                            }
                    }
                    call.respond(requests)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post("/admin/requests/{id}/resolve") {
                if (!call.isAdmin()) return@post call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Acces interzis — necesită rol ADMIN")
                )
                try {
                    val requestId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<ResolveRequest>()

                    dbQuery {
                        val req = ModificationRequests.selectAll()
                            .where { ModificationRequests.id eq requestId }
                            .singleOrNull() ?: return@dbQuery

                        ModificationRequests.update({ ModificationRequests.id eq requestId }) {
                            it[status] = if (body.approved) "APPROVED" else "REJECTED"
                            it[adminNote] = body.adminNote
                            it[resolvedAt] = LocalDateTime.now()
                        }
                        AdminLogsTable.insert {
                            it[adminId] = call.currentUserId()
                            it[bookingId] = req[ModificationRequests.bookingId]
                            it[actionType] = "RESOLVE_REQUEST"
                            it[newValue] = if (body.approved) "APPROVED" else "REJECTED"
                        }

                        if (body.approved && req[ModificationRequests.requestType] == "CANCEL") {
                            val bookingId = req[ModificationRequests.bookingId]
                            val current = BookingsTable.selectAll()
                                .where { BookingsTable.id eq bookingId }
                                .single()[BookingsTable.status]


                            if (BookingStateMachine.canCancel(current)) {
                                BookingsTable.update({ BookingsTable.id eq bookingId }) {
                                    it[status] = "CANCELLED"
                                }
                                val seatId = BookingsTable.selectAll()
                                    .where { BookingsTable.id eq bookingId }
                                    .singleOrNull()?.get(BookingsTable.seatId)
                                if (seatId != null) {
                                    SeatsTable.update({ SeatsTable.id eq seatId }) {
                                        it[isOccupied] = false
                                        it[userId] = null
                                    }
                                }
                            }
                        }
                    }

                    call.respond(HttpStatusCode.OK, mapOf("message" to "Request rezolvat"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }}}}