# Intégration Android de SlipstreamCE

Branche de préparation : `android-slipstream-integration`, basée sur
`4fed8ed6ca900c1c5939d80555b4fbd1dcb84660` de `tib-kpl/OpenCE`.
Ce commit de `main`, déjà poussé, fusionne les nouveautés d'OpenCE jusqu'à
`fc9deb0b1`. Cette intégration reste sur une branche distincte pour revue.

La référence comparée est SlipstreamCE
[`f8dbc4411` (v138)](https://github.com/thelinkin3000/SlipstreamCE/tree/f8dbc4411).
Les reprises conservent les auteurs et la provenance des commits. Les fonctions
partagées nécessaires ont été intégrées avec leurs dépendances ; le diff contient
donc aussi du code desktop, Switch et PS5. Ces deux derniers ports ne sont pas
validés dans cet environnement.

## Nouveautés reprises

| Domaine | Contenu |
| --- | --- |
| Vulkan | Thread de rendu, géométrie statique persistante, transferts partiels, optimisations des passes et de la présentation, correctifs Turnip/KGSL et gestion de durée de vie des buffers. |
| Latence | Caméra directe Android et réduction des blocs du mixeur audio. |
| Pilotes | Archives multiples dans `freedreno_drivers`, sélection par nom et retour à OpenGL après un démarrage Vulkan impossible. |
| Résolution | Limite maximale de rendu, de 480 lignes à la résolution native. |
| Tactile | Boutons dans les zones de gestes Android, Options déplaçable, réglage du mouvement de caméra pendant un appui, interface défilante et nouveaux formats de disposition. |
| Gyroscope et profils | Réglages par axe et en zoom, réglages clavier/souris/gyro par profil. |
| Cartes et mods | Import de `.map` et de `.zip`, packs de mods, suppression et signalement des doublons, relocalisation du cache Custom Edition. |
| Cinéma | Enregistrement, bibliothèque, import/export Android, caméras et commandes tactiles de lecture, vitesses et navigation temporelle, stockage des longs replays côté hôte. |
| Overlay | Panneau en jeu : bots, vitesse, HUD, caméra, rendu et commandes supplémentaires ; ouverture par gestes et extension par fichiers TOML. |
| Bots | Comportements CTF, King of the Hill, Infection et SWAT, véhicules, routes et parties Internet privées. |
| Corps | Ragdolls optionnels, avec modes `none`, `woody` et `halo2`. |
| Menus | Aperçu Spartan animé, icônes, À propos et crédits, correctifs du lance-flammes CE et du HUD de cinématique. |
| Diagnostic | Profils regroupés, mesures GPU, benchmarks/replays, traces d'import/export et réduction des logs Vulkan. |
| Build | Adaptations du runtime invité et de Gradle requises par ces ajouts. |

## Adaptations propres au fork

- L'application reste OpenCE, paquet `com.halo.decomp`. Le dépôt de mises à jour
  reste celui de la construction (`GITHUB_REPOSITORY` en CI).
- Les menus français, l'import des disques européens, les raccourcis et l'écran
  graphique Android du fork sont conservés. Les nouveaux textes XML sont traduits,
  et l'entrée Cinéma a ses deux images françaises. Le panneau overlay importé
  conserve encore des libellés anglais dans son code.
- Les anciens choix graphiques `custom` et `auto` sont migrés vers les noms
  d'archives. L'écran graphique importe et choisit les pilotes dans le même dossier
  que les réglages en jeu.
- La détection, l'apparence et le remappage des manettes existants sont conservés,
  avec leurs tests. Bumper Jumper conserve les commandes grenade/lampe du fork.
- Les requêtes de visibilité Vulkan associent résultat et numéro de soumission
  à travers le nouveau thread de rendu, avec verrou séparé et tests de régression.
- Le cache stable de textures Custom Edition du fork est raccordé au nouveau
  renderer Vulkan : stockage enregistré, bornes, palettes, invalidation et
  libération GPU sont conservés avec les nouveaux uploads et décodages du HUD.
- Un salon local vide ne peut plus lancer le compte à rebours ; le délai court
  introduit pour les parties sur une seule machine est conservé.
- La version réseau passe de **41 à 42** : les nouvelles extensions de gameplay
  et de replay ne doivent pas rejoindre des exécutables utilisant le protocole 41.
- Les menus sont régénérés par `tools/regenerate_port_menus.py` et les images
  françaises par `tools/translate_menu_art.py`. La cible APK suit aussi les
  modifications Java et de traduction lors des builds incrémentaux.

## Validation

- APK Android optimisé compilé avec les deux images invitées, OpenGL ES et Vulkan,
  et vérification des imports hôte. Compilation sans PGO : clang 18 local, profils
  prévus pour clang 22. L'APK local est signé avec la clé debug de la machine.
- Campagne complète : 1 794 réussites, 2 ignorés et 20 échecs avant la dernière
  correction CE. Les 17 échecs Android ont ensuite été corrigés et revérifiés dans
  la sélection Android/imports : **126 réussites, 1 ignoré**. Les trois autres
  échecs sont reproduits sur la copie de référence : `riding-up` dans
  `test_elevator_prediction.py`, et les deux contrôles négatifs `nan-crossing`
  dans `test_polygon_clip.py` sous clang 18. Les assertions sont conservées.
- Règles de build, profils et HUD : 55 réussites, 3 ignorés.
- Tests Java du tactile, de ses imports/exports et du gyroscope : réussis.
- Traduction : 41 fichiers XML, aucun texte XML manquant en français. Structure
  et marqueurs de format préservés ; les deux images Cinéma sont présentes dans
  l'APK, vérifiées visuellement.
- Syntaxe des branches desktop de `dsound_sdl.c`, `port_config.c`,
  `sdl_platform.c` et `hud_hires.c` : réussie.
- Compilation web indisponible localement : Emscripten absent, donc cible `web`
  non générée. Tests JavaScript du site indisponibles ici : Node.js absent.
  Les tests Python du graphe web font partie de la campagne complète.
- Les logs locaux sont dans `dist/integration/` ; l'APK est
  `dist/android/app-release.apk`.
- La CI de la branche `5be0c5c9e` a réussi sur Android. Elle a révélé trois
  problèmes des ports partagés à résoudre avant fusion : symboles de stockage
  et de sections replay à l'édition de liens Linux, inclusion de `pthread.h`
  dans les bots Windows, et type de `va_list` dans le terminal web.
- Les essais matériels et le nouveau panneau Android sont décrits dans
  [android-settings.md](android-settings.md).

La compilation ne remplace pas un essai sur téléphone : performances GPU,
compatibilité des pilotes, import réel de données et partie réseau doivent encore
être vérifiés sur appareil. Aucun gain chiffré n'est annoncé sans mesure.
