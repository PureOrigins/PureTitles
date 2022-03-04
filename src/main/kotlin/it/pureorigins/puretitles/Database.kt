package it.pureorigins.puretitles

import it.pureorigins.common.MutableText
import it.pureorigins.common.Text
import it.pureorigins.common.textFromJson
import it.pureorigins.common.toJson
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.*
import kotlin.collections.HashSet

object TitlesTable : Table("titles") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", length = 50).uniqueIndex()
    val text = text("prefix")
    val description = text("description").nullable()
    
    override val primaryKey = PrimaryKey(id)
    
    fun getAllNames(): Set<String> = selectAll().mapTo(HashSet()) { it[name] }
    fun getById(id: Int): Title? = select { TitlesTable.id eq id }.singleOrNull()?.toTitle()?.second
    fun getByName(name: String): Pair<Int, Title>? = select { TitlesTable.name eq name }.singleOrNull()?.toTitle()
    fun add(title: Title): Int = insert {
        it[name] = title.name
        it[text] = title.text.toJson()
        it[description] = title.description?.toJson()
    } get id
    fun remove(id: Int): Boolean = deleteWhere { TitlesTable.id eq id } > 0
    fun update(id: Int, title: Title): Boolean = update({ TitlesTable.id eq id }) {
        it[name] = title.name
        it[text] = title.text.toJson()
        it[description] = title.description?.toJson()
    } > 0
}

object PlayerTitlesTable : Table("player_titles") {
    val playerUniqueId = uuid("player_uuid") references PlayersTable.uniqueId
    val titleId = integer("title_id") references TitlesTable.id
    
    override val primaryKey = PrimaryKey(playerUniqueId, titleId)
    
    fun getTitles(playerUniqueId: UUID): Map<Int, Title> = innerJoin(TitlesTable).select { PlayerTitlesTable.playerUniqueId eq playerUniqueId }.associate { it.toTitle() }
    fun getTitleNames(playerUniqueId: UUID): Set<String> = innerJoin(TitlesTable).select { PlayerTitlesTable.playerUniqueId eq playerUniqueId }.mapTo(HashSet()) { it[TitlesTable.name] }
    fun getCount(playerUniqueId: UUID): Long = select { PlayerTitlesTable.playerUniqueId eq playerUniqueId }.count()
    fun getPlayersCount(titleId: Int): Long = select { PlayerTitlesTable.titleId eq titleId }.count()
    fun add(playerUniqueId: UUID, titleId: Int): Boolean = insertIgnore {
        it[PlayerTitlesTable.playerUniqueId] = playerUniqueId
        it[PlayerTitlesTable.titleId] = titleId
    }.insertedCount > 0
    fun remove(playerUniqueId: UUID, titleId: Int): Boolean = deleteWhere {
        (PlayerTitlesTable.playerUniqueId eq playerUniqueId) and (PlayerTitlesTable.titleId eq titleId)
    } > 0
}

object PlayersTable : Table("players") {
    val uniqueId = uuid("uuid")
    val currentTitleId = (integer("current_title_id") references TitlesTable.id).nullable()
    
    override val primaryKey = PrimaryKey(uniqueId)
    
    fun count(): Long = selectAll().count()
    fun getCurrentTitle(uniqueId: UUID): Pair<Int, Title>? = innerJoin(TitlesTable).select { PlayersTable.uniqueId eq uniqueId }.singleOrNull()?.toTitle()
    fun setCurrentTitle(uniqueId: UUID, titleId: Int?): Boolean {
        insertIgnore {
            it[PlayersTable.uniqueId] = uniqueId
            it[currentTitleId] = null
        }
        return update({ PlayersTable.uniqueId eq uniqueId }) {
            it[currentTitleId] = titleId
        } > 0
    }
}

object NotificationsTable : Table("titles_notifications") {
    val playerUniqueId = uuid("player_uuid")
    val text = text("text")
    val date = long("date")
    val expirationDate = long("expiration_date").nullable()
    
    fun add(playerUniqueId: UUID, text: Text, expirationTime: TemporalAmount? = null) {
        insert {
            it[NotificationsTable.playerUniqueId] = playerUniqueId
            it[NotificationsTable.text] = text.toJson()
            val now = Instant.now()
            it[date] = now.toEpochMilli()
            it[expirationDate] = now?.plus(expirationTime)?.toEpochMilli()
        }
    }
    
    fun get(playerUniqueId: UUID): List<MutableText> = select { (NotificationsTable.playerUniqueId eq playerUniqueId) and (expirationDate greater Instant.now().toEpochMilli()) }.orderBy(date).map { textFromJson(it[text]) }
    fun remove(playerUniqueId: UUID): Int = deleteWhere { NotificationsTable.playerUniqueId eq playerUniqueId }
    fun getAndRemove(playerUniqueId: UUID): List<MutableText> = get(playerUniqueId).also { remove(playerUniqueId) }
}

private fun ResultRow.toTitle() = get(TitlesTable.id) to Title(
    name = get(TitlesTable.name),
    text = textFromJson(get(TitlesTable.text)),
    description = get(TitlesTable.description)?.let { textFromJson(it) }
)