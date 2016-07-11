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
import java.util.concurrent.atomic.AtomicInteger;

import static com.pinterest.jbender.events.recording.Recorder.record;

public class ConcurrencyTest {

    private static final int noOfReq = 1_000_000;
    private static final int warmUpReq = 10_000;
    private static final int concurrency = 1000;
    private static final int queueSize = 100_000;

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

            final Channel<HttpGet> requestCh = Channels.newChannel(queueSize);
            final Channel<TimingEvent<CloseableHttpResponse>> eventCh = Channels.newChannel(queueSize);

            // Requests generator
            new Fiber<Void>("req-gen", () -> {
                // Bench handling 1k reqs
                for (int i = 0; i < noOfReq; ++i) {
                    requestCh.send(new HttpGet(url));
                }

                requestCh.close();
            }).start();

            final Histogram histogram = new Histogram(10000L, 3);

            // Event recording, both HistHDR and logging
            HdrHistogramRecorder hdrHistogramRecorder = new HdrHistogramRecorder(histogram, 1_000_000);
            record(eventCh, warmUpReq, hdrHistogramRecorder);

            // Main
            new Fiber<Void>("jbender", () -> {
                // with 5000 warmup requests.
                JBender.loadTestConcurrency(concurrency, warmUpReq, requestCh, requestExecutor, eventCh);
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
