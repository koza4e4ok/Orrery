package me.kozakov.orrery.compose.spike

import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OffscreenTextAndroidSpikeTest {
    @Test
    fun drawsTextIntoImageBitmapWithoutComposition() {
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        assertSpikeText(renderSpikeText(platform = "android-robolectric", resolver = resolver))
    }
}
