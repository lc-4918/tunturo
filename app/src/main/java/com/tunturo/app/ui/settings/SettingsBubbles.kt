package com.tunturo.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * La bulle d'un curseur, ouverte d'un appui sur sa valeur : le libelle, la valeur qui suit le doigt, et la
 * piste. Elle se referme d'un appui a cote.
 *
 * Celle de la taille du symbole de position, et celles de la taille et de l'opacite des boutons de carte :
 * des valeurs qu'on regle rarement, et qui n'ont pas a tenir chacune une ligne.
 */
@Composable
internal fun ValueBubble(
    label: String, value: String, fraction: Float, steps: Int,
    onFraction: (Float) -> Unit, onDismiss: () -> Unit,
    tag: String = "value_bubble",
) {
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = Modifier.width(240.dp).testTag(tag),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = settingsPalette.card),
        ) {
            ColumnScopeMarker.SliderRow(label, value, fraction, onFraction, steps = steps)
        }
    }
}

/**
 * Une ligne a valeur dont le curseur s'ouvre dans une bulle : le libelle, la valeur au bout ("70 %"), et
 * un appui sur la valeur qui montre la piste (cf. [ValueBubble]). Pour ce qui se regle une fois et n'a pas
 * a tenir une ligne de curseur entiere.
 */
@Composable
internal fun ColumnScopeMarker.BubbleValueRow(
    label: String, value: String, fraction: Float, onFraction: (Float) -> Unit,
    tag: String, steps: Int = 0, info: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    SetRow(label, info = info) {
        Box {
            Box(Modifier.clip(MaterialTheme.shapes.small).clickable { open = true }
                .padding(horizontal = 4.dp, vertical = 6.dp).testTag("${tag}_value")) {
                ValueText(value)
            }
            if (open) {
                ValueBubble(label, value, fraction, steps, onFraction, onDismiss = { open = false }, tag = "${tag}_bubble")
            }
        }
    }
}

/** Largeur maximale d'une bulle de ligne : celle de la bulle des points d'interet. */
private val RowBubbleMaxWidth = 320.dp

/** L'ecart entre la ligne et la bulle qu'elle ouvre. */
private val RowBubbleGap = 6.dp

/**
 * Une bulle ouverte depuis une ligne de reglage : sous la ligne si elle y tient, au-dessus sinon (cf.
 * [BubblePlacement]).
 *
 * Le dessin de la bulle des points d'interet (cf. PoiFilterBubble) : une carte en surface, elevee, aux
 * grands arrondis. Elle se ferme comme elle - d'un appui a cote, ou en en ouvrant une autre, puisqu'une
 * seule bulle est tenue a la fois par qui les ouvre.
 */
@Composable
internal fun RowBubble(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gapPx = with(LocalDensity.current) { RowBubbleGap.roundToPx() }
    val positionProvider = remember(gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                BubblePlacement.start(anchorBounds.left, anchorBounds.width, windowSize.width, popupContentSize.width),
                BubblePlacement.top(anchorBounds.top, anchorBounds.bottom, windowSize.height, popupContentSize.height, gapPx),
            )
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = modifier.widthIn(max = RowBubbleMaxWidth).fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = content,
        )
    }
}
