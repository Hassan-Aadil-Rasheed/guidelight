package com.guidelight.app.vision

import kotlinx.serialization.json.*

/** A prose caption is never spoken as walking advice. Only supported labels survive. */
object CloudflareScenePolicy {
    fun parse(content: String): AiScene {
        val start=content.indexOf('{');val end=content.lastIndexOf('}')
        require(start>=0 && end>=start)
        val value=Json.parseToJsonElement(content.substring(start,end+1)).jsonObject
        require(value.keys.containsAll(listOf("scene_type","objects","reference_match")))
        return LocalAiPolicy.parseScene(content)
    }
    fun grounded(normalized: String,caption: String): AiScene {
        val scene=parse(normalized)
        val positive=caption.lowercase(java.util.Locale.US).split(Regex("[.!?;\\n]"))
            .filterNot {Regex("\\b(no|not|without|none|cannot|can t|can’t|isn t|aren t|walk|turn|cross|proceed|step)\\b").containsMatchIn(it.replace("'"," "))}
        fun contains(label: String): Boolean {
            val aliases=when(label){"cupboard"->listOf("cupboard","wardrobe","cabinet");"sofa"->listOf("sofa","couch");
                "fridge"->listOf("fridge","refrigerator");"person"->listOf("person","people","man","woman","men","women");
                "bicycle"->listOf("bicycle","bike");"shelf"->listOf("shelf","shelves");else->listOf(label)}
            return positive.any {sentence ->aliases.any {Regex("\\b"+Regex.escape(it)+"s?\\b").containsMatchIn(sentence)}}
        }
        return scene.copy(scene_type=scene.scene_type.takeIf(::contains) ?: "unknown",
            objects=scene.objects.filter {contains(it.name)}.map {it.copy(side="unknown")},
            reference_match=scene.reference_match?.takeIf {id ->positive.any {Regex("\\b"+Regex.escape(id)+"\\b",RegexOption.IGNORE_CASE).containsMatchIn(it)}})
    }
}
