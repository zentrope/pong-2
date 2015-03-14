# Wire Protocol

> Working out the ideas. This isn't implemented.


## TODO

* Work out how to restart a game once it's finished. Should players
  just have to re-enter a new room code?

## Game

When the game-board is rendered in the browser, it sends the
following:

    send: {:tag :join, :id :game}

It has to do this to tell the server that it's a `game` rather than a
`controller`.

It should do nothing with any subsequent messages until it sees:

    recv: {:tag :room, :id :game, :room "1234"}

It should then display the room number on the screen with a message
indicating that controllers need to type the sequence in to properly
join.

Eventually, the controllers will join up which the `game` will see as
following:

    recv: {:tag :join, :id :player1, :room "1234"}
    recv: {:tag :join, :id :player2, :room "1234"}

Once both are in place, it'll send:

    send: {:tag :gamestart, :id :game, :room "1234"}

and then flip to the appropriate `playing` state. It'll start
receiving telmetry something like:

    recv: {:tag :telemetry, :id :player1, :room "1234", :y 54}
    recv: {:tag :telemetry, :id :player1, :room "1234", :y 55}

which it'll use to move paddled up and down on the screen.

Once the game is complete, the `game` should send:

    send {:tag :gameover, :id :game, :room "1234"}

and then reset itself back to the "start over" screen.

## Controller

After the user types in the 4 digit code, send a join msg:

    send: {:tag :join, :id :player1, :room "1234"}

Display a "joining" screen until receiving a game start message:

    recv: {:tag :gamestart, :id :game, :room "1234"}

Display the controls and start sending telemetry:

    send: {:tag :telemetry, :id :player1, :room "1234", :y 54}
    send: {:tag :telemetry, :id :player1, :room "1234", :y 55}

For pong, all we need is the `y` position, so that's all that gets
sent (aside from `tag` `id` and `room`). For other games, add
additional keys.

The game is over when the controller receives:

    recv: {:tag :gameover, :id :game, :room "1234"}

At this point, the controller should present a "play again?"
button. If clicked, it sends something like:

    send: {:tag :restart, :id :player1, :room "1234"}

(Or should that be join?)

If the controller receives the following:

    recv: {:tag :disconnect, :id :server}

it should go back to waiting for the player to enter a code.

## Server

**Notes**

Store connection state as a hashmap. Keys are the rooms, and values
are a list of connected clients.

    {"1234" {:player1 #<stream>,
             :player1 #<stream>,
             :game #<stream>}}

* All connections in a room receive all messages sent by other
  clients. For instance, if `:player1` sends telemetry, `:player2` will
  get the data. Does this make thing simpler on the server? Does it
  make for easier debugging at all points?

* Clients will ignore messages unless they're from the server or the
  game, I guess.

* The server never initiates a message except when one of the clients
  disappears.

* When a client leaves a room, all remaining connections are sent a
  disconnect message.

* When clients first connect, they're placed in the "lobby" until the
  join the right room.

* Should the server kick game-over rooms back to the lobby?
