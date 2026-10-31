package io.github.ksfikas.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** What a message from outside turns into. */
public class NoticeTest {

    @Test
    public void secondsStayBetweenThreeAndThirty() {
        assertEquals(8, Notice.seconds(null));
        assertEquals(8, Notice.seconds("soon"));
        assertEquals(3, Notice.seconds("0"));
        assertEquals(12, Notice.seconds(" 12 "));
        assertEquals(30, Notice.seconds("600"));
    }

    @Test
    public void titleAndTextJoinAndBlanksVanish() {
        assertEquals("Doorbell: Someone is at the door", Notice.line("Doorbell", "Someone is at the door"));
        assertEquals("Doorbell", Notice.line("Doorbell", ""));
        assertEquals("Motion", Notice.line(null, " Motion "));
        assertEquals("", Notice.line(" ", null));
    }

    @Test
    public void newlinesFlattenAndLongTextIsCut() {
        assertEquals("a b", Notice.line("", "a\r\nb"));
        String longText = new String(new char[500]).replace('\0', 'x');
        assertEquals(240, Notice.line("", longText).length());
    }
}
