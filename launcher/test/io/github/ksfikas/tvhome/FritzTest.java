package io.github.ksfikas.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.Map;

/** Reading a Fritz!Box phonebook export and matching numbers to names. */
public class FritzTest {

    private static final String BOOK = "<?xml version=\"1.0\"?><phonebooks><phonebook>"
            + "<contact><category>0</category><person><realName>Maria Papadopoulou</realName></person>"
            + "<telephony><number type=\"home\" prio=\"1\">+30 210 1234567</number>"
            + "<number type=\"mobile\">0030 6912345678</number></telephony></contact>"
            + "<contact><person><realName>Reception</realName></person>"
            + "<telephony><number type=\"intern\">**610</number></telephony></contact>"
            + "<contact><telephony><number>2310999888</number></telephony></contact>"
            + "</phonebook></phonebooks>";

    private static Map<String, String> read(String xml) throws Exception {
        XmlPullParser p = new KXmlParser();
        p.setInput(new StringReader(xml));
        return Fritz.parse(p);
    }

    @Test
    public void everyNumberOfAContactMapsToTheName() throws Exception {
        Map<String, String> b = read(BOOK);
        assertEquals("Maria Papadopoulou", b.get(Fritz.key("2101234567")));
        assertEquals("Maria Papadopoulou", b.get(Fritz.key("6912345678")));
    }

    @Test
    public void internalNumbersAndNamelessContactsAreLeftOut() throws Exception {
        Map<String, String> b = read(BOOK);
        assertNull(b.get(Fritz.key("**610")));
        assertNull(b.get(Fritz.key("2310999888")));
        assertEquals(2, b.size());
    }

    @Test
    public void numbersMatchWhateverTheirFormat() {
        String k = Fritz.key("2101234567");
        assertEquals(k, Fritz.key("+30 210 1234567"));
        assertEquals(k, Fritz.key("0030 210-1234567"));
        assertEquals(k, Fritz.key("02101234567"));
        assertEquals("", Fritz.key(null));
    }
}
