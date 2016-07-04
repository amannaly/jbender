import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.pinterest.jbender.JBender;
import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.events.recording.HdrHistogramRecorder;
import recording.ResponseTimeRecorder;
import com.pinterest.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import org.HdrHistogram.Histogram;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.nio.reactor.IOReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static com.pinterest.jbender.events.recording.Recorder.record;

public class ConcurrencyTest {

    public static void main(final String[] args) throws SuspendExecution, InterruptedException, ExecutionException, IOReactorException, IOException {

        String url = String.format("http://%s:8888/?q=russia", args[0]);

        try (final FiberApacheHttpClientRequestExecutor requestExecutor =
                     new FiberApacheHttpClientRequestExecutor<>((res) -> {
                         if (res == null) {
                             throw new AssertionError("Response is null");
                         }
                         final int status = res.getStatusLine().getStatusCode();
                         if (status != 200) {
                             throw new AssertionError("Status is " + status);
                         }
                     }, 10_000)) {

            final Channel<HttpGet> requestCh = Channels.newChannel(-1);
            final Channel<TimingEvent<CloseableHttpResponse>> eventCh = Channels.newChannel(-1);

            // Requests generator
            new Fiber<Void>("req-gen", () -> {
                // Bench handling 1k reqs
                for (int i = 0; i < 1000; ++i) {
                    requestCh.send(new HttpGet(url));
                }

                requestCh.close();
            }).start();

            final Histogram histogram = new Histogram(1000L, 3);

            // Event recording, both HistHDR and logging
            HdrHistogramRecorder hdrHistogramRecorder = new HdrHistogramRecorder(histogram, 1_000_000);
            record(eventCh, hdrHistogramRecorder);

            // Main
            new Fiber<Void>("jbender", () -> {
                JBender.loadTestConcurrency(10, 0, requestCh, requestExecutor, eventCh);
            }).start().join();

            //histogram.outputPercentileDistribution(System.out, 1.0);

            LOG.info("Number of requests: {}", histogram.getTotalCount());
            LOG.info("Number of errors: {}", hdrHistogramRecorder.errorCount);
            LOG.info("Mean response time: {} ms", histogram.getMean());
            LOG.info("95th percentile response time: {} ms", histogram.getValueAtPercentile(95.0));
            LOG.info("99th percentile response time: {} ms", histogram.getValueAtPercentile(99.0));
            LOG.info("Max response time: {} ms", histogram.getMaxValueAsDouble());
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(LoadTest.class);
}
