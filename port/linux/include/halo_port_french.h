/*
HALO_PORT_FRENCH.H

This fork's French texts of the port's own messages, those the game builds
in its code rather than reads from the menus' files (the Mods screen, the
mods' problems). They are French when config.toml's game.language is "fr",
as the French PC menus are (port/assets/menus/lang/fr.json): each place
names its English text and its French one, PORT_TEXT(english, french).
*/

#ifndef HALO_PORT_FRENCH_H
#define HALO_PORT_FRENCH_H

#include <string.h>

const char *config_string(const char *name);

static int port_french(void)
{
	const char *language = config_string("game.language");

	return language && !strncmp(language, "fr", 2);
}

#define PORT_TEXT(english, french) (port_french() ? (french) : (english))

#endif
