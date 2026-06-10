package com.example.catechismapp.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputControllerTest {

    private class FakeSpeechRecognizerClient(
        override val isAvailable: Boolean = true,
    ) : SpeechRecognizerClient {
        var startCount = 0
        var stopCount = 0
        var cancelCount = 0

        override fun startListening(locale: Locale) {
            startCount++
        }

        override fun stopListening() {
            stopCount++
        }

        override fun cancel() {
            cancelCount++
        }

        override fun destroy() = Unit
    }

    private fun createController(
        dispatcher: TestDispatcher,
        available: Boolean = true,
        onTranscript: (String) -> Unit = {},
        onError: (String) -> Unit = {},
    ): Pair<VoiceInputController, FakeSpeechRecognizerClient> {
        val fake = FakeSpeechRecognizerClient(isAvailable = available)
        val controller = VoiceInputController(
            scope = kotlinx.coroutines.CoroutineScope(dispatcher),
            createService = { fake },
            onTranscript = onTranscript,
            onError = onError,
        )
        return controller to fake
    }

    @Test
    fun `capture timeout calls stopListening`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (controller, fake) = createController(dispatcher)

        controller.startListening()
        assertEquals(VoiceInputState.Listening, controller.state.value)

        advanceTimeBy(VoiceInputConfig.CAPTURE_MAX_MS)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, fake.stopCount)
        assertEquals(VoiceInputState.Processing, controller.state.value)
    }

    @Test
    fun `finalization timeout resets to Idle with error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var errorMessage: String? = null
        val (controller, fake) = createController(
            dispatcher = dispatcher,
            onError = { errorMessage = it },
        )

        controller.startListening()
        controller.stopListening()
        assertEquals(VoiceInputState.Processing, controller.state.value)

        advanceTimeBy(VoiceInputConfig.FINALIZATION_MAX_MS)
        dispatcher.scheduler.runCurrent()

        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertEquals("No speech detected", errorMessage)
        assertEquals(1, fake.cancelCount)
    }

    @Test
    fun `cancel resets to Idle without processing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (controller, fake) = createController(dispatcher)

        controller.startListening()
        controller.cancel()

        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertEquals(1, fake.cancelCount)
        assertEquals(0, fake.stopCount)
    }

    @Test
    fun `startListening when unavailable invokes error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var errorMessage: String? = null
        val (controller, fake) = createController(
            dispatcher = dispatcher,
            available = false,
            onError = { errorMessage = it },
        )

        controller.startListening()

        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertTrue(errorMessage!!.contains("not available"))
        assertEquals(0, fake.startCount)
    }
}
