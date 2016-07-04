package recording;

import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.events.recording.Recorder;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * @author Arjun Mannaly
 */
public class ResponseRecorder implements Recorder {

    private final Logger log;

    public ResponseRecorder(final Logger l) {
        this.log = l;
    }

    @Override
    public void record(TimingEvent e) {
        CloseableHttpResponse response = (CloseableHttpResponse)e.response;

        if (response.getStatusLine().getStatusCode() != 200) {
            log.info("Response Status: {}", response.getStatusLine().getStatusCode());
            return;
        }

        Header contentEncoding = response.getFirstHeader("Content-Encoding");

        Inflater inflater;
        if (contentEncoding != null && contentEncoding.getValue().equals("gzip")) {
            inflater = new Inflater(true);
        }
        else if (contentEncoding != null && contentEncoding.getValue().equals("deflate")) {
            inflater = new Inflater();
        }
        else {
            inflater = new Inflater(true);
        }

        try (final BufferedInputStream inputStream = new BufferedInputStream(response.getEntity().getContent())) {
            byte[] ip = new byte[(int)response.getEntity().getContentLength()];
            inputStream.read(ip);
            byte[] op = new byte[ip.length * 3];
            inflater.setInput(ip, 0, ip.length);
            int length = inflater.inflate(op);
            inflater.end();
            log.info("Response: {}", new String(op, 0, length));

        } catch (IOException | DataFormatException ex){
            ex.printStackTrace();
        }
    }
}
