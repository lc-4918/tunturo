package com.tunturo.app.ui.theme

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** L'icone d'application de TM-3 : logo B1 sur le vert de Mousse, avec sa version a theme. */
@RunWith(RobolectricTestRunner::class)
class AppIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `les deux icones sont adaptatives, avec un calque monochrome pour Android 13`() {
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = ContextCompat.getDrawable(context, icon)
            assertTrue("$drawable", drawable is AdaptiveIconDrawable)
            assertNotNull((drawable as AdaptiveIconDrawable).monochrome)
        }
    }

    @Test fun `le fond de l'icone est le primary de Mousse`() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val background = icon.background as ColorDrawable
        assertEquals(TunturoLight.primary.toArgb(), background.color)
        assertEquals(TunturoLight.primary.toArgb(), ContextCompat.getColor(context, R.color.ic_launcher_background))
    }
}
