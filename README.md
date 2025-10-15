# Pluggable – A simple Kotlin Plugin Loader

-----

## What is Pluggable?

**Pluggable** is a lightweight, type-safe, and dependency-aware Kotlin utility designed for **loading, managing, and
unloading** external code (plugins) packaged as JAR files.

It simplifies the process of creating modular applications by handling `URLClassLoader` isolation, configuration
deserialization (using **Kotlinx Serialization**), and safe plugin instantiation, all while providing robust **error
handling** via the `Outcome` utility class.

-----

## Features

* **Type-Safe Loading**: Uses generics (`PluginClass`, `ConfigClass`) to ensure the loaded plugin and its configuration
  conform to expected types.
* **Isolated Class Loading**: Each plugin is loaded with its own `URLClassLoader` to minimize dependency conflicts and
  allow for safe unloading.
* **Automatic Configuration**: Reads a specified configuration file (default `plugin.json`) from within the plugin JAR
  and deserializes it using **Kotlinx Serialization**.
* **Configuration Interface**: Enforces a `Config` interface which requires a `mainClass` property for easy
  instantiation.
* **Robust Error Handling**: Utilizes a custom `Outcome<T>` sealed interface for clear and concise success/failure
  reporting, including detailed messages and stack traces.
* **Safe Unloading**: Provides an `unload` function that attempts to close the `URLClassLoader` and optionally hints the
  JVM for garbage collection.
* **Batch Operations**: Includes `loadAll` and `unloadAll` for managing entire plugin directories or lists of loaded
  plugins.

-----

## Core Components

| Component          | Description                                                                                                                                           |
|:-------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`Config`**       | A minimal interface that all plugin configuration classes must implement, primarily requiring the `mainClass` string.                                 |
| **`LoadedPlugin`** | A data class holding the successfully loaded plugin instance, its dedicated `URLClassLoader`, and its deserialized configuration.                     |
| **`Pluggable`**    | The main class for all plugin loading logic. It manages the plugin directory, file reading, class loading, and instantiation.                         |
| **`Outcome`**      | A sealed interface for explicit error handling, representing either a `Success<T>` with a value or a `Failure` with a message and optional throwable. |

-----

## Installation

Pluggable is available via **Maven Central**.

### Dependencies

This library requires **Kotlinx Serialization** for configuration handling and the provided [
`Outcome`](https://github.com/mtctx/Utilities/blob/main/src/main/kotlin/mtctx/utilities/Outcome.kt) utility.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Pluggable library
    implementation("dev.mtctx.library:pluggable:1.0.0")

    // Required for configuration serialization, can also use any other serialization library as long as it supports Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

-----

## Example Usage

### 1\. Define Plugin Interfaces and Configuration

```kotlin
// In your Host Application: The interface your plugins must implement
interface IPlugin {
    fun onEnable()
    fun onDisable()
}

// In your Host Application: The config structure
@Serializable // Requires kotlinx.serialization
data class MyPluginConfig(
    override val mainClass: String,
    val version: String
) : Config // mtctx.pluggable.Config
```

### 2\. Instantiate the `Pluggable` Loader

```kotlin
import mtctx.pluggable.Pluggable
import java.nio.file.Paths

val pluginsDir = Paths.get("./plugins")
val loader = Pluggable(
    pluginsDir = pluginsDir,
    pluginConfigSerializer = MyPluginConfig.serializer(), // From kotlinx.serialization
    pluginParentClass = IPlugin::class.java,
    // -------------
    hostClass = MainApplication::class.java, // Optional: provides host classloader, default is null
    // OR
    hostClassLoader = MainApplication::class.java.classLoader, // Optional: provides host classloader, default is null
)
```

### 3\. Load All Plugins

```kotlin
import mtctx.utilities.Outcome.Success

val outcomes = loader.loadAll()
val loadedPlugins = outcomes.values
    .filterIsInstance<Success<LoadedPlugin<IPlugin, MyPluginConfig>>>()
    .map { it.value }

// Run the enable function on all successful plugins
loadedPlugins.forEach {
    it.plugin.onEnable() // This comes from your IPlugin interface
    println("Loaded and enabled plugin: ${it.config.mainClass} (Version: ${it.config.version})")
}

// Handle failures
outcomes.forEach { (fileName, outcome) ->
    if (outcome.failed) {
        println("Failed to load $fileName: ${outcome.fold({ "" }, { msg, _ -> msg })}")
    }
}
```

### 4\. Unload Plugins

```kotlin
val unloadOutcomes = loader.unloadAll(loadedPlugins)

// Check for any unload failures
unloadOutcomes.forEach { (pluginName, outcome) ->
    if (outcome.failed) {
        val failedOutcome = outcome as Outcome.Failure
        println("Failed to unload $pluginName: ${outcome.message}") // There is also outcome.throwable (nullable) and outcome.outcome (nullable, if another outcome was provided for the failure)
    }
}
```

-----

## License

Pluggable is free software under the [**GNU GPL v3**](LICENSE). You can use it, modify it, and distribute it.