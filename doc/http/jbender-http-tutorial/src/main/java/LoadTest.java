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
