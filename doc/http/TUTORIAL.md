JBender HTTP Tutorial
=====================

This tutorial walks through the steps to create an HTTP load tester with JBender on a pre-built HTTP server.

### Getting Started

JBender uses [Gradle](http://gradle.org) as a build tool and we're going to use it for our sample load tester as well, so make sure you have it installed. The easiest way to get it on Mac OS X is probably to install [HomeBrew](http://brew.sh/) and then `brew install gradle`, while on Linux there's [LinuxBrew](https://github.com/Homebrew/linuxbrew). Your specific Linux distribution could offer native Gradle packages but they tend to lag behind the most recent version, so it's probably better to brew anyway.

We load test a ready-made server available as the [Comsat Gradle template](https://github.com/puniverse/comsat-gradle-template), so you can just `git clone` it and then run it in a separate terminal window with `gradle wrapper` followed by `./gradlew -Penv=dropwizard run` from the project directory.

Writing a JBender HTTP load test involves using JBender's `FiberApacheHttpClientRequestExecutor`, which is based on [Comsat HTTP client](http://docs.paralleluniverse.co/comsat/#http-clients).

### Creating the load test Gradle project

In your usual sources work root create a `jbender-http-tutorial` directory and view the following `build.gradle` file in it (you won't need to add this, it is already there):

``` groovy
plugins {
    id "us.kirchmeier.capsule" version "1.0-rc1"
}

apply plugin: 'java'

// Target JDK8
sourceCompatibility = 1.8
targetCompatibility = 1.8

group = 'jbendertut'
version = '0.1-SNAPSHOT'

// UTF8 encoding for sources
[compileJava, compileTestJava]*.options*.encoding = "UTF-8"

repositories {
    // Enable this if you want to use locally-built artifacts, e.g. if you have installed jbender locally
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
    // Quasar API
    compile group: "co.paralleluniverse", name: "quasar-core", version: "0.6.3-SNAPSHOT", classifier: "jdk8"

    // Comsat HTTP Client
    compile group: "co.paralleluniverse", name: "comsat-httpclient", version: "0.3.0"

    // HDR histogram
    compile group: 'org.hdrhistogram', name: 'HdrHistogram', version: "2.1.4"

    // JBender API
    compile group: "com.pinterest", name: "jbender", version: "1.0"

    // Logging
    compile group: "org.slf4j", name: "slf4j-api", version: "1.7.12"
    compile group: "org.slf4j", name: "slf4j-simple", version: "1.7.12"

    // Useful to point to the Quasar agent later in JVM flags (and Capsule-building task)
    quasar group: "co.paralleluniverse", name: "quasar-core", version: "0.6.3-SNAPSHOT", classifier: "jdk8"
}

// Task building an handy self-contained load test capsule
task capsule(type: FatCapsule) {
    applicationClass "LoadTest"

    capsuleManifest {
        javaAgents = [configurations.quasar.iterator().next().getName()] // Add "=vdc" to the Quasar agent to trace instrumentation
        jvmArgs = ["-server", "-XX:+TieredCompilation", "-XX:+AggressiveOpts"] // Aggressive optimizations
    }
}

// Gradle JavaExec load test task
task runLoadTest(type: JavaExec) {
    main = "LoadTest"

    classpath = sourceSets.main.runtimeClasspath

    // Aggressive optimizations and Quasar agent
    jvmArgs = ["-server", "-XX:+TieredCompilation", "-XX:+AggressiveOpts", "-javaagent:${configurations.quasar.iterator().next()}"] // Add "=vdc" to the Quasar agent to trace instrumentation

    // Enable this to troubleshoot instrumentation issues
    // systemProperties = ["co.paralleluniverse.fibers.verifyInstrumentation" : "true"]
}
```

This uses [Capsule](https://github.com/puniverse/capsule) to package the load tester, and provides a convenience task named `runLoadTest` that makes it easy to run the load test with gradle (in development). This is an example of what a production build file would look like for a load tester, and we highly recommend the use of Capsule!

## Load Testing

Let's now write a simple load tester with JBender. The next few sections walk through the various parts of the load tester. If you are in a hurry skip to the section "Final Load Tester Program" and just follow the instructions from there. The sample code you copied in the first step already has all this code, so these sections just describe what that code does, and why.

JBender has a very simple loop in which it does the following:

1. Generate an interval (in nanoseconds) and sleep for that amount of time (you have control over the length of these intervals).
2. Fetch the next request from a channel of requests (created by you).
3. Spawn a lightweight thread (fiber) to send the request and wait for the response (and then generate timing information).
4. Repeat until there are no more requests to send.

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

Note that this loop will send 10k requests and then close the channel. Closing the channel notifies the load tester that there will be no more requests, so it can finish waiting for pending requests and then shut itself down. If you fail to close the channel the load tester will wait for a next request forever.

### Request Executor

The next thing we need is a request executor, which takes the requests generated above and sends
them to the service. We will just use JBender's pre-built one and add a response validator:

```
final FiberApacheHttpClientRequestExecutor requestExecutor =
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

### Recorder

The last thing we need is a channel that will output `TimingEvent` objects as the load tester runs. This will let us listen to the load testers progress and record stats. We want this channel to be buffered so that we can run somewhat independently of the load test without slowing it down:

``` java
final Channel<TimingEvent<CloseableHttpResponse>> eventCh = Channels.newChannel(10000);
```

The `TimingEvent` object contains fields that include the interval time between requests, the duration of the request (how long it took to get a response), whether it was an error or a success, how much "overage" time it is experiencing and a few other things (including a field that can be filled in by the request executor).

JBender has a few simple "recorders" that make it easy to do basic things like logging events and generating histograms:

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

Create a directory for the load tester:

``` bash
mkdir -p src/main/java
```

Then create a file named `LoadTest.java` in that directory and add these lines to it:

``` java
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.HdrHistogram.Histogram;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.nio.reactor.IOReactorException;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.pinterest.jbender.JBender;
import com.pinterest.jbender.events.Event;
import com.pinterest.jbender.events.recording.HdrHistogramRecorder;
import com.pinterest.jbender.events.recording.LoggingRecorder;
import com.pinterest.jbender.executors.RequestExecutor;
import com.pinterest.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import com.pinterest.jbender.intervals.ConstantIntervalGenerator;
import com.pinterest.jbender.intervals.IntervalGenerator;
import static com.pinterest.jbender.events.recording.Recorder.record;

/**
 * Sample HTTP benchmark against {@url https://github.com/puniverse/comsat-gradle-template}
 */
public class LoadTest {
  public static void main(final String[] args) throws SuspendExecution, InterruptedException, ExecutionException, IOReactorException, IOException {
    final IntervalGenerator intervalGenerator = new ConstantIntervalGenerator(10000000);
    try (final FiberApacheHttpClientRequestExecutor requestExecutor =
            new FiberApacheHttpClientRequestExecutor<>((res) -> {
              if (res == null) {
                throw new AssertionError("Response is null");
              }
              final int status = res.getStatusLine().getStatusCode();
              if (status != 200) {
                throw new AssertionError("Status is " + status);
              }
            }, 1000000)) {

      final Channel<HttpGet> requestCh = Channels.newChannel(1000);
      final Channel<Event<CloseableHttpResponse>> eventCh = Channels.newChannel(1000);

      // Requests generator
      new Fiber<Void>("req-gen", () -> {
        // Bench handling 1k reqs
        for (int i = 0; i < 1000; ++i) {
          requestCh.send(new HttpGet("http://localhost:8080/hello-world"));
        }

        requestCh.close();
      }).start();

      final Histogram histogram = new Histogram(3600000000L, 3);

      // Event recording, both HistHDR and logging
      record("recorder", eventCh, new HdrHistogramRecorder(histogram), new LoggingRecorder(LOG));

      // Main
      new Fiber<Void>("jbender", () -> {
        JBender.loadTestThroughput(intervalGenerator, requestCh, requestExecutor, eventCh);
      }).start().join();

      histogram.outputPercentileDistribution(System.out, 1000.0);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(LoadTest.class);
}
```

### Run Server and Load Tester

With the `comsat-gradle-template` server running in one terminal window, run the load tester in
another one from the `jbender-http-tutorial` project directory with `gradle wrapper` (only the first time) and then `./gradlew runLoadTest`.

The output of the load test will be the percentile distribution from the histogram.
