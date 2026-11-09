/* The production join gate, with advertisements from actual protocol versions. */
#include "harness.h"
#include <stdarg.h>
#define MAXIMUM_NETWORK_ADVERTISED_GAMES 2
struct network_advertised_game { int unused; };
struct network_game_client { struct network_advertised_game available_games[2]; };
static struct { unsigned int version, flags; } network_game_client_advertised_versions[2];
static int notices;
static unsigned int refused_version;
static void network_event(const char *format, ...) { (void)format; }
static void network_game_client_tell_version(unsigned int version, boolean distributed)
{ (void)distributed; notices++; refused_version=version; }
#include "version.inc"
#include "under_test.inc"
int main(int argc, char **argv)
{
 struct network_game_client client;
 struct network_advertised_game *game=&client.available_games[0];
 CHECK(argc==2,"case required");
 network_game_client_advertised_versions[0].version=41;
 network_game_client_advertised_versions[0].flags=HALO_PORT_ADVERTISED_DISTRIBUTED_FLAG;
 if (!strcmp(argv[1],"compatible41") || !strcmp(argv[1],"compatible42")) {
  if (!strcmp(argv[1],"compatible42")) network_game_client_advertised_versions[0].version=42;
  CHECK(network_game_client_advertised_game_compatible(&client,game,TRUE),"OpenCE/SlipstreamCE 41 host is joinable");
  CHECK(!notices,"compatible host has no version error");
 } else if (!strcmp(argv[1],"future_protocol")) {
  CHECK(!network_game_client_advertised_game_compatible(&client,game,FALSE),"a future protocol must not accept 41");
  network_game_client_advertised_versions[0].version=42;
  CHECK(!network_game_client_advertised_game_compatible(&client,game,FALSE),"a future protocol must not inherit the 42 alias");
  network_game_client_advertised_versions[0].version=43;
  CHECK(network_game_client_advertised_game_compatible(&client,game,FALSE),"the future protocol accepts itself");
 } else if (!strcmp(argv[1],"lockstep")) {
  network_game_client_advertised_versions[0].flags=0;
  CHECK(!network_game_client_advertised_game_compatible(&client,game,TRUE),"lockstep host still refused");
  CHECK(notices==1,"explain netcode mismatch");
 } else if (!strcmp(argv[1],"invalid")) {
  CHECK(!network_game_client_advertised_game_compatible(NULL,game,TRUE),"missing client refused");
  CHECK(!network_game_client_advertised_game_compatible(&client,client.available_games+2,TRUE),"out-of-range advertisement refused");
  CHECK(!notices,"invalid advertisement is not inspected");
 } else {
  unsigned int version=!strcmp(argv[1],"older")?40:43;
  boolean tell=strcmp(argv[1],"silent")!=0;
  network_game_client_advertised_versions[0].version=version;
  CHECK(!network_game_client_advertised_game_compatible(&client,game,tell),"different protocol still refused");
  CHECK(notices==tell,"respect silent compatibility checks");
  CHECK(!tell || refused_version==version,"explain actual mismatched version");
 }
 return 0;
}
