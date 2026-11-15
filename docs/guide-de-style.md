# Guide de style Tunturo

L'identité commune de Tunturo (application Android), Trailmap (site) et Tunturo pour Garmin (montre),
définie par TM-3 et validée par Luc le 15 novembre 2026. Ce fichier fait référence : une valeur qui
change ici change dans le code, et inversement.

Où elle est appliquée :

| Support | Couleurs | Police | Logo et icône |
|---|---|---|---|
| Android | `app/.../ui/theme/Color.kt` (TunturoLight, TunturoDark) | `Type.kt`, `res/font/outfit_*.ttf` | `res/drawable/ic_tunturo_logo.xml`, `tools/app-icon/generate_icon.py` |
| Trailmap | `front/src/styles.scss`, variables `--tm-*` | `front/public/fonts/Outfit-*.ttf` | `front/public/favicon/`, généré par `generate_icon.py --web` |
| Montre | pas encore (MW-8) | pas encore | pas encore |

Maquette de référence : "Tunturo - identite" (https://claude.ai/artifact/GtTztQCUknhkzx6YneUNGj),
planches des logos, des palettes et de l'icône.

## 1. Couleurs : la palette Mousse

Couleur source **#6B7F3A**, jeu généré par le Material Theme Builder (style Tonal Spot), retenu parmi neuf
palettes essayées dans l'application (Ruska, Ambre, Airelle, Myrtille, Lichen, Mousse, Glacier, Arctique,
Granite).

**Une seule retouche à l'export** : les textes prennent la couleur de la palette, pas les gris presque
neutres de Material 3. `onSurface` et `onBackground` = `onPrimaryContainer` ; `onSurfaceVariant` =
`secondary`. Les valeurs du tableau tiennent déjà compte de cette retouche.

| Rôle Material 3 | Clair | Sombre |
|---|---|---|
| primary | #536525 | #B9CF83 |
| onPrimary | #FFFFFF | #273500 |
| primaryContainer | #D5EC9C | #3C4D0F |
| onPrimaryContainer | #3C4D0F | #D5EC9C |
| inversePrimary | #B9CF83 | #536525 |
| secondary | #5A6147 | #C3CAAA |
| onSecondary | #FFFFFF | #2C331C |
| secondaryContainer | #DFE6C5 | #434A31 |
| onSecondaryContainer | #434A31 | #DFE6C5 |
| tertiary | #39665F | #A1D0C7 |
| onTertiary | #FFFFFF | #023731 |
| tertiaryContainer | #BCECE3 | #204E48 |
| onTertiaryContainer | #204E48 | #BCECE3 |
| background | #FAFAEE | #12140D |
| onBackground | #3C4D0F | #D5EC9C |
| surface | #FAFAEE | #12140D |
| onSurface | #3C4D0F | #D5EC9C |
| surfaceVariant | #E2E4D4 | #45483C |
| onSurfaceVariant | #5A6147 | #C3CAAA |
| surfaceTint | #536525 | #B9CF83 |
| inverseSurface | #303129 | #E3E3D7 |
| inverseOnSurface | #F2F1E5 | #303129 |
| error | #BA1A1A | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #FFDAD6 | #93000A |
| onErrorContainer | #93000A | #FFDAD6 |
| outline | #76786B | #909284 |
| outlineVariant | #C6C8B8 | #45483C |
| scrim | #000000 | #000000 |
| surfaceBright | #FAFAEE | #383A32 |
| surfaceDim | #DBDBCF | #12140D |
| surfaceContainerLowest | #FFFFFF | #0D0F08 |
| surfaceContainerLow | #F4F4E8 | #1B1C15 |
| surfaceContainer | #EFEFE2 | #1F2019 |
| surfaceContainerHigh | #E9E9DD | #292B23 |
| surfaceContainerHighest | #E3E3D7 | #34362D |

Lisibilité : tout texte se lit à 4,5:1 au moins sur son fond, et un contour à 3:1, dans les deux thèmes
(vérifié par `ThemeTest`).

### Où va la couleur

| Élément | Couleurs |
|---|---|
| Boutons de la carte | fond primary (opacité 90 % au moins), dessin onPrimary ; allumé : primaryContainer et onPrimaryContainer |
| Petits boutons ronds (fermer, actualiser, agrandir...) | primary et onPrimary ; translucides à 75 % sur une image, une miniature ou une infobulle |
| Bouton d'action principal (Calculer, Importer) | aplat primary |
| Bouton secondaire (Annuler) | aplat secondaryContainer, sans contour |
| Onglet retenu | primary et onPrimary |
| Puce retenue | tertiaryContainer et onTertiaryContainer |
| Menu latéral | fond surfaceContainerLow |
| Réglages | fond surfaceContainerHigh, cartes surfaceContainerLow (sombre : fond surface, cartes surfaceContainerHigh) |
| En-têtes de panneau et de réglages | fond des cartes, jamais un aplat de couleur |
| Parcours du calcul d'itinéraire | primary |

### Couleurs qui ne suivent pas le thème

- **Repère GPS** : la couleur choisie dans ses réglages, jamais celle du thème. Gris #8A8A8A quand la
  position n'est plus sûre.
- **Bouton allumé en alerte** (cloche hors trace) : rouge #B3261E.
- **Traces de la bibliothèque** : palette de traces (`MapPalette`), quinze couleurs.
- **Disciplines de Trailmap** : VTT #B8621B, VTC #1F6F8B, randonnée #6A4C93.

### Couleurs réservées au parcours calculé

Une trace ne peut pas prendre une couleur trop proche du parcours du calcul d'itinéraire, sans quoi on ne
sait plus lequel des deux traits est le trajet calculé. "Trop proche" = écart Delta E 1976 (espace CIELAB)
inférieur à **20** avec l'une des deux primaires, claire #536525 et sombre #B9CF83.

