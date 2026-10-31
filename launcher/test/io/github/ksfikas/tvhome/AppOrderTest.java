package io.github.ksfikas.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Which apps the phone lists first. */
public class AppOrderTest {

    private static List<AppOrder.Entry> apps(String... pairs) {
        List<AppOrder.Entry> l = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) l.add(new AppOrder.Entry(pairs[i], pairs[i + 1]));
        return l;
    }

    private static String names(List<AppOrder.Entry> l) {
        StringBuilder s = new StringBuilder();
        for (AppOrder.Entry e : l) s.append(e.name).append(e.fav ? "*" : "").append(' ');
        return s.toString().trim();
    }

    @Test
    public void favouritesComeFirstInDockOrderThenTheRestAlphabetically() {
        List<AppOrder.Entry> out = AppOrder.arrange(
                apps("a.zeta", "Zeta", "b.net", "Netflix", "c.alpha", "Alpha", "d.yt", "YouTube"),
                Arrays.asList("d.yt", "b.net"), Collections.emptySet());
        assertEquals("YouTube* Netflix* Alpha Zeta", names(out));
    }

    @Test
    public void dockAppsThatAreNotInstalledAreSkippedAndHiddenOnesLeftOut() {
        List<AppOrder.Entry> out = AppOrder.arrange(
                apps("a.one", "One", "b.two", "Two", "c.three", "Three"),
                Arrays.asList("x.gone", "b.two"), new HashSet<>(Collections.singleton("c.three")));
        assertEquals("Two* One", names(out));
    }

    @Test
    public void noDockMeansAllAlphabeticalAndNoneFavourite() {
        List<AppOrder.Entry> out = AppOrder.arrange(apps("a", "Beta", "b", "alpha"), Collections.emptyList(), Collections.emptySet());
        assertEquals("alpha Beta", names(out));
        assertFalse(out.get(0).fav);
        assertTrue(out.size() == 2);
    }
}
