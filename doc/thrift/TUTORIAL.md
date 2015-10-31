JBender Thrift Tutorial
=======================

This tutorial walks through the steps to create a simple "Echo" Thrift service with a load tester. The complete
project can be found at [jbender-echo](https://github.com/cgordon/jbender-echo) TODO: open it up or include it
here.

## Getting Started

You will need a copy of Thrift installed on your machine, which will allow you to run the `thrift`
command. You can follow the "Getting Started" instructions on the [Apache Thrift](https://thrift.apache.org/) 
page to download and install it. The easiest way to get it on Mac OS X is probably to install
[HomeBrew](http://brew.sh/) and then `brew install gradle` though.

JBender uses [Gradle](http://gradle.org) as a build tool and we're going to use it for our sample load
tester as well, so make sure you have it installed. The easiest way to get it on Mac OS X is still
`brew install gradle`, while on Linux there's [LinuxBrew](https://github.com/Homebrew/linuxbrew).
Your specific Linux distribution could offer native Gradle packages but they tend to lag behind the
most recent version, so it's probably better to brew anyway.

### Creating the load test Gradle project

In your usual sources work root create a `jbender-thrift-tutorial` directory and the following `build.gradle` file in it:

``` groovy
// Gradle Thrift plugin
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath "co.tomlee.gradle.plugins:gradle-thrift-plugin:0.0.4"
    }
}

// Capsule plugin
plugins {
    id "us.kirchmeier.capsule" version "1.0-rc1"
}

apply plugin: 'java'
apply plugin: 'thrift'

// Target JDK8
sourceCompatibility = 1.8
targetCompatibility = 1.8

group = 'jbendertut'
version = '0.1-SNAPSHOT'

// UTF8 encoding for sources
[compileJava, compileTestJava]*.options*.encoding = "UTF-8"

repositories {
    // Enable this if you want to use locally-built artifacts
    mavenLocal()

    // This allows using published Quasar snapshots
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots"
    }

    mavenCentral()
}

configurations {
    quasar
}

dependencies {
    // Thrift API
    compile group: "org.apache.thrift", name: "libthrift", version: "0.9.1"

    // Quasar-Thrift server
    compile group: "com.pinterest", name: "quasar-thrift", version: "0.1-SNAPSHOT"

    // Quasar API
    compile group: "co.paralleluniverse", name: "quasar-core", version: "0.6.3-SNAPSHOT", classifier: "jdk8"

    // JBender API
    compile group: "com.pinterest", name: "jbender", version: "1.0"

    // Logging
    compile group: "org.slf4j", name: "slf4j-api", version: "1.7.12"
    compile group: "org.slf4j", name: "slf4j-simple", version: "1.7.12"

    // Useful to point to the Quasar agent later in JVM flags (and Capsule-building task)
    quasar group: "co.paralleluniverse", name: "quasar-core", version: "0.6.3-SNAPSHOT", classifier: "jdk8"
}

// Thrift generators
generateThriftSource {
    generators { java {} }
}

// Automatically find Quasar suspendables in Thrift-generated code
classes {
    doFirst {
        ant.taskdef(name: 'scanSuspendables',
                classname: 'co.paralleluniverse.fibers.instrument.SuspendablesScanner',
                classpath: "build/classes/main:build/resources/main:${configurations.runtime.asPath}")
        ant.scanSuspendables(
                auto: true,
                suspendablesFile: "$sourceSets.main.output.resourcesDir/META-INF/suspendables",
                supersFile: "$sourceSets.main.output.resourcesDir/META-INF/suspendable-supers",
                append: true) {
            fileset(dir: sourceSets.main.output.classesDir)
        }
    }
}

// Task building an handy self-contained server capsule
task serverCapsule(type: FatCapsule) {
    applicationClass "com.pinterest.echo.jbender.server.Main"

    capsuleManifest {
        javaAgents = [configurations.quasar.iterator().next().getName()]
        // Aggressive optimizations
        jvmArgs = ["-server", "-XX:+TieredCompilation", "-XX:+AggressiveOpts"]
    }
}

// Task building an handy self-contained load test capsule
task capsule(type: FatCapsule) {
    applicationClass "com.pinterest.echo.jbender.Main"

    capsuleManifest {
        javaAgents = [configurations.quasar.iterator().next().getName()]
        // Aggressive optimizations
        jvmArgs = ["-server", "-XX:+TieredCompilation", "-XX:+AggressiveOpts"]
    }
}

// Gradle JavaExec load test task
task runLoadTest(type: JavaExec) {
    main = "com.pinterest.echo.jbender.Main"

    classpath = sourceSets.main.runtimeClasspath

    // Aggressive optimizations and Quasar agent
    jvmArgs = ["-server", "-XX:+TieredCompilation", "-XX:+AggressiveOpts", "-javaagent:${configurations.quasar.iterator().next()}"] // Add "=vdc" to the Quasar agent to trace instrumentation

    // Enable this to troubleshoot instrumentation issues
    // systemProperties = ["co.paralleluniverse.fibers.verifyInstrumentation" : "true"]
}

// Gradle JavaExec server task
task runServer(type: JavaExec) {
    main = "com.pinterest.echo.jbender.server.Main"

    classpath = sourceSets.main.runtimeClasspath

    // Aggressive optimizations and Quasar agent
    jvmArgs = ["-server", "-XX:+TieredCompilation", "-XX:+AggressiveOpts", "-javaagent:${configurations.quasar.iterator().next()}"] // Add "=vdc" to the Quasar agent to trace instrumentation

    // Enable this to troubleshoot instrumentation issues
    // systemProperties = ["co.paralleluniverse.fibers.verifyInstrumentation" : "true"]
}
```


## Writing the Thrift Server and Client

This section will walk through the creation of a Thrift client and server, which we will use to
test JBender in the following section.

### Thrift Service Definition and Code Generation

Now create a file named `src/main/thrift/echo.thrift` and add these lines to it using your
text editor:

``` thrift
namespace java com.pinterest.echo.thrift

struct EchoRequest {
    1: optional string message;
}

struct EchoResponse {
    2: optional string message;
}

service EchoService {
    EchoResponse echo(1: EchoRequest request);
}
```

This defines a Thrift service with one API endpoint named `echo` that takes a `EchoRequest` and
returns a `EchoResponse`.

### Thrift Service Implementation

Now we will create a simple service definition that just echoes the request string to the response.
First, create a new directory:

```
mkdir -p src/main/java/com/pinterest/echo/jbender/server
```

Then create a file named `EchoServiceImpl.java` in that directory and add these lines to it:

``` java
package com.pinterest.echo.jbender.server;

import co.paralleluniverse.fibers.Suspendable;
import com.pinterest.echo.thrift.EchoRequest;
import com.pinterest.echo.thrift.EchoResponse;
import com.pinterest.echo.thrift.EchoService;
import org.apache.thrift.TException;

public class EchoServiceImpl implements EchoService.Iface {
  @Override
  @Suspendable
  public EchoResponse echo(EchoRequest request) throws TException {
    return new EchoResponse().setMessage(request.getMessage());
  }
}
```

Finally create a file named `Main.java`:

``` java
package com.pinterest.echo.jbender.server;

import co.paralleluniverse.fibers.Suspendable;
import com.pinterest.echo.thrift.EchoService;
import com.pinterest.quasar.thrift.TFiberServer;
import com.pinterest.quasar.thrift.TFiberServerSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TFastFramedTransport;

import java.net.InetSocketAddress;

public class Main {
  @Suspendable
  public static void main(String[] args) throws Exception {
    EchoService.Processor<EchoService.Iface> processor =
        new EchoService.Processor<EchoService.Iface>(new EchoServiceImpl());
    TFiberServerSocket trans = new TFiberServerSocket(new InetSocketAddress(9999));
    TFiberServer.Args targs = new TFiberServer.Args(trans, processor)
        .protocolFactory(new TBinaryProtocol.Factory())
        .transportFactory(new TFastFramedTransport.Factory());
    TFiberServer server = new TFiberServer(targs);
    server.serve();
    server.join();
  }
}
```


## Load Testing

Let's now write a simple load tester with JBender. This section The next few sections walk through
the various parts of the load tester. If you are in a hurry skip to the section "Final Load Tester Program"
and just follow the instructions from there.

### Intervals

The first thing we need is a function to generate intervals (in nanoseconds) between executing
requests. The JBender library comes with some predefined intervals: a uniform distribution
(always wait the same amount of time between each request) and an exponential distribution. In this
case we will use the exponential distribution, which means our server will experience load as
generated by a [Poisson process](http://en.wikipedia.org/wiki/Poisson_process), which is fairly
typical of server workloads on the Internet (with the usual caveats that every service is a special
snowflake, etc, etc). We get the interval function with this code:

``` java
final IntervalGenerator intervalGenerator = new ConstantIntervalGenerator(qps);
```

Where `qps` is our desired throughput measured in queries per second. It is also the reciprocal of
the mean value of the exponential distribution used to generate the request arrival times (see the
wikipedia article above). In practice this means you will see an average QPS that fluctuates around
the target QPS (with less fluctuation as you increase the time interval over which you are
averaging).

### Request Generator

The second thing we need is a channel of requests to send to the HTTP server. When an interval has
been generated and JBender is ready to send the request, it pulls the next request from this channel
and spawns a Quasar _fiber_ (lightweight thread) to send the request to the server. This code creates
and starts a simple synthetic Apache HTTP Client's `HttpGet` request generator to the "Hello World"
server endpoint:

``` java
new Fiber<Void>("message-producer", () -> {
  // Bench handling 10k reqs
  for (int i = 0; i < 10000; ++i) {
    requestCh.send(new HttpGet("http://localhost:8080/hello-world"));
  }

  requestCh.close();
  return null;
}).start();
```

### Request Executor

The next thing we need is a request executor, which takes the requests generated above and sends
them to the service. We will just use JBender's pre-built one and add a response validator:

```
final RequestExecutor<HttpGet, CloseableHttpResponse> requestExecutor =
  new FiberApacheHttpClientRequestExecutor<>((res) -> {
    if (res == null) {
      throw new AssertionError("Response is null");
    }
    final int status = res.getStatusLine().getStatusCode();
    if (status != 200) {
      throw new AssertionError("Status " + status + " is not 200");
    }
  }, 1000000);
```

This validates that the response has actually been produced ans has a HTTP 200 status code.

### Recording Results

The last thing we need is a channel that will output events as the load tester runs. This will let
us listen to the load testers progress and record stats. We want this channel to be buffered so that
we can run somewhat independently of the load test without slowing it down:

``` java
final Channel<Event<CloseableHttpResponse>> eventCh = Channels.newChannel(10000);
```

The `JBender.loadTestThroughput` function will send there events for things like how long it waits
between requests, how much overage it is currently experiencing, and when requests start and end,
how long they took and whether or not they had errors. That raw event stream makes it possible to
analyze the results of a load test. JBender has a couple simple "recorders" that provide basic
functionality for result analysis:

* `LoggingRecorder` creates a recorder that takes a `Logger` and outputs each event.
* `NewHistogramRecorder` records request latencies on a [`org.HdrHistogram.Histogram`](https://github.com/HdrHistogram/HdrHistogram).

You can combine recorders using the `Recorder.record` function, so you can both log events and manage a
histogram using code like this:

```
final Logger LOG = LoggerFactory.getLogger(LoadTest.class);
final Histogram histogram = new Histogram(3600000000L, 3);
record("recorder", eventCh, new HdrHistogramRecorder(histogram), new LoggingRecorder(LOG));
```

The histogram takes two arguments: the maximum expected value and the number of precision digits and will
adjust automatically to record latencies both efficiently and with high-definition buckets.

It is relatively easy to build recorders, or to just process the events from the channel yourself:
see the JBender documentation for more details on what events can be sent, and what data they
contain.

### Final Load Tester Program

Then create a file named `src/main/java/com/pinterest/echo/jbender/Main.java`:

``` java
package com.pinterest.echo.jbender;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.pinterest.echo.thrift.EchoRequest;
import com.pinterest.echo.thrift.EchoResponse;
import com.pinterest.jbender.JBender;
import com.pinterest.jbender.events.Event;
import com.pinterest.jbender.events.recording.HdrHistogramRecorder;
import com.pinterest.jbender.events.recording.LoggingRecorder;
import com.pinterest.jbender.executors.RequestExecutor;
import com.pinterest.jbender.intervals.ConstantIntervalGenerator;
import com.pinterest.jbender.intervals.IntervalGenerator;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.pinterest.jbender.events.recording.Recorder.record;

public class Main {
  public static void main(String[] args) throws SuspendExecution, InterruptedException {
    final IntervalGenerator intervalGen = new ConstantIntervalGenerator(10000000);
    final RequestExecutor<EchoRequest, EchoResponse> requestExector = new EchoRequestExecutor();

    final Channel<EchoRequest> requestCh = Channels.newChannel(-1);
    final Channel<Event<EchoResponse>> eventCh = Channels.newChannel(-1);

    // Requests generator
    new Fiber<Void>("req-gen", () -> {
      for (int i=0; i < 1000; ++i) {
        final EchoRequest req = new EchoRequest();
        req.setMessage("foo");
        requestCh.send(req);
      }

      requestCh.close();
    }).start();

    final Histogram histogram = new Histogram(3600000000L, 3);
    // Event recording, both HistHDR and logging
    record("recorder", eventCh, new HdrHistogramRecorder(histogram), new LoggingRecorder(LOG));

    JBender.loadTestThroughput(intervalGen, requestCh, requestExector, eventCh);

    histogram.outputPercentileDistribution(System.out, 1000.0);
  }

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);
}
```

Also create an `src/main/java/com/pinterest/echo/jbender/EchoRequestExecutor.java` file holding the executor:

``` java
package com.pinterest.echo.jbender;

import co.paralleluniverse.fibers.SuspendExecution;
import com.pinterest.echo.thrift.EchoRequest;
import com.pinterest.echo.thrift.EchoResponse;
import com.pinterest.echo.thrift.EchoService;
import com.pinterest.jbender.executors.RequestExecutor;
import com.pinterest.quasar.thrift.TFiberSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class EchoRequestExecutor implements RequestExecutor<EchoRequest, EchoResponse> {
  @Override
  public EchoResponse execute(long l, EchoRequest echoRequest) throws SuspendExecution, InterruptedException {
    try {
//      TProtocol proto = new TBinaryProtocol(new TFastFramedTransport(TFiberSocket.open(new InetSocketAddress("localhost", 9999), 10000, TimeUnit.MILLISECONDS)));
      TProtocol proto = new TBinaryProtocol(new TFastFramedTransport(TFiberSocket.open(new InetSocketAddress("localhost", 9999))));
      EchoService.Client client = new EchoService.Client(proto);
      return client.echo(echoRequest);
    } catch (Exception ex) {
      LOG.error("failed to echo", ex);
      throw new RuntimeException(ex);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(EchoRequestExecutor.class);
}
```

### Run Server and Load Tester

With `./gradlew runServer` running in one terminal window, run the load tester in
another one with `./gradlew runLoadTest`.

The output of the load test will be the percentile distribution from the histogram.
