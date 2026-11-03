package org.immix.frame.hardware;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.immix.frame.FrameContract;

/** Local owner controls: other apps cannot set persistent policy through the display-client contract. */
public final class SettingsActivity extends Activity {
    private TextView status;
    private EditText minutes, quietStart, quietEnd;
    private final Handler main = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            FrameService service = FrameService.instance;
            Bundle state = service == null ? Bundle.EMPTY : service.localState();
            status.setText("Backend: " + state.getBoolean("presence_supported") +
                    "\nPower policy active: " + state.getBoolean(FrameContract.MANAGED) +
                    "\nPresence: " + state.getString(FrameContract.PRESENCE, "unknown") +
                    "\nReason: " + state.getString(FrameContract.REASON, "starting") +
                    "\nDisplay awake: " + state.getBoolean(FrameContract.DISPLAY_ON) +
                    "\nDeep-sleep wake: not verified");
            main.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        android.content.Intent service = new android.content.Intent(this, FrameService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setFocusableInTouchMode(true);
        layout.requestFocus();
        layout.setPadding(30, 20, 30, 20);
        status = new TextView(this); layout.addView(status);
        TextView label = new TextView(this); label.setText("Inactivity timeout in minutes (0 = always on)"); layout.addView(label);
        minutes = new EditText(this); minutes.setInputType(2); minutes.setText(Long.toString(getSharedPreferences("policy", MODE_PRIVATE).getLong("timeout_ms", 15 * 60_000L) / 60_000L)); layout.addView(minutes);
        quietStart = new EditText(this); quietStart.setSingleLine(true); quietStart.setHint("Quiet start HH:mm (optional)"); layout.addView(quietStart);
        quietEnd = new EditText(this); quietEnd.setSingleLine(true); quietEnd.setHint("Quiet end HH:mm (optional)"); layout.addView(quietEnd);
        android.content.SharedPreferences saved = getSharedPreferences("policy", MODE_PRIVATE);
        quietStart.setText(formatTime(saved.getInt("quiet_start", -1)));
        quietEnd.setText(formatTime(saved.getInt("quiet_end", -1)));
        button(layout, "Observe only", () -> configure(false));
        button(layout, "Enable motion power policy", () -> configure(true));
        button(layout, "Manual stay off (motion will not wake)", () -> { if (FrameService.instance != null) FrameService.instance.manualOff(true); });
        button(layout, "Clear manual override", () -> { if (FrameService.instance != null) FrameService.instance.manualOff(false); });
        TextView note = new TextView(this);
        note.setText("The root adapter must be started separately. No boot hook is installed. Management returns to observe-only after service restart.");
        layout.addView(note);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }
    private void configure(boolean enabled) {
        try {
            long value = Long.parseLong(minutes.getText().toString());
            if (value < 0 || value > 1440) throw new IllegalArgumentException();
            if (FrameService.instance != null) FrameService.instance.configure(enabled, value * 60_000, parseTime(quietStart), parseTime(quietEnd));
        } catch (IllegalArgumentException e) { minutes.setError("Use 0–1440 minutes and a complete valid quiet-hours pair"); }
    }
    private String formatTime(int value) {
        return value < 0 ? "" : String.format(java.util.Locale.ROOT, "%02d:%02d", value / 60, value % 60);
    }
    private int parseTime(EditText field) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) return -1;
        if (!text.matches("[0-2][0-9]:[0-5][0-9]")) throw new IllegalArgumentException();
        int hour = Integer.parseInt(text.substring(0, 2));
        if (hour > 23) throw new IllegalArgumentException();
        return hour * 60 + Integer.parseInt(text.substring(3, 5));
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setOnClickListener(view -> action.run()); parent.addView(button);
    }
    @Override public void onResume() { super.onResume(); main.post(refresh); }
    @Override public void onPause() { main.removeCallbacks(refresh); super.onPause(); }
}
