package io.github.ksfikas.tvhome;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * The Fritz!Box phonebook, so an incoming call can show a name and not only a number. Reads it over the
 * router's TR-064 interface (port 49000, HTTP Digest login with a Fritz!Box user that may use phone
 * features). The login stays on this box (never in a settings backup) and is only ever sent to the router.
 */
final class Fritz {
    private Fritz() { }

    private static final String USER = "fritz_user", PASS = "fritz_pass", HOST = "fritz_host";
    private static final String SERVICE = "urn:dslforum-org:service:X_AVM-DE_OnTel:1", CONTROL = "/upnp/control/x_contact";
    private static final long KEEP_MS = 6L * 3600 * 1000, RETRY_MS = 10L * 60 * 1000;

    /** The router's address: a saved one, else this network's gateway (works even where the name fritz.box does not resolve), else fritz.box. */
    static String host(Context c) {
        String saved = Tvh.prefs(c).getString(HOST, "");
        if (!saved.isEmpty()) return saved;
        try {
            WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int g = w.getDhcpInfo().gateway;
            if (g != 0) return (g & 0xff) + "." + ((g >> 8) & 0xff) + "." + ((g >> 16) & 0xff) + "." + ((g >> 24) & 0xff);
        } catch (RuntimeException ignored) { }
        return "fritz.box";
    }

    static String user(Context c) { return Tvh.prefs(c).getString(USER, ""); }

    private static String pass(Context c) { return Tvh.prefs(c).getString(PASS, ""); }

    static boolean hasLogin(Context c) { return !user(c).isEmpty(); }

    static synchronized void saveLogin(Context c, String user, String pass) {
        Tvh.prefs(c).edit().putString(USER, user.trim()).putString(PASS, pass).apply();
        book = null;
        failedAt = 0;
    }

    // ---------------------------------------------------------------- phonebook text

    /** Phone numbers compare by their last ten digits, so +30 210 1234567, 2101234567 and 02101234567 are one number. */
    static String key(String number) {
        if (number == null) return "";
        String d = number.replaceAll("\\D", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /** Every number in a Fritz!Box phonebook export, mapped to the contact's name. */
    static Map<String, String> parse(XmlPullParser p) throws XmlPullParserException, IOException {
        Map<String, String> out = new HashMap<>();
        String name = null, tag = null;
        List<String> numbers = new ArrayList<>();
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e == XmlPullParser.START_TAG) {
                tag = p.getName();
                if (tag.equals("contact")) { name = null; numbers.clear(); }
            } else if (e == XmlPullParser.TEXT && tag != null) {
                String t = p.getText().trim();
                if (t.isEmpty()) continue;
                if (tag.equals("realName") && name == null) name = t;
                else if (tag.equals("number")) numbers.add(t);
            } else if (e == XmlPullParser.END_TAG) {
                tag = null;
                if (p.getName().equals("contact") && name != null) {
                    for (String n : numbers) {
                        String k = key(n);
                        if (k.length() >= 5) out.putIfAbsent(k, name);
                    }
                }
            }
        }
        return out;
    }

