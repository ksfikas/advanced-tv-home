package io.github.ksfikas.tvhome;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The order apps appear in on the phone: the dock's favourites first, in the dock's order, then the rest alphabetically; hidden ones left out. */
final class AppOrder {
    private AppOrder() { }

    static final class Entry {
        final String pkg, name;
        boolean fav;
        Entry(String pkg, String name) { this.pkg = pkg; this.name = name; }
    }

    static List<Entry> arrange(List<Entry> installed, List<String> dock, Set<String> hidden) {
        Map<String, Entry> byPkg = new LinkedHashMap<>();
        for (Entry e : installed) if (!hidden.contains(e.pkg)) byPkg.putIfAbsent(e.pkg, e);
        List<Entry> out = new ArrayList<>();
        for (String p : dock) {
            Entry e = byPkg.remove(p);
            if (e != null) { e.fav = true; out.add(e); }
        }
        List<Entry> rest = new ArrayList<>(byPkg.values());
        Collator c = Collator.getInstance();
        Collections.sort(rest, (a, b) -> c.compare(a.name, b.name));
        out.addAll(rest);
        return out;
    }
}
