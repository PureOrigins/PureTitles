package it.pureorigins.puretitles

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.network.chat.BaseComponent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.ContextAwareComponent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.entity.Entity
import org.apache.logging.log4j.LogManager
import java.util.*

data class TitleComponent(val titleName: String) : BaseComponent(), ContextAwareComponent {
    override fun resolve(source: CommandSourceStack?, sender: Entity?, depth: Int): MutableComponent {
        val titleWithId = plugin.getTitle(titleName)
        return if (titleWithId != null) {
            val title = titleWithId.second
            plugin.getStyledMinecraftText(title)
        } else {
            TextComponent("")
        }
    }
    
    override fun getContents() = titleName
    override fun plainCopy() = TitleComponent(titleName)
    override fun toString() = "TitleComponent{titleName='$titleName', siblings=$siblings, style=$style}"
    
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is TitleComponent -> false
        else -> titleName == other.titleName && super.equals(other)
    }
    
    override fun hashCode() = Objects.hash(titleName, super.hashCode())
}

data class TitleSelectorComponent(val pattern: String, val separator: Optional<Component> = Optional.empty()) : BaseComponent(), ContextAwareComponent {
    private val logger = LogManager.getLogger()
    private val selector: EntitySelector? = try {
        EntitySelectorParser(StringReader(pattern)).parse()
    } catch (e: CommandSyntaxException) {
        logger.warn("Invalid selector component: {}: {}", pattern, e.message)
        null
    }
    
    override fun resolve(source: CommandSourceStack?, sender: Entity?, depth: Int): MutableComponent {
        if (source == null || selector == null) return TextComponent("")
        val optional = ComponentUtils.updateForEntity(source, this.separator, sender, depth)
        return ComponentUtils.formatList(selector.findPlayers(source), optional) { player ->
            val (_, title) = plugin.getCurrentTitle(player.uuid) ?: return@formatList player.getDisplayName()
            val text = TextComponent("")
            text.append(plugin.getStyledMinecraftText(title))
            text.append(TextComponent(" "))
            text.append(player.getDisplayName())
        }
    }
    
    override fun getContents() = pattern
    override fun plainCopy() = TitleSelectorComponent(pattern, separator)
    override fun toString() = "TitleSelectorComponent{pattern='$pattern', separator='$separator', siblings=$siblings, style=$style}"
    
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is TitleSelectorComponent -> false
        else -> pattern == other.pattern && separator == other.separator && super.equals(other)
    }
    
    override fun hashCode() = Objects.hash(pattern, separator, super.hashCode())
}