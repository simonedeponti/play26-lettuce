# play26-lettuce

A Redis cache plugin for Play 2.6+, based on lettuce (https://lettuce.io/).

It does support only the `AsyncCacheApi` for the moment.

It supports SSL, and therefore is suitable for use with Azure Redis Cache.

## Usage

Enable the module:

```hocon
# Enable redis cache plugin
play.modules.enabled += "com.github.simondeponti.play26lettuce.LettuceModule"
```

Then (optionally) enable Kryo serialization, by adding:

```sbtshell
libraryDependencies += "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.0"
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
      "java.util.LinkedHashMap" = kryo
      "my.custom.Class" = kryo
    }
  }
}
```