- Android : `PlannerColorGuard`, au choix de couleur et à l'import.
- Trailmap : `planning-maplibre.spec.ts` vérifie que chaque discipline reste à 20 au moins du parcours.
- Le vert clair #7CB342 (26 de la primaire sombre) reste permis : jugé assez éloigné.

## 2. Typographie : Outfit

**Outfit**, police unique de l'interface et du nom, sous licence OFL, embarquée (l'application et le site
fonctionnent hors ligne, pas de Google Fonts). Quatre graisses statiques : Regular 400, Medium 500,
SemiBold 600, Bold 700. Outfit ne couvre que l'alphabet latin : le grec et le cyrillique retombent sur la
police du système.

Échelle de texte de l'application (taille / interligne en sp, graisse) :

| Style | Taille | Interligne | Graisse | Particularité |
|---|---|---|---|---|
| displayLarge | 40 | 48 | SemiBold | approche -0,5 |
| displayMedium | 34 | 42 | SemiBold | approche -0,4 |
| displaySmall | 28 | 36 | SemiBold | approche -0,3 |
| headlineLarge | 26 | 32 | SemiBold | approche -0,3 |
| headlineMedium | 24 | 30 | SemiBold | approche -0,2 |
| headlineSmall | 22 | 28 | SemiBold | approche -0,2 |
| titleLarge | 20 | 26 | SemiBold | approche -0,2 |
| titleMedium | 16 | 24 | SemiBold | approche -0,1 |
| titleSmall | 15 | 20 | SemiBold | chiffres à chasse fixe (tnum), pour les totaux |
| bodyLarge | 15 | 22 | Regular | |
| bodyMedium | 14 | 20 | Regular | libellés des lignes |
| bodySmall | 12 | 16 | Regular | |
| labelLarge | 13 | 18 | Medium | |
| labelMedium | 12 | 16 | Medium | |
| labelSmall | 10,5 | 14 | Medium | approche +0,4 |

**Le nom "Tunturo"** s'écrit toujours en Outfit SemiBold, quel que soit le texte qui l'entoure
(`brandedText` dans `BrandName.kt`). Le mot-symbole s'écrit en bas de casse : "tunturo".

## 3. Logo : B1 "sommet en courbes"

Trois courbes de niveau fermées et le point du sommet, vues d'en haut. **Le dessin retenu est l'esquisse**
(une version affinée a été proposée et écartée). Une seule couleur.

Dessin, dans un carré de 64 :

```
courbe 1 : M8,46 C8,28 22,14 34,14 C48,14 58,28 56,44 C54,54 14,56 8,46 Z
courbe 2 : M17,42 C17,30 26,21 34,21 C44,21 49,30 48,40 C47,47 20,49 17,42 Z
courbe 3 : M26,37 C26,31 30,27 34,27 C39,27 41,32 40,36 C39,40 27,41 26,37 Z
sommet   : disque de centre (33,5 ; 33,5), rayon 2
trait    : 2,6 (3,4 dans le menu latéral, où le logo est petit)
```

Couleurs du logo : primary sur fond clair, primaryContainer (#D5EC9C) sur fond sombre ou sur le vert.

## 4. Icône d'application

- **Fond primary #536525, logo en primaryContainer #D5EC9C** (jeu 1 de la planche).
- Icône adaptative Android : le logo couvre 62 % de la zone visible (72 dp sur 108), centré.
- Icône à thème (Android 13 et plus) : le même dessin en une couleur, le système le colore.
- Android 7 : PNG ronds et carrés arrondis, à toutes les densités.
- Écran de démarrage : le logo clair sur le disque vert de l'icône, fond gris foncé #2C2C2E.
- Site Trailmap : même icône en carré arrondi ; pour les favicons (32 px et moins), logo à 80 % et trait de 3,4,
  sans quoi les courbes se brouillent. Favicon SVG et ICO, icône d'écran d'accueil iOS et Android,
  `theme-color` #536525.
- Tout se régénère par `python3 tools/app-icon/generate_icon.py --web ~/Projets/trailmap/front/public/favicon` :
  changer le script, pas les fichiers.

## 5. Formes et espacements

Arrondis (dp) : 6 pastilles, 8 champs et boutons, 12 cartes et menus, 16 bulles sur la carte et cartes
des réglages, 24 bandes du bas et dialogues ; rond complet pour les puces et boutons icône.

Espacements : pas de 4, six crans seulement - 2, 4, 8, 12, 16, 24 dp. Entre deux blocs, on choisit un
cran, pas une valeur.

## 6. Icônes

Icônes de Material (Outlined de préférence), et icônes dessinées au trait (`TunturoIcons`) là où celles
de Material sont pleines et massives (la poubelle). Un trait, 24 dp, bouts et angles arrondis.

## 7. Essayé et refusé

À ne pas reproposer :

- en-têtes de panneau ou de réglages en aplat de couleur ;
- aplat sur Enregistrer, Exporter et Réinitialiser du calcul d'itinéraire ;
- couleur du thème sur le repère GPS ;
- filets sous l'en-tête du menu latéral et du gestionnaire de couches (retirés) ;
- l'ancien thème bleu #16588F de l'application et l'orange de Trailmap ;
- Inter (remplacée par Outfit partout).

## 8. Reste à appliquer

- Fond de carte Trailmap aux couleurs de l'identité (TM-11b).
- Montre (MW-8) : la montre est limitée à 64 couleurs ; chaque couleur du guide y sera ramenée à la plus
  proche de sa palette, sans couleur arbitraire.
- iOS : mêmes valeurs que l'application Android.
