# play26-lettuce

[![Build Status](https://travis-ci.org/simonedeponti/play26-lettuce.svg?branch=master)](https://travis-ci.org/simonedeponti/play26-lettuce)

A Redis cache plugin for Play 2.6+, based on lettuce (https://lettuce.io/).

It does support Scala (2.12 and 2.11) and Java `AsyncCacheApi` and `SyncCacheApi`.

It supports SSL, and therefore is suitable for use with Azure Redis Cache.

## Usage

Add dependency to sbt (along with the resolver to BinTray):

```sbtshell
libraryDependencies += "com.github.simonedeponti" %% "play26-lettuce" % "0.2.2"

resolvers ++= Seq(
  "simonedeponti-bintray" at "https://dl.bintray.com/simonedeponti/maven"
)
```

Enable the module:

```hocon
# Enable redis cache plugin
play.modules.enabled += "com.github.simondeponti.play26lettuce.LettuceModule"
```

Then (optionally) enable Kryo serialization, by adding:

```sbtshell
libraryDependencies += "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.1"
``` 

And then adding in you configuration something like this:

```hocon
akka {
  extensions = ["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]
  actor {
    kryo {
      idstrategy = default
      resolve-subclasses = true
    }
    serializers {
      java = "akka.serialization.JavaSerializer"
      kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
    }
    serialization-bindings {
      "java.io.Serializable" = kryo
      "my.custom.Class" = kryo
    }
  }
}
```

## Configuration

After doing all of the above, one must set the redis endpoint for each cache by specifying `lettuce.$cachename.url`:

```hocon

play.cache {
  defaultCache = "default"
  bindCaches = []
}

lettuce.default.url = "redis://localhost/0"
```

If you need additional caches,
put them into `bindCaches` and then create the corresponding `lettuce.$cacheName.url` config key.

**Never specify the exact same URL for two caches: please use different databases to separate them**

Correct example:

```hocon

play.cache {
  defaultCache = "default"
  bindCaches = ["users"]
}

lettuce.default.url = "redis://localhost/0"
lettuce.users.url = "redis://localhost/1"
```

Wrong example (the two caches might have key conflicts, and will throw an error upon start):

```hocon

play.cache {
  defaultCache = "default"
  bindCaches = ["users"]
}

lettuce.default.url = "redis://localhost/0"
lettuce.users.url = "redis://localhost/0"  # Same URL, everything explodes in a ball of fire.
```


Optionally, for each cache, a timeout can be set for syncronous wrappers:

```hocon
lettuce.default.syncTimeout = 3s
```

## History

### v1.0.0

- Added multiset/multiget support in custom interface (dominics)
- No configuration silently exports no bindings (dominics)
- Better support for multiple caches without using KEYS
  command but mandating separated databases for different caches

### v0.2.3

- Timeout for sync wrappers is configurable

### v0.2.2

- More robust serialization

### v0.2.1

- Cross building scala 2.12 & 2.11

### v0.2.0

- Added support for Scala sync API
- Added support for Java API (sync & async)

### v0.1

- Initial version