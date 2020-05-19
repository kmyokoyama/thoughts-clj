# twitter-clj

Twitter-based API for experimentation with Clojure.

This is a toy project intended to be used as a platform for learning and experimenting with Clojure. It is like a canvas where I can try language features, libraries and tools related to back end web development with Clojure.

I've decided to build an API similar to Twitter because it is quite familiar to me (and most people). I really didn't want to spend much time thinking in business requirements and features. Instead, I wanted to focus on the tech-side (Clojure, web development concepts, back end technologies and so on).

Since the domain this project implements is well-known by many developers, this API also presents itself as an option for front end engineers who want to practice building Single-Page Applications (SPA). Simply run this back end (see Usage below), and you have a working Twitter-like API ready to respond your requests (see API below).

There are many features not implemented yet (followers, content sharing etc). I intend to add these and more features in near future.

If you have any suggestions, feel free to open an issue!

## Usage

For development (ironically, its main use case), we don't even need a running instance of Datomic, since the API uses an in-memory Datomic database.

To run the API:

```bash
$ lein run
```

Configuration is specified at `resources/dev-config.edn`.

To run tests:

```shell script
$ lein test
```

## License

[The MIT License (MIT)](https://choosealicense.com/licenses/mit/)

Copyright © 2020 Kazuki Yokoyama