package io.github.ksfikas.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Which Fritz!Box call monitor lines count as an incoming call. */
public class CallMonitorTest {

    @Test
    public void anIncomingCallGivesIdNumberAndLine() {
        CallMonitor.Ring r = CallMonitor.parse("31.10.26 21:05:11;RING;3;0301234567;12345;SIP0;");
        assertNotNull(r);
        assertEquals("3", r.id);
        assertEquals("0301234567", r.number);
        assertEquals("12345", r.called);
    }

    @Test
    public void aHiddenNumberIsEmpty() {
        assertEquals("", CallMonitor.parse("31.10.26 21:05:11;RING;1;;12345;SIP0;").number);
    }

    @Test
    public void everythingElseIsIgnored() {
        assertNull(CallMonitor.parse("31.10.26 21:05:11;CALL;1;0;12345;0301234567;SIP0;"));
        assertNull(CallMonitor.parse("31.10.26 21:05:20;CONNECT;1;1;0301234567;"));
        assertNull(CallMonitor.parse("31.10.26 21:06:00;DISCONNECT;1;40;"));
        assertNull(CallMonitor.parse("garbage"));
        assertNull(CallMonitor.parse(""));
        assertNull(CallMonitor.parse(null));
    }
}
