- put timing values in config
- Ping/Pong for crap networks - not in irclj alpha! build our own?

more commands:
- leave / join a room
- details of a commit
- watch / unwatch project
- verbose / quiet modes
- specify a gitlab token or username/password
-- the bot could be almost zero config:
-- on start do nothing
-- "identify <token>" to identify a user - private message only
-- "groups" to list groups
-- "watch <group>" to watch a group

done:
- switch to core.async to handle threads and states and messages
- send responses to help/quit publicly instead of DM
- quit should actually kill the process!  (might wait until we go core.async)

