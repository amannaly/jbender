import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.httpclient.FiberHttpClientBuilder;
import co.paralleluniverse.strands.SuspendableCallable;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @author Arjun Mannaly
 */
public class LoadTestingSession {

    private final String startSessionURL;
    private final String endSessionURL;

    private CloseableHttpClient client;

    public LoadTestingSession(String hostName) {
        this.startSessionURL = String.format("http://%s:8888/start_session", hostName);
        this.endSessionURL = String.format("http://%s:8888/end_session", hostName);

        client = FiberHttpClientBuilder.create().build();
    }

    public void startSession() {
        try {
            new Fiber<CloseableHttpResponse>(() -> {
                try {
                    client.execute(new HttpGet(startSessionURL));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start().get();
        } catch (ExecutionException| InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void endSession() {
        try {
            new Fiber<CloseableHttpResponse>(() -> {
                try {
                    client.execute(new HttpGet(endSessionURL));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start().get();
        } catch (ExecutionException| InterruptedException e) {
            e.printStackTrace();
        }
    }
}
