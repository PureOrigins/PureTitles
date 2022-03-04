package it.pureorigins.puretitles

import it.pureorigins.common.sendNullableMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

class Notifications(private val database: Database) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val news = transaction(database) { NotificationsTable.getAndRemove(player.uniqueId) }
        news.forEach {
            player.sendNullableMessage(it)
        }
    }
}