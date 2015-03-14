# Wire Protocol

> Working out the ideas. This isn't implemented.

## Game

### Game loads

When the `game` is rendered in the browser and the web-socket
connection is established, the `game` sends the following message:

    send: {:event :join, :id :game}

This will trigger the server to establish a new `game` session.


### Game registers a session

It should do nothing with any subsequent messages until it receives
the following message:

    recv: {:event :session, :id :game, :session "1234"}

The `game` should then display the session number on the screen with a
message indicating that `controllers` need to type the sequence in to
properly join.


### Controllers join the game session ###

Eventually, the `controllers` will associate themselves with the
`game's` session. The `game` will see this as start or join messages:

    recv: {:event :join, :id :player1, :session "1234"}
    recv: {:event :join, :id :player2, :session "1234"}

or

    recv: {:event :restart, :id :player1, :session "1234"}
    recv: {:event :restart, :id :player2, :session "1234"}

A "join" event indicates that the `controller` has connected to the
session for the first time. A "restart" event indicates that the
`controller` is ready for a new game.

### Game starts

Once the `game` has registered that two players are participating in
the game session, it shold send the following event:

    send: {:event :gamestart, :id :game, :session "1234"}

and then flip to the appropriate `playing` state.

### Game receives controller events

As users interact with the `controls`, the `game` will start receiving
telemetry events similar to:

    recv: {:event :telemetry, :id :player1, :session "1234", :y 54}
    recv: {:event :telemetry, :id :player1, :session "1234", :y 55}

which it'll use to move paddled up and down on the screen.

### Game ends

Once the game is complete, the `game` should publish a game-over
event:

    send {:event :gameover, :id :game, :session "1234"}

Presumably the `game` should be back to the state where it's waiting
for controllers to register their inclination to start a new game.

### Controller disconnects

If a controller is disconnected from `game` session, the server will
send the following message:

    recv: {:event :disconnect, :id :player1}

The game can decide to pause until there's a `:join` or `:restart`
event, or just tank the whole game and start over.

### Server goes down

This is detectable when the socket connection is broken. By its very
nature, there is no incoming messages.

### Server comes back up

When the connection to the server is resumed, follow the same
processes as when the game is first loaded. If there was a game in
progress, it's lost.


## Controller

After the user types in the 4 digit code, send a join msg:

    send: {:event :join, :id :player1, :session "1234"}

Display a "joining" screen until receiving a game start message:

    recv: {:event :gamestart, :id :game, :session "1234"}

Display the controls and start sending telemetry:

    send: {:event :telemetry, :id :player1, :session "1234", :y 54}
    send: {:event :telemetry, :id :player1, :session "1234", :y 55}

For pong, all we need is the `y` position, so that's all that gets
sent (aside from `evet` `id` and `session`). For other games, add
additional keys.

The game is over when the controller receives:

    recv: {:event :gameover, :id :game, :session "1234"}

At this point, the controller should present a "play again?"
button. If clicked, it sends something like:

    send: {:event :restart, :id :player1, :session "1234"}

(Or should that be join?)

If the controller receives the following:

    recv: {:event :disconnect, :id :server}

it should go back to waiting for the player to enter a code.

## Server

**Notes**

Store connection state as a hashmap? Let's say keys are the
session-ids, and values are a list of connected clients.

    {"1234" {:player1 #<stream>,
             :player1 #<stream>,
             :game #<stream>}}

* All connections in a session receive all messages sent by other
  clients. For instance, if `:player1` sends telemetry, `:player2`
  will get the data. Does this make thing simpler on the server? Does
  it make for easier debugging at all points?

* Clients will ignore messages unless they're from the server or the
  game, I guess.

* The server never initiates a message except when one of the clients
  disappears.

* When a client leaves a session, all remaining connections are sent a
  disconnect message. (Or, no: clients decide if they ought to leave
  because they get the disconnect message.)

* When clients first connect, they're placed in the "lobby" until they
  join a game session.
