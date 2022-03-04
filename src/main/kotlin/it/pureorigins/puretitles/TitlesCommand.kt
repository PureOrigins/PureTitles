package it.pureorigins.puretitles

import com.mojang.brigadier.arguments.StringArgumentType.*
import it.pureorigins.common.*
import net.minecraft.commands.arguments.EntityArgument.getPlayers
import net.minecraft.commands.arguments.EntityArgument.players
import kotlinx.serialization.Serializable
import net.minecraft.commands.arguments.ComponentArgument.getComponent
import net.minecraft.commands.arguments.ComponentArgument.textComponent

class TitlesCommand(private val plugin: PureTitles, private val config: Config) {
    val command get() = literal(config.commandName) {
        requiresPermission("puretitles.titles")
        success { source.sendNullableMessage(config.commandUsage?.templateText()) }
        then(setCommand)
        then(infoCommand)
        then(addCommand)
        then(removeCommand)
        then(createCommand)
        then(deleteCommand)
    }
    
    val setCommand get() = literal(config.set.commandName) {
        requiresPermission("puretitles.titles.set")
        success { source.sendNullableMessage(config.set.commandUsage?.templateText()) }
        then(argument("title", greedyString()) {
            suggestions {
                plugin.getPlayerTitles(source.player.uniqueId).map { (_, title) -> title.name } + config.nullTitleName
            }
            success {
                val player = source.player
                val titleName = getString(this, "title")
                if (titleName == config.nullTitleName) {
                    plugin.setCurrentTitle(player.uniqueId, null)
                    return@success source.sendNullableMessage(config.set.success?.templateText("title" to null))
                }
                val (id, title) = plugin.getTitle(titleName) ?: return@success source.sendNullableMessage(config.set.titleNotFound?.templateText())
                val playerTitles = plugin.getPlayerTitles(player.uniqueId)
                if (id !in playerTitles) return@success source.sendNullableMessage(config.set.titleNotOwned?.templateText("title" to title))
                plugin.setCurrentTitle(player.uniqueId, id)
                source.sendNullableMessage(config.set.success?.templateText("title" to title))
            }
        })
    }
    
    val infoCommand get() = literal(config.info.commandName) {
        requiresPermission("puretitles.titles.info")
        success {
            val player = source.player
            val titles = plugin.getPlayerTitles(player.uniqueId).values
            val currentTitle = plugin.getCurrentTitle(player.uniqueId)?.second
            source.sendNullableMessage(config.info.message?.templateText("titles" to titles, "currentTitle" to currentTitle))
        }
    }
    
    val addCommand get() = literal(config.add.commandName) {
        requiresPermission("puretitles.titles.add")
        success { source.sendNullableMessage(config.add.commandUsage?.templateText()) }
        then(argument("targets", players()) {
            then(argument("title", greedyString()) {
                suggestions {
                    plugin.getAllNames()
                }
                success {
                    val players = getPlayers(this, "targets")
                    val titleName = getString(this, "title")
                    val (id, title) = plugin.getTitle(titleName) ?: return@success source.sendNullableMessage(config.add.titleNotFound?.templateText())
                    val success = players.filter {
                        plugin.giveTitle(it.uuid, id)
                    }
                    if (success.isEmpty()) source.sendNullableMessage(config.add.titleAlreadyOwned?.templateText("title" to title))
                    else source.sendNullableMessage(config.add.success?.templateText("title" to title, "players" to players))
                }
            })
        })
    }
    
    val removeCommand get() = literal(config.remove.commandName) {
        requiresPermission("puretitles.titles.remove")
        success { source.sendNullableMessage(config.remove.commandUsage?.templateText()) }
        then(argument("targets", players()) {
            then(argument("title", greedyString()) {
                suggestions {
                    plugin.getAllNames()
                }
                success {
                    val players = getPlayers(this, "targets")
                    val titleName = getString(this, "title")
                    val (id, title) = plugin.getTitle(titleName) ?: return@success source.sendNullableMessage(config.remove.titleNotFound?.templateText())
                    val success = players.filter {
                        plugin.removeTitle(it.uuid, id)
                    }
                    if (success.isEmpty()) source.sendNullableMessage(config.remove.titleNotOwned?.templateText("title" to title))
                    else source.sendNullableMessage(config.remove.success?.templateText("title" to title, "players" to players))
                }
            })
        })
    }
    
