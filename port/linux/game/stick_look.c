/* Per-profile stick aiming, separate from mouse, touch and gyroscope input. */
#include "cseries.h"
#include "../src/port_config.h"
#include "stick_look.h"

void port_stick_look_rates(short player, float *yaw, float *pitch)
{
	double horizontal = config_player_real(player, "input.stick_sensitivity_horizontal");
	double vertical = config_player_real(player, "input.stick_sensitivity_vertical");

	/* Zero inherits the saved Halo profile. Other values use the stock
	1-10 sensitivity scale; hand-written settings cannot exceed its range. */
	if (horizontal > 0.0)
	{
		if (horizontal < 1.0) horizontal = 1.0;
		if (horizontal > 10.0) horizontal = 10.0;
		*yaw = (float)(60.0 + 20.0 * horizontal);
	}
	if (vertical > 0.0)
	{
		if (vertical < 1.0) vertical = 1.0;
		if (vertical > 10.0) vertical = 10.0;
		*pitch = (float)(30.0 + 10.0 * vertical);
	}
	if (config_player_boolean(player, "input.stick_equal_axes"))
		*pitch *= 2.0f;
}
