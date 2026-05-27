package com.androidclaw.app.skills

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Skills are reusable multi-step automations (like OpenClaw's SKILL.md format).
 * Users can create, edit, import/export, and trigger skills by name or slash command.
 *
 * Skill format (stored as JSON):
 * {
 *   "id": "morning-routine",
 *   "name": "Morning Routine",
 *   "description": "Start the day right",
 *   "trigger": "/morning",
 *   "schedule": "daily 07:00",
 *   "steps": [
 *     {"action": "set_brightness", "params": {"level": "80"}},
 *     {"action": "play_music", "params": {"query": "morning jazz"}},
 *     {"action": "read_aloud", "params": {"text": "Good morning! Here's your schedule for today."}}
 *   ],
 *   "enabled": true,
 *   "priority": 0
 * }
 */
class SkillManager(private val context: Context) {

    companion object {
        private const val TAG = "SkillManager"
        private const val SKILLS_DIR = "skills"
        private const val BUNDLED_SKILLS_DIR = "bundled_skills"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val skillsDir: File = File(context.filesDir, SKILLS_DIR).also { it.mkdirs() }

    /**
     * Get all skills (user + bundled), user skills take precedence.
     */
    fun getAllSkills(): List<Skill> {
        val userSkills = loadUserSkills()
        val bundled = loadBundledSkills()

        // User skills override bundled with same ID
        val userIds = userSkills.map { it.id }.toSet()
        return userSkills + bundled.filter { it.id !in userIds }
    }

    /**
     * Get enabled skills only.
     */
    fun getEnabledSkills(): List<Skill> = getAllSkills().filter { it.enabled }

    /**
     * Find a skill by slash trigger (e.g., "/morning").
     */
    fun findByTrigger(trigger: String): Skill? {
        val normalized = if (trigger.startsWith("/")) trigger else "/$trigger"
        return getEnabledSkills().find { it.trigger.equals(normalized, ignoreCase = true) }
    }

    /**
     * Find a skill by name (fuzzy match).
     */
    fun findByName(query: String): Skill? {
        val q = query.lowercase()
        return getEnabledSkills().find {
            it.name.lowercase().contains(q) || it.id.lowercase().contains(q)
        }
    }

    /**
     * Create or update a skill.
     */
    fun saveSkill(skill: Skill) {
        val file = File(skillsDir, "${skill.id}.json")
        file.writeText(json.encodeToString(Skill.serializer(), skill))
        Log.i(TAG, "Saved skill: ${skill.name} (${skill.id})")
    }

    /**
     * Delete a user skill.
     */
    fun deleteSkill(id: String): Boolean {
        val file = File(skillsDir, "$id.json")
        return if (file.exists()) {
            file.delete()
            Log.i(TAG, "Deleted skill: $id")
            true
        } else false
    }

    /**
     * Export a skill as JSON string (for sharing).
     */
    fun exportSkill(id: String): String? {
        val skill = getAllSkills().find { it.id == id } ?: return null
        return json.encodeToString(Skill.serializer(), skill)
    }

    /**
     * Import a skill from JSON string.
     */
    fun importSkill(jsonStr: String): Skill? {
        return try {
            val skill = json.decodeFromString(Skill.serializer(), jsonStr)
            saveSkill(skill)
            skill
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import skill: ${e.message}")
            null
        }
    }

    /**
     * List skills as a formatted string for the agent.
     */
    fun listSkillsFormatted(): String {
        val skills = getAllSkills()
        if (skills.isEmpty()) return "No skills configured."
        return buildString {
            appendLine("Skills (${skills.size}):")
            skills.sortedBy { it.priority }.forEach { s ->
                val status = if (s.enabled) "✓" else "✗"
                val trigger = if (s.trigger.isNotEmpty()) " [${s.trigger}]" else ""
                val schedule = if (s.schedule.isNotEmpty()) " ⏰${s.schedule}" else ""
                appendLine("$status ${s.name}$trigger$schedule — ${s.steps.size} steps")
                if (s.description.isNotEmpty()) appendLine("  ${s.description}")
            }
        }.trim()
    }

    private fun loadUserSkills(): List<Skill> {
        return skillsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString(Skill.serializer(), file.readText())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load skill ${file.name}: ${e.message}")
                    null
                }
            } ?: emptyList()
    }

    private fun loadBundledSkills(): List<Skill> {
        return try {
            val files = context.assets.list(BUNDLED_SKILLS_DIR) ?: return emptyList()
            files.filter { it.endsWith(".json") }.mapNotNull { filename ->
                try {
                    val content = context.assets.open("$BUNDLED_SKILLS_DIR/$filename")
                        .bufferedReader().readText()
                    json.decodeFromString(Skill.serializer(), content)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load bundled skill $filename: ${e.message}")
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val description: String = "",
    val trigger: String = "", // slash command like "/morning"
    val schedule: String = "", // e.g., "daily 07:00", "weekdays 08:30"
    val steps: List<SkillStep> = emptyList(),
    val enabled: Boolean = true,
    val priority: Int = 0 // lower = higher priority
)

@Serializable
data class SkillStep(
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val description: String = "" // human-readable description of this step
)