    val createCommand get() = literal(config.create.commandName) {
        requiresPermission("puretitles.titles.create")
        success { source.sendNullableMessage(config.create.commandUsage?.templateText()) }
        then(argument("name", string()) {
            success { source.sendNullableMessage(config.create.commandUsage?.templateText()) }
            then(argument("text", textComponent()) {
                success {
                    val titleName = getString(this, "name")
                    val titleText = getComponent(this, "text")
                    val oldTitle = plugin.getTitle(titleName)
                    if (oldTitle != null) {
                        source.sendNullableMessage(config.create.titleAlreadyExists?.templateText("title" to oldTitle.second))
                    } else {
                        val title = Title(titleName, titleText)
                        plugin.createTitle(title)
                        source.sendNullableMessage(config.create.success?.templateText("title" to title))
                    }
                }
            })
        })
    }
    
    val deleteCommand get() = literal(config.delete.commandName) {
        requiresPermission("puretitles.titles.delete")
        success { source.sendNullableMessage(config.delete.commandUsage?.templateText()) }
        then(argument("title", string()) {
            suggestions {
                plugin.getAllNames()
            }
            success {
                val titleName = getString(this, "title")
                val (id, title) = plugin.getTitle(titleName)
                    ?: return@success source.sendNullableMessage(config.delete.titleNotFound?.templateText("title" to titleName))
                plugin.deleteTitle(id)
                source.sendNullableMessage(config.delete.success?.templateText("title" to title))
            }
        })
    }
    
    @Serializable
    data class Config(
        val commandName: String = "titles",
        val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/titles <set | info>\", \"color\": \"gray\"}]",
        val nullTitleName: String = "none",
        val set: Set = Set(),
        val info: Info = Info(),
        val add: Add = Add(),
        val remove: Remove = Remove(),
        val create: Create = Create(),
        val delete: Delete = Delete()
    ) {
        @Serializable
        data class Set(
            val commandName: String = "set",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/titles set <title | none>\", \"color\": \"gray\"}]",
            val titleNotFound: String? = "{\"text\": \"Title not found.\", \"color\": \"dark_gray\"}",
            val titleNotOwned: String? = "{\"text\": \"Title not owned.\", \"color\": \"dark_gray\"}",
            val success: String? = "{\"text\": \"Title set.\", \"color\": \"gray\"}"
        )
        
        @Serializable
        data class Info(
            val commandName: String = "info",
            val message: String? = "[{\"text\": \"Current title: \", \"color\": \"gray\"}, <#if currentTitle??>{\"title\": \"\${currentTitle.name}\"}<#else>{\"text\": \"none\", \"color\": \"gray\"}</#if>, {\"text\": \"\\n\"}, {\"text\": \"\${titles?size} Owned title<#if titles?size != 0>s</#if>: \", \"color\": \"gray\"}, <#list titles as title>{\"title\": \"\${title.name}\"}<#sep>,{\"text\": \", \", \"color\": \"gray\"},</#list>]"
        )
        
        @Serializable
        data class Add(
            val commandName: String = "add",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/titles add <targets> <title>\", \"color\": \"gray\"}]",
            val titleNotFound: String? = "{\"text\": \"Title not found.\", \"color\": \"dark_gray\"}",
            val titleAlreadyOwned: String? = "{\"text\": \"Title already owned.\", \"color\": \"dark_gray\"}",
            val message: String? = "[{\"text\": \"You got a new title: \", \"color\": \"gray\"}, {\"title\": \"\${title.name}\"}, {\"text\": \".\", \"color\": \"gray\"}]",
            val success: String? = "{\"text\": \"\${players?size} title<#if players?size != 0>s</#if> added.\", \"color\": \"gray\"}"
        )
        
        @Serializable
        data class Remove(
            val commandName: String = "remove",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/titles remove <targets> <title>\", \"color\": \"gray\"}]",
            val titleNotFound: String? = "{\"text\": \"Title not found.\", \"color\": \"dark_gray\"}",
            val titleNotOwned: String? = "{\"text\": \"Title not owned.\", \"color\": \"dark_gray\"}",
            val success: String? = "{\"text\": \"\${players?size} title<#if players?size != 0>s</#if> removed.\", \"color\": \"gray\"}"
        )
        
        @Serializable
        data class Create(
            val commandName: String = "create",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/titles create <name> <text>\", \"color\": \"gray\"}]",
            val titleAlreadyExists: String? = "[{\"text\": \"Title already exists. (\", \"color\": \"dark_gray\"}, {\"title\": \"\${title.name}\"}, {\"text\": \")\", \"color\": \"dark_gray\"}]",
            val success: String? = "{\"text\": \"Title created.\", \"color\": \"gray\"}"
        )
        
        @Serializable
        data class Delete(
            val commandName: String = "delete",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/titles delete <title name>\", \"color\": \"gray\"}]",
            val titleNotFound: String? = "{\"text\": \"Title not found.\", \"color\": \"dark_gray\"}",
            val success: String? = "{\"text\": \"Title deleted.\", \"color\": \"gray\"}"
        )
    }
}