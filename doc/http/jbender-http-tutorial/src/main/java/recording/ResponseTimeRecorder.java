package recording;

import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.events.recording.Recorder;
import org.slf4j.Logger;

/**
 * @author Arjun Mannaly
 */
public class ResponseTimeRecorder implements Recorder {

    private final Logger log;

    public ResponseTimeRecorder(final Logger l) {
        this.log = l;
    }

    @Override
    public void record(TimingEvent e) {
        long time = (e.durationNanos/1000_000);
        log.info("Response time -> {} ms", time);
    }
}
