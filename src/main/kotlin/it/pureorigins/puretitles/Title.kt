package it.pureorigins.puretitles

import it.pureorigins.common.Text

data class Title(
    val name: String,
    val text: Text,
    val description: Text? = null
)