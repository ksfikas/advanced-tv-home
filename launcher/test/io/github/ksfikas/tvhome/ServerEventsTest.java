package io.github.ksfikas.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What the server's change messages mean to the app. */
public class ServerEventsTest {

    @Test
    public void aRecordingChangeIsAChange() throws Exception {
        assertTrue(ServerEvents.parse("{\"boxid\":\"x\",\"messages\":[{\"change\":[\"abc\"],\"notificationClass\":\"dvrentry\"}]}").changed);
    }

    @Test
    public void aGuideMarkerUpdateIsAChange() throws Exception {
        assertTrue(ServerEvents.parse("{\"messages\":[{\"dvr_update\":[\"9694\"],\"notificationClass\":\"epg\"}]}").changed);
    }

    @Test
    public void statusAndLogChatterIsNot() throws Exception {
        ServerEvents.Result r = ServerEvents.parse("{\"messages\":[{\"notificationClass\":\"diskspaceUpdate\",\"freediskspace\":5},"
                + "{\"notificationClass\":\"logmessage\",\"logtxt\":\"x\"},{\"reload\":1,\"notificationClass\":\"subscriptions\"}]}");
        assertFalse(r.changed);
        assertNull(r.signal);
        assertFalse(ServerEvents.parse("{\"boxid\":\"x\",\"messages\":[]}").changed);
    }

    @Test
    public void aTunerReportGivesSignalSnrAndRate() throws Exception {
        ServerEvents.Result r = ServerEvents.parse("{\"messages\":[{\"notificationClass\":\"input_status\",\"input\":\"Sony CXD2880 #0 : DVB-T #0\","
                + "\"subs\":1,\"signal\":-37374,\"signal_scale\":2,\"snr\":29330,\"snr_scale\":2,\"ber\":0,\"unc\":0,\"cc\":2,\"bps\":7294400}]}");
        assertNotNull(r.signal);
        Tvh.Status s = r.signal.toStatus();
        assertEquals("Sony CXD2880 · DVB-T", s.tuner);
        assertEquals("-37.4 dBm", s.signal);
        assertEquals("29.3 dB", s.snr);
        assertEquals("BER 0 · UNC 0 · CC 2", s.errors);
        assertEquals(7294400L, s.bitrate);
    }

    @Test
    public void anIdleTunerOrAReloadHintIsIgnored() throws Exception {
        assertNull(ServerEvents.parse("{\"messages\":[{\"notificationClass\":\"input_status\",\"subs\":0,\"snr\":1}]}").signal);
        assertNull(ServerEvents.parse("{\"messages\":[{\"reload\":1,\"notificationClass\":\"input_status\"}]}").signal);
    }
}
