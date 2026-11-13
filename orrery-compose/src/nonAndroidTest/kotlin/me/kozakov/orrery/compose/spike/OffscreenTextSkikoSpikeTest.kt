package me.kozakov.orrery.compose.spike

import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlin.test.Test

class OffscreenTextSkikoSpikeTest {
    @Test
    fun drawsTextIntoImageBitmapWithoutComposition() {
        assertSpikeText(renderSpikeText(platform = "skiko", resolver = createFontFamilyResolver()))
    }
}
