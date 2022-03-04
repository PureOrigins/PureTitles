package it.pureorigins.puretitles

import com.google.gson.Gson
import com.google.gson.JsonParseException
import it.pureorigins.common.*
import it.pureorigins.puretitles.json.ComponentSerializer
import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.bukkit.Bukkit.getPlayer
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

lateinit var plugin: PureTitles private set

class PureTitles : JavaPlugin() {
    fun getTitle(id: Int): Title? = transaction(database) { TitlesTable.getById(id) }
    fun getTitle(name: String): Pair<Int, Title>? = transaction(database) { TitlesTable.getByName(name) }
    fun createTitle(title: Title): Int = transaction(database) { TitlesTable.add(title) }
    fun deleteTitle(titleId: Int): Boolean = transaction(database) { TitlesTable.remove(titleId) }
    fun getAllNames(): Set<String> = transaction(database) { TitlesTable.getAllNames() }
    
    fun getPlayerTitles(playerUniqueId: UUID): Map<Int, Title> = transaction(database) { PlayerTitlesTable.getTitles(playerUniqueId) }
    fun getPlayerTitlesCount(playerUniqueId: UUID): Long = transaction(database) { PlayerTitlesTable.getCount(playerUniqueId) }
    fun getTitleOwnerCount(titleId: Int): Long = transaction(database) { PlayerTitlesTable.getPlayersCount(titleId) }
    fun addTitle(playerUniqueId: UUID, titleId: Int): Boolean = transaction(database) { PlayerTitlesTable.add(playerUniqueId, titleId) }
    fun removeTitle(playerUniqueId: UUID, titleId: Int): Boolean = transaction(database) { PlayerTitlesTable.remove(playerUniqueId, titleId) }
    
    fun getPlayersCount(): Long = transaction(database) { PlayersTable.count() }
    fun getCurrentTitle(playerUniqueId: UUID): Pair<Int, Title>? = transaction(database) { PlayersTable.getCurrentTitle(playerUniqueId) }
    fun setCurrentTitle(playerUniqueId: UUID, titleId: Int?): Boolean = transaction(database) { PlayersTable.setCurrentTitle(playerUniqueId, titleId) }
    
    fun giveTitle(playerUniqueId: UUID, titleId: Int): Boolean {
        return addTitle(playerUniqueId, titleId).also {
            val text = giveTitleMessage?.templateText("title" to getTitle(titleId))
            if (it) {
                val player = getPlayer(playerUniqueId)
                if (player != null) {
                    player.sendNullableMessage(text)
                } else if (text != null) {
                    transaction(database) {
                        NotificationsTable.add(playerUniqueId, text)
                    }
                }
            }
        }
    }
    
    private val gson by lazy { unsafeGetStaticField(Component.Serializer::class.java.declaredFields.first { it.type == Gson::class.java }) as Gson }
    fun getStyledMinecraftText(title: Title): MutableText {
        val style = try {
            val customJsonStyle = titleStyle?.templateJson("title" to title)
            if (customJsonStyle != null) {
                gson.fromJson(customJsonStyle, Style::class.java)
            } else {
                Style.EMPTY
            }
        } catch (e: JsonParseException) {
            Style.EMPTY
        }
        return (title.text as MutableComponent).withStyle { // possible without a copy because it's only used for this
            it.applyTo(style)
        }
    }
    
    private lateinit var database: Database
    
    private var giveTitleMessage: String? = null
    private var titleStyle: String? = null
    
    override fun onLoad() {
        plugin = this
        ComponentSerializer.register()
    }
    
    override fun onEnable() {
        val (db, titleStyle, titles) = json.readFileAs(file("titles.json"), Config())
        require(db.url.isNotEmpty()) { "database url should not be empty" }
        this.titleStyle = titleStyle
        this.giveTitleMessage = titles.add.message
        database = Database.connect(db.url, user = db.username, password = db.password)
        transaction(database) {
            createMissingTablesAndColumns(PlayersTable, TitlesTable, PlayerTitlesTable, NotificationsTable)
        }
        registerEvents(Notifications(database))
        registerCommand(TitlesCommand(this, titles).command)
    }
    
    @Serializable
    data class Config(
        val database: Database = Database(),
        val titleStyle: String? = "{\"hoverEvent\": {\"action\": \"show_text\", \"value\": \"set title\"}, \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/titles set \${title.name}\"}, \"insertion\": \"\${title.name}\"}",
        val titles: TitlesCommand.Config = TitlesCommand.Config()
    ) {
        @Serializable
        data class Database(
            val url: String = "",
            val username: String = "",
            val password: String = ""
        )
    }
}
