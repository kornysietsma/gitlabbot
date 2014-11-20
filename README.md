# gitlabbot

An IRC bot to watch gitlab commits

## Usage

At the moment this is in active development, and running it probably requires reading code. sorry.

I started this using lazybot, but it was a bit complex, and the mongodb dependency made it too hard to run at work,
so decided to roll my own... it's now based on http://github.com/kornysietsma/botty

## usage

via leiningen:

`lein run config_file.clj`

sample config file is in resources/sample-config.clj

You can also build an uberjar with `lein uberjar` and then run with just java:

`java -jar uberjar.jar config_file.clj`

## License

        DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
                    Version 2, December 2004

 Copyright (C) 2014 Kornelis Sietsma <korny@sietsma.com>

 Everyone is permitted to copy and distribute verbatim or modified
 copies of this license document, and changing it is allowed as long
 as the name is changed.

            DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
   TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION

  0. You just DO WHAT THE FUCK YOU WANT TO.