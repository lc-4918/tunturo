package com.tunturo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Cote de la pointe, avant rotation : un carre tourne d'un quart de tour, dont la moitie depasse. */
private val TailSize = 14.dp

/**
 * Une bulle qui SORT d'un bouton de la colonne de droite : sa pointe le vise, et elle grandit depuis lui.
 *
 * La bulle des points d'interet et celle de l'enregistrement : le meme geste - un bouton qui ouvre ses
 * choix a cote de lui - et donc le meme dessin.
 *
 * L'appelant la pose contre le bord gauche du bouton, son bas contre celui du bouton (cf. MapControls).
 * [content] recoit le modificateur qui borne la carte a [maxWidth] et lui laisse la place de la pointe.
 *
 * @param tailFromBottom hauteur de la pointe au-dessus du bas de la bulle : celle du centre du bouton.
 */
@Composable
fun AnchoredBubble(
    open: Boolean,
    maxWidth: Dp,
    tailFromBottom: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    AnimatedVisibility(
        visible = open,
        modifier = modifier,
        // Le point fixe de la mise a l'echelle : le bord droit, a la hauteur de la pointe. C'est ce qui
        // fait sortir la bulle DU BOUTON plutot que de la faire gonfler sur place.
        enter = scaleIn(
            animationSpec = keyframes {
                durationMillis = 320
                0.25f at 0
                1.015f at 210 using CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
                1f at 320
            },
            initialScale = 0.25f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + fadeIn(tween(200)),
        exit = scaleOut(
            animationSpec = tween(260, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            targetScale = 0.2f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + fadeOut(tween(260)),
    ) {
        /*
         * La bulle, et sa pointe posee A CHEVAL sur son bord droit.
         *
         * La boite fait la largeur de la bulle plus une demi-pointe : la carte occupe la gauche, la pointe
         * s'aligne a droite, et son centre tombe donc exactement sur le bord de la carte. La rotation ne
         * change pas les limites de mise en page - le losange deborde de lui-meme vers le bouton, ce qui
         * est precisement ce qu'on veut d'une pointe.
         *
         * Dessinee APRES la carte, donc par-dessus : posee avant, le fond de la carte l'aurait recouverte.
         */
        Box(Modifier.widthIn(max = maxWidth + TailSize / 2).padding(start = 8.dp)) {
            content(Modifier.widthIn(max = maxWidth).padding(end = TailSize / 2))
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(bottom = (tailFromBottom - TailSize / 2).coerceAtLeast(0.dp))
                    .size(TailSize)
                    .rotate(45f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
