Neo: libraries for building Java-based web applications
===================================================

[![Build Status](https://travis-ci.org/1and1/neo.svg)](https://travis-ci.org/1and1/neo)

The Neo project contains several libraries that enable writing resilient, high scalable, web-based applications and services. This includes convenience artifacts in context of Java8 as well as web artifacts

Neo considers synchronous programming as well as the asynchronous, reactive-oriented programming approach.

Requires JDK 1.8 or higher

**This project is still on alpha level. Interfaces are subject of change**

Latest release
--------------

Not yet

- 0.1 API Docs:

To add a dependency on Neo using Maven, use the following:

```
<dependency>
  <groupId>net.oneandone.commonlibs</groupId>
  <artifactId>neo-pom</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

Snapshots
---------

Snapshots of Neo built from the `master` branch are available through Maven
using version `0.1-SNAPSHOT`.


Learn about Neo
------------------
Neo includes following **modules**:
- [Collections](neo-collect) - Convenience artefacts to use Guava immutable collections in context of Java8
- [DataReplicator](neo-datareplicator) - Pull-based client lib to replicated uri-addressed resources in a resilient way
- [HTTP Problem support](neo-problem) - Java implementation of the [Draft RFC Problem Details for HTTP APIs support](https://tools.ietf.org/html/draft-ietf-appsawg-http-problem-03)

Links
-----
- [GitHub project]()
- [Issue tracker: report a defect or feature request]()

Internal implementation notes
--------------------

1. Minimal usage of 3rd parts libraries. Neo relies on JSE and partly on JEE. Further more Neo makes usage of Google Guava, Jersey and the Jackson lib.

2. Zero configuration. By default the libraries uses production ready defaults

3. Neo prefers a fluent interface style. Async methods are suffixed with `Async` and return a CompletableFuture instance

4. Neo prefers immutable artifacts. For instance Neo makes use of an "immutable builder pattern". Here, the builder method (typically prefixed by `with`) returns a new immutable instance of the object to build.
