JBender
=======

JBender makes it easy to build load testers for services using protocols like HTTP and Thrift (and
many others). JBender provides a library of flexible, easy-to-use primitives that can be combined
(with plain Java code) to build high performance load testers customized to any use case, and that
can evolve with your service over time.

JBender provides two different approaches to load testing. The first, `JBender.loadTestThroughput`
gives the tester control over the throughput (queries per second), but not over the concurrency
(number of active connections). This approach is well suited for services that are open to the
Internet, like web services, and the backend services to which they speak. The primary benefit of
this approach is that the load tester will maintain the requested throughput, even if the service
is struggling (or failing) to support it. As a result, a much more clear picture of how the target
service responds to load is provided.

The second approach, `JBender.loadTestConcurrency`, gives the tester control over the concurrency
(number of active connections), but not the throughput (queries per second). This approach is well
suited to applications that need to test a large number of concurrent, inactive connections, like
chat servers. This approach is not suitable for testing request latency, as the load tester will
slow down to match the server (because it cannot start more connections than requested).

That JBender is a library makes it flexible and easy to extend, but means it takes longer to create
an initial load tester. As a result, we've focused on creating easy-to-follow tutorials.

## Quasar

The JBender library makes heavy use of [Quasar](http://www.paralleluniverse.co/quasar/), a library from [Parallel Universe](http://www.paralleluniverse.co/) which adds lightweight threads (called "fibers") to the JVM.

The tutorials, linked below, will help you get up and running with Quasar and JBender, and provide some background on how Quasar works (hint: it's very straightforward to use Quasar!). Here are some links for more information:

* [Quasar's Documentation](http://docs.paralleluniverse.co/quasar/).
* [Quasar's Github Page](https://github.com/puniverse/quasar).
* [The Parallel Universe Blog](http://blog.paralleluniverse.co/).

## Getting Started

The easiest way to get started with JBender is to use one of the tutorials:

* [Thrift](https://github.com/cgordon/jbender/blob/master/doc/thrift/TUTORIAL.md)
* [HTTP](https://github.com/cgordon/jbender/blob/master/doc/http/TUTORIAL.md)

## Performance

The Linux TCP stack for a default server installation is usually not tuned to high
throughput servers or load testers. After some experimentation, we have settled on adding these
lines to `/etc/sysctl.conf`, after which you can run `sysctl -p` to load them (although it is
recommended to restart your host at this point to make sure these take effect).

```
# /etc/sysctl.conf
# Increase system file descriptor limit
fs.file-max = 100000

# Increase ephermeral IP ports
net.ipv4.ip_local_port_range = 1024 65000

# Increase Linux autotuning TCP buffer limits
# Set max to 16MB for 1GE and 32M (33554432) or 54M (56623104) for 10GE
# Don't set tcp_mem itself! Let the kernel scale it based on RAM.
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.core.rmem_default = 16777216
net.core.wmem_default = 16777216
net.core.optmem_max = 40960
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216

# Make room for more TIME_WAIT sockets due to more clients,
# and allow them to be reused if we run out of sockets
# Also increase the max packet backlog
net.core.netdev_max_backlog = 100000
net.ipv4.tcp_max_syn_backlog = 100000
net.ipv4.tcp_max_tw_buckets = 2000000
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_tw_recycle = 1
net.ipv4.tcp_fin_timeout = 10

# Disable TCP slow start on idle connections
net.ipv4.tcp_slow_start_after_idle = 0

# From https://people.redhat.com/alikins/system_tuning.html
net.ipv4.tcp_sack = 0
net.ipv4.tcp_timestamps = 1
```

This is a slightly modified version of advice taken from this source:
http://www.nateware.com/linux-network-tuning-for-2013.html#.VBjahC5dVyE

In addition, it helps to increase the open file limit with something like:

```ulimit -n 100000```

## What Is Missing

JBender does not provide any support for sending load from more than one machine. If you need to
send more load than a single machine can handle, or you need the requests to come from multiple
physical hosts (or different networks, or whatever), you currently have to write your own tools. In
addition, the histogram implementation used by JBender is inefficient to send over the network,
unlike q-digest or t-digest, which we hope to implement in the future.

JBender does not provide any visualization tools, and has a relatively simple set of measurements,
including a customizable histogram of latencies, an error rate and some other summary statistics.
JBender does provide a complete log of everything that happens during a load test, so you can use
existing tools to graph any aspect of that data, but nothing in JBender makes that easier right now.

JBender only provides helper functions for HTTP and Thrift currently, because that is all we use
internally at Pinterest.

The load testers we have written internally with JBender have a lot of common command line arguments,
but we haven't finalized a set to share as part of the library.

## Comparison to Other Load Testers

#### Bender

JBender is a port of Bender to the JVM platform with [Quasar](http://docs.paralleluniverse.co/quasar/)
lightweight threads (_fibers_) and channels.

#### JMeter

JMeter provides a GUI to configure and run load tests, and can also be configured via XML (really,
really not recommended by hand!) and run from the command line. JMeter's is not a good approach to
load testing services (see the JBender docs and the Iago philosophy for more details on why that is).
It isn't easy to extend JMeter to handle new protocols, so it doesn't have support for Thrift or
Protobuf. It is relatively easy to extend other parts of JMeter by writing Java code, however, and
the GUI makes it easy to plug all the pieces together.

#### Iago

Iago is Twitter's load testing library and it is the inspiration for JBender's `loadTestThroughput`
function. Iago is a Scala library written on top of Netty and the Twitter Finagle libraries. As a
result, Iago is powerful, but difficult to understand, extend and configure. It was frustration with
making Iago work that led to the creation of JBender.

#### The Grinder

The Grinder has the same load testing approach as JMeter, but allows scripting via Jython, which
makes it more flexible and extensible. The Grinder uses threads, which limits the concurrency at
which it can work, and makes it hard to implement things like JBender's `loadTestThroughput` function.
The Grinder does have support for conveniently running distributed load tests.

## Copyright

Copyright 2015 Pinterest.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Attribution

JBender includes open source from the following sources:

* Apache Thrift Libraries. Copyright 2014 Apache Software Foundation. Licensed under the Apache License v2.0 (http://www.apache.org/licenses/).
* Quasar Libraries. Copyright 2014 Parallel Universe. Licensed under the GNU Lesser General Public License (http://www.gnu.org/licenses/lgpl.html).
