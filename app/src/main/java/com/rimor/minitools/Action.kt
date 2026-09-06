package com.rimor.minitools

/** What a gesture can be set to do. */
enum class Action(val label: String) {
    NONE("Nothing"),
    LAUNCHER("Launcher"),
    RECENTS("Recents"),
    ROTATE("Rotate"),
    ;

    companion object {
        fun from(name: String?): Action = entries.firstOrNull { it.name == name } ?: NONE

        /** The features a user would expect to be able to reach. NONE is not one of them. */
        val features: List<Action> = entries.filter { it != NONE }
    }
}
