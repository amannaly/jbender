import co.paralleluniverse.fibers.SuspendExecution;
import com.pinterest.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.nio.reactor.IOReactorException;

/**
 * @author Arjun Mannaly
 */
public class LoadTestingSession {

    private final String startSessionURL;
    private final String endSessionURL;

    private FiberApacheHttpClientRequestExecutor requestExecutor;

    public LoadTestingSession(String hostName) {
        this.startSessionURL = String.format("http://%s:8888/start_session", hostName);
        this.endSessionURL = String.format("http://%s:8888/end_session", hostName);

        try {
            requestExecutor =
                    new FiberApacheHttpClientRequestExecutor<>((res) -> {
                        if (res == null) {
                            throw new AssertionError("Response is null");
                        }
                        final int status = res.getStatusLine().getStatusCode();
                        if (status != 200) {
                            throw new AssertionError("Status is " + status);
                        }
                    }, 5);
        } catch (IOReactorException e) {
            e.printStackTrace();
        }
    }

    public void startSession() {
        try {
            requestExecutor.execute(0L, new HttpGet(startSessionURL));
        } catch (SuspendExecution| InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void endSession() {
        try {
            requestExecutor.execute(0L, new HttpGet(endSessionURL));
        } catch (SuspendExecution| InterruptedException e) {
            e.printStackTrace();
        }
    }
}
