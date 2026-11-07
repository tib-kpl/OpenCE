/* Vulkan query retirement, using the real host function without a GPU. */
#include "harness.h"
#include <stdint.h>
#include <pthread.h>
static pthread_mutex_t visibility_results_lock = PTHREAD_MUTEX_INITIALIZER;

#define PARTS 4
struct test
{
	uint32_t index;
	float area;
	uint32_t group, parts;
	uint64_t test;
};

static struct
{
	int ready;
	struct test *tests[2];
	unsigned count[2];
	uint32_t *mapped[2];
	uint32_t latest[4];
	uint64_t counted[4];
} V;

#include "under_test.inc"

int main(int argc, char **argv)
{
	struct test tests[2] = { { 1, 1.0f, 0, 1, 7 }, { 1, 1.0f, 1, 1, 8 } };
	uint32_t samples[8] = { 12, 0, 0, 0, 24, 0, 0, 0 };

	CHECK(argc == 2, "case required");
	V.ready = 1;
	V.tests[0] = tests;
	V.mapped[0] = samples;
	V.count[0] = 1;
	if (!strcmp(argv[1], "zero"))
	{
		samples[0] = 0;
		host_vk_visibility_retired(0);
		CHECK(V.latest[1] == 0 && V.counted[1] == 7, "visibility result and test must stay together");
	}
	else if (!strcmp(argv[1], "scaled"))
	{
		tests[0].area = 4.0f;
		host_vk_visibility_retired(0);
		CHECK(V.latest[1] == 3 && V.counted[1] == 7, "visibility result and test must stay together");
	}
	else
	{
		host_vk_visibility_retired(0);
		CHECK(V.latest[1] == 12 && V.counted[1] == 7, "visibility result and test must stay together");
		/* The same slot now belongs to another flare, whose query is pending. */
		CHECK(V.counted[1] < 8, "visibility result and test must stay together");
		V.tests[0] = &tests[1];
		V.count[0] = 1;
		host_vk_visibility_retired(0);
		CHECK(V.latest[1] == 24 && V.counted[1] == 8, "visibility result and test must stay together");
	}
	CHECK(V.count[0] == 0, "visibility result and test must stay together");
	return 0;
}
