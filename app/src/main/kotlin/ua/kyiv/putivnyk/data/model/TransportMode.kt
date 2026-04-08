package ua.kyiv.putivnyk.data.model

enum class TransportMode(val key: String) {
    WALKING("walking"),
    DRIVING("driving");

    companion object {
        fun fromKey(key: String): TransportMode =
            entries.firstOrNull { it.key == key } ?: WALKING
    }
}
