package echo.jbender;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.fibers.Fiber;
import echo.thrift.EchoService;
import echo.thrift.EchoRequest;
import echo.thrift.EchoResponse;
import com.pinterest.jbender.JBender;
import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.events.recording.HdrHistogramRecorder;
import com.pinterest.jbender.events.recording.LoggingRecorder;
import com.pinterest.jbender.executors.RequestExecutor;
import com.pinterest.jbender.intervals.ConstantIntervalGenerator;
import com.pinterest.jbender.intervals.IntervalGenerator;
import com.pinterest.quasar.thrift.TFiberSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static com.pinterest.jbender.events.recording.Recorder.record;

public class Main {
  static final class EchoRequestExecutor implements RequestExecutor<EchoRequest, EchoResponse> {
    @Override
    public EchoResponse execute(long l, EchoRequest echoRequest) throws SuspendExecution, InterruptedException {
      try {
        TProtocol proto = new TBinaryProtocol(new TFastFramedTransport(TFiberSocket.open(new InetSocketAddress("localhost", 9999))));
        EchoService.Client client = new EchoService.Client(proto);
        return client.echo(echoRequest);
      } catch (Exception ex) {
        LOG.error("failed to echo", ex);
        throw new RuntimeException(ex);
      }
    }
  }

  public static void main(String[] args) throws SuspendExecution, InterruptedException {
    final IntervalGenerator intervalGen = new ConstantIntervalGenerator(10000000);
    final RequestExecutor<EchoRequest, EchoResponse> requestExector = new EchoRequestExecutor();

    final Channel<EchoRequest> requestCh = Channels.newChannel(-1);
    final Channel<TimingEvent<EchoResponse>> eventCh = Channels.newChannel(-1);

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
    record(eventCh, new HdrHistogramRecorder(histogram, 1000000), new LoggingRecorder(LOG));

    JBender.loadTestThroughput(intervalGen, 0, requestCh, requestExector, eventCh);

    histogram.outputPercentileDistribution(System.out, 1000.0);
  }

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);
}
