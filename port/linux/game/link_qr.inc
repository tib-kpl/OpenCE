/* the link page's QR codes: Direct Link's, the invite's and the Theater's (Switch and PS5 only: LINK_PAGE).
Included by menu_functions.c, which it reads the statics of, at the place the code was */
/* The QR codes (link_page.c's encoder): Direct Link's of the link page's
address, in the games' list while it has none, centred over it and drawn
from where the ticker line under the list is (UI units: the list's rows from
y 102 to 357, the ticker's text from x 100 at y 402); and Server Setup's of
the game's invite link, on its own screen (invite_copy), over its line of
text. The Switch's and the PS5's. Each picture is made again when its text changes: whole
texels a module (and the white margin a QR code wants, four modules), in a
256 by 256 texture */
enum
{
	LINK_QR_TEXTURE = 256,
	LINK_QR_MARGIN = 4,
	_link_qr_address = 0,
	_link_qr_invite,
	/* (the Theater's, of its replay page's address: network_replay_page.c) */
	_link_qr_replays,
	NUMBER_OF_LINK_QRS
};

static struct
{
	struct bitmap_data *bitmap;
	char text[128];
	/* the texels the code fills */
	short texels;
} link_qrs[NUMBER_OF_LINK_QRS];

static boolean link_qr_make(short which, char const *text)
{
	long modules = which == _link_qr_address ? link_page_qr_size() : link_page_text_qr(text);
	long side = modules + 2 * LINK_QR_MARGIN;
	long scale = modules ? LINK_QR_TEXTURE / side : 0;
	unsigned long *pixels;
	long x, y;

	if (scale < 1)
		return FALSE;
	if (!link_qrs[which].bitmap)
	{
		link_qrs[which].bitmap = bitmap_2d_new(LINK_QR_TEXTURE, LINK_QR_TEXTURE, 0, 11 /* a8r8g8b8 */);
		if (link_qrs[which].bitmap && !rasterizer_bitmap_new(link_qrs[which].bitmap))
		{
			bitmap_delete(link_qrs[which].bitmap);
			link_qrs[which].bitmap = NULL;
		}
		if (!link_qrs[which].bitmap)
			return FALSE;
	}
	pixels = (unsigned long *)link_qrs[which].bitmap->base_address;
	for (y = 0; y < LINK_QR_TEXTURE; y++)
	{
		for (x = 0; x < LINK_QR_TEXTURE; x++)
		{
			long module_x = x / scale - LINK_QR_MARGIN;
			long module_y = y / scale - LINK_QR_MARGIN;
			boolean inside = x < side * scale && y < side * scale;
			boolean dark = inside && module_x >= 0 && module_y >= 0 && module_x < modules && module_y < modules &&
				(which == _link_qr_address ? link_page_qr_dark((int)module_x, (int)module_y) :
					link_page_text_qr_dark((int)module_x, (int)module_y));

			pixels[y * LINK_QR_TEXTURE + x] = !inside ? 0 : dark ? 0xFF000000UL : 0xFFFFFFFFUL;
		}
	}
	rasterizer_bitmap_changed(link_qrs[which].bitmap);
	link_qrs[which].texels = (short)(side * scale);
	csstrncpy(link_qrs[which].text, text, sizeof(link_qrs[which].text) - 1);
	return TRUE;
}

/* the code of text, side UI units square, centred at (centre_x, centre_y) */
static void link_qr_draw_at(short which, char const *text, short centre_x, short centre_y, short side)
{
	rectangle2d square;
	rectangle2d source;

	if (!text[0])
		return;
	if ((!link_qrs[which].bitmap || strcmp(link_qrs[which].text, text)) && !link_qr_make(which, text))
		return;
	square.x0 = (short)(centre_x - side / 2);
	square.y0 = (short)(centre_y - side / 2);
	square.x1 = (short)(square.x0 + side);
	square.y1 = (short)(square.y0 + side);
	source.x0 = source.y0 = 0;
	source.x1 = source.y1 = link_qrs[which].texels;
	draw_bitmap_in_rect(link_qrs[which].bitmap, &square, &source, NULL, 0xFFFFFFFFUL, NULL, TRUE);
}

/* (Direct Link's) over the empty list, from the ticker line */
static void link_qr_draw(rectangle2d const *ticker)
{
	real scale = (real)(ticker->y1 - ticker->y0) / 18.0f;

	if (multiplayer.mode != _multiplayer_mode_direct_link || multiplayer.game_count)
		return;
	link_qr_draw_at(_link_qr_address, link_page_address(), (short)(ticker->x0 + (320 - 100) * scale),
		(short)(ticker->y0 - (402 - (102 + 357) / 2) * scale), (short)(200 * scale));
}

/* (the invite's screen) over its line of text (UI units: the text from x 40
at y 410, 560 wide; the code between the title, to y 60, and it) */
static void invite_qr_draw(rectangle2d const *line)
{
	real scale = (real)(line->x1 - line->x0) / 560.0f;
	char link[TEXT_FIELD_LENGTH];

	if (!p2p_invite_link(link, sizeof(link)))
		return;
	link_qr_draw_at(_link_qr_invite, link, (short)(line->x0 + (320 - 40) * scale),
		(short)(line->y0 - (410 - (60 + 400) / 2) * scale), (short)(320 * scale));
}
