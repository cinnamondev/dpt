# Dynamic PterodacTyl

* Work in progress, but most of the things are there.

Simple Velocity plugin to start servers as they are required!

How many use cases require such a solution when you can just keep running a server? Not many! But this is *probably useful to someone*

If a server isn't on, `/dptsend` will start the server before sending the player(s)! (TODO: after some time of server inactivity the server should signal to be stopped(?))

Commands:

(not implemented)

`/dptsend <player/all/here> <server>`
  - player: send player to server
  - all: send all players on the proxy to server
  - here: CALLER MUST BE A PLAYER. send all players on current server to server.

Permissions:
- `dpt.send`
- `dpt.send.others` (send others)
- `dpt.send.all` (access to all servers LISTED IN THE __PLUGIN__ CONFIGURATION)
 -`dpt.send.<server-name>` (access to a specific server LISTED IN THE __PLUGIN__ CONFIGURATION) 

 `all` and `<server-name>` would not apply to a regular `/send` or `/server` command and only refer to the capability to
 run the dptsend command targeting a server that is already specifed in the plugins config.

`/dptserver <server>`
Send player to server

Permissions:
- `dpt.send`

# License

Licensed under Apache 2.0.