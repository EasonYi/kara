package kara

import java.util.HashMap

/**
 * Base class for config classes that use the Kara JSON config system.
 * Values are stored in a flat key/value map, and can be accessed like an array.
 */
open class Config() {
    val data = HashMap<String, String>()

    /**
     * Gets the value for the given key.
     * Will raise an exception if the value isn't present. Try calling contains(key) first if you're unsure.
     */
    fun get(name : String) : String {
        if (data.containsKey(name))
            return data[name]!!
        throw ConfigMissingException("Could not find config value for key $name")
    }

    fun tryKey(name: String): String? {
        return data[name]
    }

    /** Sets a value for the given key. */
    fun set(name : String, value : String) {
        data[name] = value
    }

    /** Returns true if the config contains a value for the given key. */
    fun contains(name : String) : Boolean {
        return data.containsKey(name)
    }

    /** Prints the entire config to a nicely formatted string. */
    fun toString() : String {
        val builder = StringBuilder()
        for (name in data.keySet()) {
            builder.append("$name: ${data[name]}\n")
        }
        return builder.toString()
    }
}

public class ConfigMissingException(desc: String) : RuntimeException(desc)
