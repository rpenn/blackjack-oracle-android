package com.blackjackoracle.tutorial

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/// The tutorial narrator plays pre-rendered MP3s from res/raw and only falls
/// back to on-device synthesis when a file is missing — a silent downgrade to
/// a much worse voice. This pins every scripted line to a real, non-trivial
/// res/raw file so a rename or dropped file fails the tests, not the demo.
/// (Android analog of iOS TutorialAudioTests.)
class TutorialAudioResourcesTest {

    @Test
    fun everyLineResolvesToARealRawResource() {
        // R.raw's constants are compile-time proof the resources exist, but the
        // script references its files by NAME (resources.getIdentifier at
        // runtime), so check the name strings against R.raw's fields.
        val rawFields = Class.forName("com.blackjackoracle.R\$raw")
            .fields.map { it.name }.toSet()
        for (line in TutorialScript.allLines) {
            assertTrue(
                "No res/raw resource named '${line.audioResource}' for line \"${line.text}\"",
                line.audioResource in rawFields,
            )
        }
    }

    @Test
    fun everyBundledFileContainsRealSpeech() {
        // Gradle runs JVM unit tests with the module directory as the working
        // dir, so the source files are directly checkable. A near-empty MP3
        // means a failed TTS render got committed.
        val rawDir = File(System.getProperty("user.dir"), "src/main/res/raw")
        assertTrue("res/raw not found at ${rawDir.absolutePath}", rawDir.isDirectory)
        for (line in TutorialScript.allLines) {
            val file = File(rawDir, "${line.audioResource}.mp3")
            assertTrue("Missing bundled audio ${file.name}", file.isFile)
            assertTrue("${file.name} should contain real speech", file.length() > 10_000)
        }
    }
}
