/*
 *     Pluggable: Pluggable.kt
 *     Copyright (C) 2025 mtctx
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package mtctx.pluggable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import mtctx.utilities.Outcome
import mtctx.utilities.failure
import mtctx.utilities.runCatchingOutcomeBlock
import mtctx.utilities.success
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

class Pluggable<HostClass, PluginClass, ConfigClass : Config>(
    private val pluginsDir: Path,
    private val pluginConfigSerializer: KSerializer<ConfigClass>,
    private val pluginParentClass: Class<PluginClass>,
    hostClass: Class<HostClass>? = null,
    private val hostClassLoader: ClassLoader? = hostClass?.classLoader,
) {
    init {
        if (pluginsDir.notExists()) pluginsDir.createDirectories()
    }

    fun load(
        pluginFile: File,
        pluginConfigName: String = "plugin.json",
        fileFormat: StringFormat = Json
    ): Outcome<LoadedPlugin<PluginClass, ConfigClass>> {
        if (!pluginFile.exists() || !pluginFile.isFile) {
            return failure("Plugin file not found or is not a file: ${pluginFile.name}")
        }

        return runCatchingOutcomeBlock<LoadedPlugin<PluginClass, ConfigClass>> {
            val loader = URLClassLoader(arrayOf(pluginFile.toURI().toURL()), hostClassLoader)

            val pluginJsonURL = loader.getResource(pluginConfigName)
                ?: return@runCatchingOutcomeBlock failure("Plugin configuration file '$pluginConfigName' not found inside JAR: ${pluginFile.name}")

            val configText = try {
                pluginJsonURL.readText()
            } catch (e: IOException) {
                return@runCatchingOutcomeBlock failure("Failed to read config file from JAR: ${pluginFile.name}", e)
            }

            val pluginConfig = try {
                fileFormat.decodeFromString(pluginConfigSerializer, configText)
            } catch (e: SerializationException) {
                return@runCatchingOutcomeBlock failure("Failed to deserialize plugin config: ${pluginFile.name}", e)
            }

            val pluginClassName = pluginConfig.mainClass
            val pluginClass = try {
                loader.loadClass(pluginClassName)
            } catch (e: ClassNotFoundException) {
                return@runCatchingOutcomeBlock failure(
                    "Plugin main class '$pluginClassName' not found in JAR: ${pluginFile.name}",
                    e
                )
            }

            if (!pluginParentClass::class.java.isAssignableFrom(pluginClass)) return@runCatchingOutcomeBlock failure(
                "Plugin class '$pluginClassName' does not implement the expected Parent: ${pluginParentClass::class.simpleName}"
            )

            val pluginInstance = try {
                pluginClass.getDeclaredConstructor().newInstance() as PluginClass
            } catch (e: ClassCastException) {
                return@runCatchingOutcomeBlock failure(
                    "Plugin class '$pluginClassName' does not implement the expected interface 'PluginClass' (Type Mismatch).",
                    e
                )
            } catch (e: Exception) {
                return@runCatchingOutcomeBlock failure(
                    "Failed to instantiate plugin class '$pluginClassName'. Check for parameterless constructor.",
                    e
                )
            }

            success(LoadedPlugin(pluginInstance, loader, pluginConfig))
        }
    }

    fun unload(loadedPlugin: LoadedPlugin<PluginClass, ConfigClass>, hintGC: Boolean = true): Outcome<Boolean> =
        runCatchingOutcomeBlock<Boolean> {
            try {
                loadedPlugin.urlClassLoader.close()
            } catch (e: Exception) {
                return@runCatchingOutcomeBlock failure(
                    "Could not close URLClassLoader for plugin: ${loadedPlugin.plugin!!::class.simpleName}",
                    e
                )
            }
            if (hintGC) System.gc()
            return@runCatchingOutcomeBlock success()
        }

    fun loadAll(
        pluginConfigName: String = "plugin.json",
        fileFormat: StringFormat = Json
    ): Map<String, Outcome<LoadedPlugin<PluginClass, ConfigClass>>> {
        val outcomes = mutableMapOf<String, Outcome<LoadedPlugin<PluginClass, ConfigClass>>>()
        (pluginsDir.toFile().listFiles { _, name -> name.endsWith(".jar") } ?: arrayOf<File>()).forEach { jar ->
            val outcome = load(jar, pluginConfigName, fileFormat)
            outcomes[jar.name] = outcome
        }
        return outcomes
    }

    fun unloadAll(
        loadedPlugins: List<LoadedPlugin<PluginClass, ConfigClass>>,
        hintGC: Boolean = true
    ): Map<String, Outcome<Boolean>> {
        val outcomes = mutableMapOf<String, Outcome<Boolean>>()
        loadedPlugins.forEach { loadedPlugin ->
            val outcome = unload(loadedPlugin, false)
            outcomes[loadedPlugin.config.mainClass] = outcome
        }
        if (hintGC) System.gc()
        return outcomes
    }
}