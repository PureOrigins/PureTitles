package it.pureorigins.puretitles.json

import com.google.gson.*
import it.pureorigins.common.unsafeGetStaticField
import it.pureorigins.common.unsafeSetStaticField
import it.pureorigins.puretitles.TitleComponent
import it.pureorigins.puretitles.TitleSelectorComponent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.GsonHelper
import java.lang.reflect.Type
import java.util.*


object ComponentSerializer : Component.Serializer() {
    override fun deserialize(element: JsonElement, type: Type, context: JsonDeserializationContext): MutableComponent {
        if (element.isJsonObject) {
            val json = element.asJsonObject
            var text: MutableComponent? = null
            if (json.has("title")) {
                val titleName = GsonHelper.getAsString(json, "title")
                text = TitleComponent(titleName)
            }
            if (json.has("title_selector")) {
                val pattern = GsonHelper.getAsString(json, "title_selector")
                val optional = if (json.has("separator")) Optional.of(
                    deserialize(
                        json["separator"],
                        type,
                        context
                    ) as Component
                ) else Optional.empty()
                text = TitleSelectorComponent(pattern, optional)
            }
            if (text != null) {
                if (json.has("extra")) {
                    val jsonArray = GsonHelper.getAsJsonArray(json, "extra")
                    if (jsonArray.size() <= 0) {
                        throw JsonParseException("Unexpected empty array of components")
                    }
                    jsonArray.forEach {
                        text.append(context.deserialize(it, type) as Component)
                    }
                }
                text.style = context.deserialize(json, Style::class.java)
                return text
            }
        }
        return super.deserialize(element, type, context)
    }
    
    override fun serialize(component: Component, type: Type, context: JsonSerializationContext): JsonElement {
        val json = super.serialize(component, type, context)
        if (component is TitleComponent) {
            json as JsonObject
            json.addProperty("title", component.titleName)
        } else if (component is TitleSelectorComponent) {
            json as JsonObject
            json.addProperty("title_selector", component.pattern)
            component.separator.ifPresent {
                json.add("separator", serialize(it, it.javaClass, context))
            }
        }
        return json
    }
    
    fun register() {
        val field = Component.Serializer::class.java.declaredFields.first { it.type == Gson::class.java }
        val oldGson = unsafeGetStaticField(field) as Gson
        val builder = oldGson.newBuilder()
        builder.registerTypeHierarchyAdapter(Component::class.java, ComponentSerializer)
        unsafeSetStaticField(field, builder.create())
    }
}