# Panneau de configuration Android

L'installation du disque et les réglages ouverts depuis le jeu partagent une
interface bleu nuit/cyan. Son organisation reprend la référence demandée,
le panneau Android du projet voisin `Unreal-Android` : rubriques latérales,
choix réglables avec les flèches, navigation aux gâchettes et retour au jeu.
Le dessin est réalisé par les vues Android et utilise la police Overpass
déjà fournie dans le projet, avec sa licence.

## Accès et commandes

- Avec une manette connectée : **Start + Select** ouvre le panneau, dans les
  deux ordres. La roue crantée tactile est masquée.
- Sans manette : la roue crantée reste visible. L'ajout, le retrait et la
  modification d'un périphérique actualisent sa visibilité.
- **L1/R1** change de rubrique ; **A** valide ; **gauche/droite** ajuste un
  choix. Les boutons et les flèches sont aussi utilisables au tactile.
- **B**, ou **Start + Select** depuis le panneau en jeu, revient au jeu.
- Start et Select seuls conservent leurs actions. Un bref délai de 250 ms
  laisse le temps de former la combinaison. Les appuis d'une autre manette
  ne peuvent pas la compléter. Une pause, perte de focus ou déconnexion
  annule les appuis en attente et relâche ceux déjà transmis au moteur.

## Rubriques

| Rubrique | Contenu |
| --- | --- |
| Jeu et données | État des données et import du disque Xbox `.iso` / `.xiso`, avec prise en charge des éditions européennes. |
| Graphismes | Choix Vulkan / OpenGL ES et accès aux pilotes. |
| Pilotes | Pilote système, téléchargement Turnip, import d'archive et sélection des archives installées. |
| Affichage | Résolution maximale, échelle de rendu et synchronisation verticale. |

Les réglages graphiques sont écrits dans le `config.toml` existant et
s'appliquent au prochain lancement. L'import utilise l'extracteur existant.
Le téléchargement du pilote utilise le même habillage et indique sa progression.

## Validation

`python tools/test_touch_layout.py` vérifie le tactile, le gyroscope et la
logique de combinaison du code de production : deux ordres, appuis simples,
répétitions, combinaison après un appui déjà transmis, annulation et séparation
des manettes. L'adaptateur Android est aussi exécuté avec une horloge contrôlée :
un appui rejoué reste enfoncé au moins 50 ms pour être vu par SDL, et aucun
relâchement retardé ne fuit après une perte de focus ou une déconnexion.
L'APK compile en variante normale et en variante de test ; le
manifeste confirme les paquets respectifs `com.halo.decomp` et
`com.halo.decomp.test`. Le changement de variante invalide la cible APK Ninja.

Les premiers essais sur Odin 2 Portal puis AYN Thor ont vérifié le rendu du
panneau et le démarrage du menu principal Vulkan. Sur la Thor, la roue est
masquée avec sa manette intégrée, Start + Select ouvre le panneau, L1/R1 change
de rubrique et une modification de résolution a été confirmée dans le fichier.
Les essais utilisent une application séparée et une copie des cartes et de la
configuration ; l'application habituelle et ses sauvegardes restent intactes.

Le retrait physique d'une manette n'est pas vérifié sur ces consoles à manette
intégrée. Les captures et logs de test restent locaux dans `dist/integration/`.
