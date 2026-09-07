package com.rimor.minitools

/** What a gesture can be set to do. */
enum class Action(val label: String) {
    NONE("Nothing"),
    LAUNCHER("Launcher"),
    RECENTS("Recents"),
    ROTATE("Rotate"),
    ;

    /** Rotation is absent from the norotate build, not merely switched off. */
    val available: Boolean get() = this != ROTATE || BuildConfig.HAS_ROTATION

    companion object {
        fun from(name: String?): Action =
            entries.firstOrNull { it.name == name }?.takeIf { it.available } ?: NONE

        /** The features a user would expect to be able to reach. NONE is not one of them. */
        val features: List<Action> = entries.filter { it != NONE && it.available }

        /** Everything a gesture can be set to, in the order the chips are drawn. */
        val assignable: List<Action> = entries.filter { it.available }
    }
}
