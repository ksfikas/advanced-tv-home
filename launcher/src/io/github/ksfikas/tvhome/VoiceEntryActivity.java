package io.github.ksfikas.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;

/**
 * Where Google's assistant can hand a spoken "play MEGA on Advanced TV Home" or "search ERT 1 on Advanced TV
 * Home". It has no screen: it passes the words to Search as "watch ...", which switches channel (or searches).
 */
public class VoiceEntryActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String said = getIntent().getStringExtra(SearchManager.QUERY);
        if (said == null) said = getIntent().getStringExtra("query");
        Intent go = new Intent(this, SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (said != null && !said.trim().isEmpty()) go.putExtra(SearchActivity.EXTRA_SAID, "watch " + said.trim());
        startActivity(go);
        finish();
    }
}
