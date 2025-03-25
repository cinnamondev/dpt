# **D**ynamic **P**terodac**T**yl

Velocity plugin that is able to start pterodactyl containers on demand and send players to the respective server, and
shut down servers that have neen inactive for too long.

## Commands

### `/dptsend <player/all/current> <server> <promptPlayersToJoin/promptMeToSend>`
  - player: send player to server
  - all: send all players on the proxy to server
  - current: CALLER MUST BE A PLAYER. send all players on current server to server.
  - promptPlayersToJoin: send a message to all players when the server is ready inviting them to the server.
  - promptMeToSend: send an interactive message when the server is ready that sends all the players on click.

### `/dptsend server <origin server> <destination server> <promptPlayersToJoin/promptMeToSend>`
  - origin server: any velocity server.
  - destination server: any velocity server dpt is configured to control.
  - promptPlayersToJoin: send a message to all players when the server is ready inviting them to the server.
  - promptMeToSend: send an interactive message when the server is ready that sends all the players on click.

### `/dptserver <destination server> <immediate>`
  - destination server: any velocity server dpt is configured to control
  - immediate: `dptserver` default behaviour should be to prompt when the server is ready. specifying this option will send the player as soon as the servers ready.

### `/dptping <server>`
  - server: any velocity server dpt is configured to control

### `/dptwd <server> <start/stop>`
  - server: any velocity server dpt is configured to control

## Permissions:

- `dpt.send` Access to `/dptserver <destination server>`. Give this permission to users ideally (plus specific server permissions)
- `dpt.send.others` Send other players on proxy/server (`/dptsend <player> <destination>`)
- `dpt.send.all` Send all players on proxy/server (`/dptsend <all/current> <destination>`)
- `dpt.send.<server-name>` Access to do `/dptserver <server-name>` and `/dptsend <player/all/current> <server-name>` where `server-name` is already configured.
- `dpt.send.anywhere` Above permission but for any server.
- `dpt.ping` Ping a server.
- `dpt.watchdog` Access to `/dptwd`

# License

Licensed under Apache 2.0.