    static Map<String, String> parse(String xml) throws XmlPullParserException, IOException {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml));
        return parse(p);
    }

    // ---------------------------------------------------------------- loading

    private static Map<String, String> book;
    private static long loadedAt, failedAt;

    /** The contact's name for this number, or null (not in the phonebook, no login, or the router did not answer). Blocks: call off the main thread. */
    static String nameFor(Context c, String number) {
        if (!hasLogin(c) || key(number).length() < 5) return null;
        Map<String, String> b;
        synchronized (Fritz.class) {
            long now = System.currentTimeMillis();
            if (book == null || now - loadedAt > KEEP_MS) {
                if (book == null && now - failedAt < RETRY_MS) return null;
                try {
                    book = load(c);
                    loadedAt = now;
                } catch (IOException | XmlPullParserException ex) {
                    failedAt = now;
                    if (book == null) return null; // an old copy is better than none
                }
            }
            b = book;
        }
        return b.get(key(number));
    }

    /** Reads every phonebook from the router; throws with a readable reason. Also the login check in the setup dialog. */
    static Map<String, String> load(Context c) throws IOException, XmlPullParserException {
        Map<String, String> all = new HashMap<>();
        String ids = tag(soap(c, "GetPhonebookList", ""), "NewPhonebookList");
        if (ids == null || ids.trim().isEmpty()) throw new IOException(c.getString(R.string.fritz_err_none));
        for (String id : ids.split(",")) {
            String url = tag(soap(c, "GetPhonebook", "<NewPhonebookID>" + id.trim() + "</NewPhonebookID>"), "NewPhonebookURL");
            if (url == null) continue;
            all.putAll(parse(new String(fetch(c, url.trim()), StandardCharsets.UTF_8)));
        }
        return all;
    }

    // ---------------------------------------------------------------- the router

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + ">(.*?)</" + name + ">", Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).replace("&amp;", "&") : null;
    }

    private static final Pattern PARAM = Pattern.compile("(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))");

    /** One TR-064 action: POST, answer the router's Digest challenge, return the reply body. */
    private static String soap(Context c, String action, String args) throws IOException {
        String body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + action + " xmlns:u=\"" + SERVICE + "\">"
                + args + "</u:" + action + "></s:Body></s:Envelope>";
        String url = "http://" + host(c) + ":49000" + CONTROL;
        String auth = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection con = open(url);
            try {
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
                con.setRequestProperty("SoapAction", SERVICE + "#" + action);
                if (auth != null) con.setRequestProperty("Authorization", auth);
                try (OutputStream o = con.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
                int code = con.getResponseCode();
                if (code == 401 && attempt == 0) { auth = answer(c, con.getHeaderField("WWW-Authenticate")); continue; }
                if (code == 401) throw new IOException(c.getString(R.string.fritz_err_login));
                if (code != 200) throw new IOException(c.getString(R.string.fritz_err_http, code));
                return new String(read(con.getInputStream()), StandardCharsets.UTF_8);
            } catch (java.net.ConnectException | java.net.SocketTimeoutException | java.net.UnknownHostException e) {
                throw new IOException(c.getString(R.string.fritz_err_unreachable, host(c)));
            } finally {
                con.disconnect();
            }
        }
        throw new IOException(c.getString(R.string.fritz_err_login));
    }

    private static String answer(Context c, String challenge) throws IOException {
        if (challenge == null || !challenge.toLowerCase(Locale.ROOT).startsWith("digest")) throw new IOException(c.getString(R.string.fritz_err_login));
        String realm = null, nonce = null, opaque = null, qop = null;
        Matcher m = PARAM.matcher(challenge.substring(challenge.indexOf(' ') + 1));
        while (m.find()) {
            String k = m.group(1).toLowerCase(Locale.ROOT), v = m.group(2) != null ? m.group(2) : m.group(3);
            if (k.equals("realm")) realm = v;
            else if (k.equals("nonce")) nonce = v;
            else if (k.equals("opaque")) opaque = v;
            else if (k.equals("qop")) qop = v;
        }
        byte[] r = new byte[8];
        new SecureRandom().nextBytes(r);
        StringBuilder cn = new StringBuilder();
        for (byte b : r) cn.append(String.format(Locale.ROOT, "%02x", b));
        String user = user(c), nc = "00000001";
        boolean auth = qop != null && qop.contains("auth");
        String resp = Tvh.digestResponse("MD5", user, realm, pass(c), "POST", CONTROL, nonce, auth ? nc : null, auth ? cn.toString() : null);
        StringBuilder h = new StringBuilder("Digest username=\"").append(user).append("\", realm=\"").append(realm)
                .append("\", nonce=\"").append(nonce).append("\", uri=\"").append(CONTROL).append("\", algorithm=MD5");
        if (auth) h.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cn).append('"');
        h.append(", response=\"").append(resp).append('"');
        if (opaque != null) h.append(", opaque=\"").append(opaque).append('"');
        return h.toString();
    }

    /**
     * The phonebook file itself (its address carries a one-off session id). The router signs https with a
     * certificate of its own, so that is accepted for this one connection, and only to a private-network address.
     */
    private static byte[] fetch(Context c, String url) throws IOException {
        HttpURLConnection con = open(url);
        try {
            if (con instanceof HttpsURLConnection && isLocal(new URL(url).getHost())) trustRouter((HttpsURLConnection) con);
            if (con.getResponseCode() != 200) throw new IOException(c.getString(R.string.fritz_err_http, con.getResponseCode()));
            return read(con.getInputStream());
        } finally {
            con.disconnect();
        }
    }

    private static boolean isLocal(String h) {
        return h.startsWith("192.168.") || h.startsWith("10.") || h.matches("172\\.(1[6-9]|2\\d|3[01])\\..*") || h.endsWith(".box") || h.endsWith(".lan");
    }

    private static void trustRouter(HttpsURLConnection con) throws IOException {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String type) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String type) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            con.setSSLSocketFactory(ctx.getSocketFactory());
            con.setHostnameVerifier((h, s) -> true);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(15000);
        con.setInstanceFollowRedirects(false);
        return con;
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 8 * 1024 * 1024) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
