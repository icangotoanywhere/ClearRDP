package io.github.icangotoanywhere.clearrdp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.freerdp.freerdpcore.services.LibFreeRDP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Screen layout (portrait):
 *
 *   status bar
 *   ┌──────────────────────────┐
 *   │      remote desktop      │  from below the status bar down to the top edge
 *   │                          │  of the phone's own keyboard
 *   ├──────────────────────────┤  ← same line as the top of the phone keyboard
 *   │   shortcut key panel     │  same height as the phone keyboard; the phone
 *   │                          │  keyboard, when shown, covers exactly this area
 *   └──────────────────────────┘
 *
 * The phone keyboard's height is measured the first time (shown for a moment, then
 * hidden) and remembered; whenever it is shown later the height is re-checked.
 */
public class SessionActivity extends Activity
    implements LibFreeRDP.UIEventListener, LibFreeRDP.EventListener, RemoteView.Host,
               KeyInputView.Listener
{
	private static final int OFF = 0, ONCE = 1, LOCK = 2;
	private static final int[] MOD_VK = { Vk.LCONTROL, Vk.LMENU, Vk.LSHIFT, Vk.LWIN };
	private static final String[] MOD_NAME = { "Ctrl", "Alt", "Shift", "Win" };

	private static final int C_KEY = 0xFF2B2F36, C_FN = 0xFF22262C, C_ACCENT = 0xFF2F6FDB,
	                         C_LOCK = 0xFFC8661E, C_PRESS = 0xFF4A515C, C_DANGER = 0xFF7A2E2E;

	private final Handler ui = new Handler(Looper.getMainLooper());
	private float dp;
	private SharedPreferences prefs;

	private HostStore.Host host;
	private long inst;
	private volatile boolean connected, closing, freed;
	private boolean started;

	private LinearLayout root;
	private FrameLayout remoteBox;
	private RemoteView remote;
	private KeyInputView input;
	private TextView composing;
	private LinearLayout panel;
	private TextView modeBtn, kbBtn;
	private final TextView[] modBtn = new TextView[4];
	private final int[] mod = new int[4];

	// insets / keyboard geometry (px)
	private int statusTop, navBottom;
	private boolean insetsSeen, imeVisible;
	private int kbHeight;      // distance from screen bottom to the top of the phone keyboard
	private boolean measuring;
	private int sessionW, sessionH;
	private AlertDialog dialog;

	private ClipboardManager clipboard;
	private String lastClipSent = "", lastClipRemote = "";

	private final Runnable altRelease = () -> {
		if (mod[1] == LOCK)
			setMod(1, OFF);
	};
	private final Runnable sendLayout = () -> {
		if (connected && inst != 0)
			LibFreeRDP.sendMonitorLayout(inst, sessionW, sessionH);
	};
	private final Runnable measureTimeout = () -> {
		if (!measuring)
			return;
		measuring = false;
		hideIme();
		layoutAll();
	};

	// =============================================================== lifecycle

	@Override
	protected void onCreate(Bundle b)
	{
		super.onCreate(b);
		dp = getResources().getDisplayMetrics().density;
		prefs = getSharedPreferences("ui", MODE_PRIVATE);
		host = new HostStore(this).find(getIntent().getStringExtra("id"));
		if (host == null)
		{
			finish();
			return;
		}
		clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
		kbHeight = prefs.getInt("kbHeight", 0);

		Window w = getWindow();
		w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
		                   WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		w.setStatusBarColor(Color.BLACK);
		w.setNavigationBarColor(0xFF15171A);
		if (Build.VERSION.SDK_INT >= 30)
			w.setDecorFitsSystemWindows(false);
		else
			w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
			                                       View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
			                                       View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

		root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(0xFF15171A);

		// --- remote desktop area, with the "composing" bubble overlaid at its bottom
		remoteBox = new FrameLayout(this);
		remote = new RemoteView(this, this);
		remote.setTouchpad(host.touchpad);
		remote.setMessage("正在准备…");
		remoteBox.addView(remote, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
		                                                       ViewGroup.LayoutParams.MATCH_PARENT));
		composing = new TextView(this);
		composing.setTextColor(Color.WHITE);
		composing.setTextSize(15);
		composing.setPadding(px(10), px(4), px(10), px(4));
		GradientDrawable cbg = new GradientDrawable();
		cbg.setColor(0xE02F6FDB);
		cbg.setCornerRadius(px(6));
		composing.setBackground(cbg);
		composing.setVisibility(View.GONE);
		FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
		    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
		    Gravity.BOTTOM | Gravity.END);
		clp.setMargins(0, 0, px(6), px(6));
		remoteBox.addView(composing, clp);
		input = new KeyInputView(this);
		input.setListener(this);
		input.setAlpha(0f);
		remoteBox.addView(input, new FrameLayout.LayoutParams(1, 1));
		root.addView(remoteBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

		// --- shortcut key panel, same height as the phone keyboard
		panel = buildPanel();
		root.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

		root.setOnApplyWindowInsetsListener(this::onInsets);
		root.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
			if (bt - t != ob - ot)
				layoutAll();
		});
		setContentView(root);

		input.requestFocus();
		LibFreeRDP.setEventListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		clipboard.addPrimaryClipChangedListener(clipListener);
	}

	@Override
	protected void onPause()
	{
		clipboard.removePrimaryClipChangedListener(clipListener);
		super.onPause();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			pushLocalClipboard();
	}

	@Override
	protected void onDestroy()
	{
		ui.removeCallbacksAndMessages(null);
		closeSession();
		super.onDestroy();
	}

	@Override
	public void onBackPressed()
	{
		if (imeVisible)
			hideIme();
		else
			confirmDisconnect();
	}

	@Override
	public boolean onBackPreIme(KeyEvent ev)
	{
		if (imeVisible)
			return false; // let the phone keyboard close itself
		if (ev.getAction() == KeyEvent.ACTION_UP)
			confirmDisconnect();
		return true;
	}

	// =============================================================== layout

	private int px(float v)
	{
		return Math.round(v * dp);
	}

	private WindowInsets onInsets(View v, WindowInsets in)
	{
		int top, nav, imeBottom;
		boolean ime;
		if (Build.VERSION.SDK_INT >= 30)
		{
			top = in.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
			nav = in.getInsets(WindowInsets.Type.navigationBars()).bottom;
			imeBottom = in.getInsets(WindowInsets.Type.ime()).bottom;
			ime = in.isVisible(WindowInsets.Type.ime()) && imeBottom > nav + px(100);
		}
		else
		{
			top = in.getSystemWindowInsetTop();
			nav = in.getStableInsetBottom();
			imeBottom = in.getSystemWindowInsetBottom();
			ime = imeBottom > nav + px(100);
		}
		statusTop = top;
		navBottom = nav;
		insetsSeen = true;
		root.setPadding(0, top, 0, 0);
		panel.setPadding(panel.getPaddingLeft(), panel.getPaddingTop(), panel.getPaddingRight(),
		                 px(4) + nav);

		boolean was = imeVisible;
		imeVisible = ime;
		if (ime)
		{
			// remember / refresh the keyboard height
			if (kbHeight == 0 || Math.abs(imeBottom - kbHeight) > px(4))
			{
				kbHeight = imeBottom;
				prefs.edit().putInt("kbHeight", kbHeight).apply();
			}
			if (measuring)
			{
				measuring = false;
				ui.removeCallbacks(measureTimeout);
				hideIme();
			}
		}
		if (was != ime)
			updateKbBtn();
		layoutAll();
		return in;
	}

	/** Size the remote area and the shortcut panel from the keyboard height. */
	private void layoutAll()
	{
		int fullH = root.getHeight();
		int width = root.getWidth();
		if (fullH <= 0 || width <= 0 || !insetsSeen)
			return;

		if (kbHeight == 0)
		{
			if (!measuring)
			{
				// first time ever: pop the phone keyboard for a moment to learn its height
				measuring = true;
				remote.setMessage("正在测量键盘高度…");
				ui.postDelayed(this::showIme, 150);
				ui.postDelayed(measureTimeout, 2000);
			}
			return;
		}
		if (measuring)
			return;

		int kb = kbHeight;
		if (kb < px(160) || kb > fullH * 0.7f)
			kb = Math.round(fullH * 0.4f); // implausible value: fall back to 40% of screen

		int remoteH = fullH - statusTop - kb;
		if (remoteH < px(160))
			remoteH = px(160);
		int panelH = fullH - statusTop - remoteH; // reaches the bottom of the screen

		setHeight(remoteBox, remoteH);
		setHeight(panel, panelH);

		int sw = width - (width % 2);
		int sh = remoteH - (remoteH % 2);
		if (!started)
		{
			started = true;
			sessionW = sw;
			sessionH = sh;
			ui.post(this::startSession);
		}
		else if (connected && (sw != sessionW || sh != sessionH))
		{
			sessionW = sw;
			sessionH = sh;
			ui.removeCallbacks(sendLayout);
			ui.postDelayed(sendLayout, 600);
		}
	}

	private static void setHeight(View v, int h)
	{
		ViewGroup.LayoutParams lp = v.getLayoutParams();
		if (lp.height != h)
		{
			lp.height = h;
			v.setLayoutParams(lp);
		}
	}

	// =============================================================== phone keyboard

	private boolean dialogShowing()
	{
		return dialog != null && dialog.isShowing();
	}

	private void showIme()
	{
		if (isFinishing())
			return;
		if (!input.hasFocus())
			input.requestFocus();
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.restartInput(input);
		imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
		if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null)
			getWindow().getInsetsController().show(WindowInsets.Type.ime());
	}

	private void hideIme()
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
		if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null)
			getWindow().getInsetsController().hide(WindowInsets.Type.ime());
		onComposing("");
	}

	private void toggleIme()
	{
		if (imeVisible)
			hideIme();
		else
			showIme();
	}

	@Override
	public void onUserTouch()
	{
	}


	// =============================================================== session

	private static String clientName()
	{
		String m = Build.MODEL == null ? "Android" : Build.MODEL.replaceAll("[^A-Za-z0-9-]", "");
		if (m.isEmpty())
			m = "Android";
		return m.length() > 15 ? m.substring(0, 15) : m;
	}

	private void startSession()
	{
		remote.setMessage("正在连接 " + host.title() + " …");
		try
		{
			inst = LibFreeRDP.newInstance(getApplicationContext());
		}
		catch (Throwable t)
		{
			fail("远程桌面内核加载失败：" + t);
			return;
		}
		LibFreeRDP.setUIEventListener(inst, this);

		List<String> a = new ArrayList<>();
		a.add("ClearRDP");
		a.add("/gdi:sw");
		a.add("/client-hostname:" + clientName());
		a.add("/v:" + host.address);
		a.add("/port:" + host.port);
		if (!host.user.isEmpty())
			a.add("/u:" + host.user);
		if (!host.domain.isEmpty())
			a.add("/d:" + host.domain);
		if (!host.password.isEmpty())
			a.add("/p:" + host.password);
		a.add("/size:" + sessionW + "x" + sessionH);
		a.add("/bpp:32");
		if (host.scale > 100)
		{
			a.add("/scale-desktop:" + host.scale);
			a.add("/scale-device:100");
		}
		if (host.smooth && LibFreeRDP.hasH264Support())
			a.add("/gfx:AVC420");
		else
			a.add("/gfx:progressive");
		a.add("/network:auto");
		a.add("+fonts");
		a.add("/clipboard");
		a.add("/disp");
		if (host.sound)
		{
			a.add("/audio-mode:0");
			a.add("/sound");
		}
		else
			a.add("/audio-mode:1");
		a.add("+auto-reconnect");
		a.add("/kbd:unicode:on");
		a.add("/cert:ignore");
		a.add("/log-level:WARN");

		if (!LibFreeRDP.parseArguments(inst, a))
		{
			fail("连接参数有误，请检查地址和端口。");
			return;
		}
		if (!LibFreeRDP.connect(inst))
			fail("无法启动连接。");
	}

	private void closeSession()
	{
		closing = true;
		final long i = inst;
		if (i == 0 || freed)
			return;
		freed = true;
		connected = false;
		new Thread(() -> LibFreeRDP.freeInstance(i), "rdp-free").start();
	}

	private void confirmDisconnect()
	{
		if (dialogShowing())
			return;
		dialog = new AlertDialog.Builder(this)
		             .setMessage("断开远程桌面并返回？")
		             .setPositiveButton("断开", (d, w) -> {
			             closeSession();
			             finish();
		             })
		             .setNegativeButton("继续使用", null)
		             .show();
	}

	private static String explain(String err)
	{
		if (err == null)
			return "";
		String e = err.toUpperCase();
		if (e.contains("LOGON_FAILURE") || e.contains("AUTHENTICATION") ||
		    e.contains("WRONG_PASSWORD") || e.contains("ACCESS_DENIED"))
			return "用户名或密码不对。\n（" + err + "）";
		if (e.contains("PASSWORD_EXPIRED") || e.contains("MUST_CHANGE"))
			return "这个账号的密码已过期，需要先在电脑上改密码。\n（" + err + "）";
		if (e.contains("DNS") || e.contains("CONNECT_FAILED") || e.contains("TRANSPORT") ||
		    e.contains("TIMEOUT"))
			return "连不上这台电脑：地址不对、电脑没开或没开启远程桌面、网络不通或被防火墙挡住。\n（" +
			    err + "）";
		if (e.contains("SECURITY_NEGO") || e.contains("TLS"))
			return "安全协商失败，电脑那边的远程桌面安全设置不兼容。\n（" + err + "）";
		return err;
	}

	private void fail(final String msg)
	{
		ui.post(() -> {
			connected = false;
			remote.setMessage(null);
			if (isFinishing())
				return;
			if (dialogShowing())
				dialog.dismiss();
			dialog = new AlertDialog.Builder(this)
			             .setTitle(host.title())
			             .setMessage(msg)
			             .setCancelable(false)
			             .setPositiveButton("重新连接", (d, w) -> recreateSession())
			             .setNegativeButton("返回", (d, w) -> {
				             closeSession();
				             finish();
			             })
			             .show();
		});
	}

	private void recreateSession()
	{
		final long old = inst;
		inst = 0;
		freed = false;
		closing = false;
		connected = false;
		remote.setBitmap(null);
		remote.setMessage("正在重新连接…");
		new Thread(() -> {
			if (old != 0)
				LibFreeRDP.freeInstance(old);
			ui.post(this::startSession);
		}, "rdp-reconnect").start();
	}

	// ---- LibFreeRDP.EventListener (session thread)

	@Override
	public void OnPreConnect(long instance)
	{
	}

	@Override
	public void OnConnectionSuccess(long instance)
	{
		if (instance != inst)
			return;
		connected = true;
		ui.post(() -> {
			remote.setMessage(null);
			pushLocalClipboard();
		});
	}

	@Override
	public void OnConnectionFailure(long instance)
	{
		if (instance != inst || closing)
			return;
		fail("连接失败。\n" + explain(LibFreeRDP.getLastErrorString(instance)));
	}

	@Override
	public void OnDisconnecting(long instance)
	{
	}

	@Override
	public void OnDisconnected(long instance)
	{
		if (instance != inst || closing)
			return;
		connected = false;
		String err = LibFreeRDP.getLastErrorString(instance);
		fail("连接已断开。" + (err == null || err.isEmpty() || err.contains("SUCCESS") ? ""
		                                                                          : "\n" + explain(err)));
	}

	// ---- LibFreeRDP.UIEventListener (session thread)

	private void newSurface(int width, int height)
	{
		Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		b.setHasAlpha(false);
		remote.setBitmap(b);
	}

	@Override
	public void OnSettingsChanged(int width, int height, int bpp)
	{
		newSurface(width, height);
	}

	@Override
	public void OnGraphicsResize(int width, int height, int bpp)
	{
		newSurface(width, height);
	}

	@Override
	public void OnGraphicsUpdate(int x, int y, int width, int height)
	{
		Bitmap b = remote.getBitmap();
		if (b == null)
			return;
		synchronized (remote)
		{
			LibFreeRDP.updateGraphics(inst, b, x, y, width, height);
		}
		remote.invalidateRemote(x, y, width, height);
	}

	@Override
	public boolean OnAuthenticate(StringBuilder username, StringBuilder domain,
	                              StringBuilder password)
	{
		final CountDownLatch latch = new CountDownLatch(1);
		final boolean[] ok = { false };
		final String[] out = new String[3];
		ui.post(() -> {
			LinearLayout f = new LinearLayout(this);
			f.setOrientation(LinearLayout.VERTICAL);
			f.setPadding(px(20), px(8), px(20), 0);
			final EditText u = new EditText(this);
			u.setHint("用户名");
			u.setSingleLine(true);
			u.setText(username.toString());
			f.addView(u);
			final EditText p = new EditText(this);
			p.setHint("密码");
			p.setSingleLine(true);
			p.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			f.addView(p);
			if (dialogShowing())
				dialog.dismiss();
			dialog = new AlertDialog.Builder(this)
			             .setTitle("登录 " + host.title())
			             .setView(f)
			             .setCancelable(false)
			             .setPositiveButton("登录", (d, w) -> {
				             out[0] = u.getText().toString();
				             out[2] = p.getText().toString();
				             ok[0] = true;
				             latch.countDown();
			             })
			             .setNegativeButton("取消", (d, w) -> latch.countDown())
			             .setOnDismissListener(d -> latch.countDown())
			             .show();
			(username.length() == 0 ? u : p).requestFocus();
		});
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			return false;
		}
		if (!ok[0])
			return false;
		username.setLength(0);
		username.append(out[0]);
		password.setLength(0);
		password.append(out[2]);
		return true;
	}

	@Override
	public void OnRemoteClipboardChanged(final String data)
	{
		if (data == null)
			return;
		ui.post(() -> {
			lastClipRemote = data;
			lastClipSent = data;
			try
			{
				clipboard.setPrimaryClip(ClipData.newPlainText("远程桌面", data));
			}
			catch (Throwable ignored)
			{
			}
		});
	}

	@Override
	public void OnPointerSet(int[] pixels, int width, int height, int hotX, int hotY)
	{
		remote.setCursor(pixels, width, height, hotX, hotY);
	}

	@Override
	public void OnPointerSetNull()
	{
		remote.setCursorHidden(true);
	}

	@Override
	public void OnPointerSetDefault()
	{
		remote.setCursorDefault();
	}

	// =============================================================== clipboard

	private final ClipboardManager.OnPrimaryClipChangedListener clipListener =
	    this::pushLocalClipboard;

	private void pushLocalClipboard()
	{
		if (!connected || inst == 0)
			return;
		try
		{
			ClipData c = clipboard.getPrimaryClip();
			if (c == null || c.getItemCount() == 0)
				return;
			CharSequence t = c.getItemAt(0).coerceToText(this);
			if (t == null)
				return;
			String s = t.toString();
			if (s.isEmpty() || s.equals(lastClipSent) || s.equals(lastClipRemote))
				return;
			lastClipSent = s;
			LibFreeRDP.sendClipboardData(inst, s);
		}
		catch (Throwable ignored)
		{
		}
	}

	// =============================================================== mouse

	@Override
	public void sendMouse(int x, int y, int flags)
	{
		if (connected && inst != 0)
			LibFreeRDP.sendCursorEvent(inst, x, y, flags);
	}

	// =============================================================== keyboard

	private void vk(int code, boolean down)
	{
		if (connected && inst != 0)
			LibFreeRDP.sendKeyEvent(inst, code, down);
	}

	private void unicode(char c)
	{
		if (!connected || inst == 0)
			return;
		LibFreeRDP.sendUnicodeKeyEvent(inst, c, true);
		LibFreeRDP.sendUnicodeKeyEvent(inst, c, false);
	}

	private boolean anyMod()
	{
		for (int m : mod)
			if (m != OFF)
				return true;
		return false;
	}

	/** Press a key, wrapping it in any one-shot modifiers (locked ones are already held). */
	private void press(int code)
	{
		for (int i = 0; i < 4; i++)
			if (mod[i] == ONCE)
				vk(MOD_VK[i], true);
		vk(code, true);
		vk(code, false);
		for (int i = 3; i >= 0; i--)
			if (mod[i] == ONCE)
			{
				vk(MOD_VK[i], false);
				setMod(i, OFF, false);
			}
	}

	private void combo(int... codes)
	{
		for (int c : codes)
			vk(c, true);
		for (int i = codes.length - 1; i >= 0; i--)
			vk(codes[i], false);
		for (int i = 0; i < 4; i++)
			if (mod[i] == ONCE)
				setMod(i, OFF, false);
	}

	private void setMod(int i, int state)
	{
		setMod(i, state, true);
	}

	private void setMod(int i, int state, boolean sendNow)
	{
		int old = mod[i];
		if (sendNow)
		{
			if (old != LOCK && state == LOCK)
				vk(MOD_VK[i], true);
			else if (old == LOCK && state != LOCK)
				vk(MOD_VK[i], false);
		}
		mod[i] = state;
		TextView b = modBtn[i];
		if (b != null)
		{
			b.setText(state == LOCK ? MOD_NAME[i] + "锁" : MOD_NAME[i]);
			b.setBackground(keyBg(state == ONCE ? C_ACCENT : state == LOCK ? C_LOCK : C_KEY));
		}
	}

	@Override
	public void onText(CharSequence text)
	{
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == '\n' || c == '\r')
			{
				press(Vk.RETURN);
				continue;
			}
			if (c == '\t')
			{
				press(Vk.TAB);
				continue;
			}
			int code = anyMod() ? Vk.fromChar(c) : 0;
			if (code != 0)
				press(code);
			else
				unicode(c);
		}
	}

	@Override
	public void onComposing(CharSequence text)
	{
		if (text == null || text.length() == 0)
			composing.setVisibility(View.GONE);
		else
		{
			composing.setText(text);
			composing.setVisibility(View.VISIBLE);
		}
	}

	private static boolean isSpecialKey(int kc)
	{
		switch (kc)
		{
			case KeyEvent.KEYCODE_DEL:
			case KeyEvent.KEYCODE_FORWARD_DEL:
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_NUMPAD_ENTER:
			case KeyEvent.KEYCODE_TAB:
			case KeyEvent.KEYCODE_ESCAPE:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_MOVE_HOME:
			case KeyEvent.KEYCODE_MOVE_END:
			case KeyEvent.KEYCODE_PAGE_UP:
			case KeyEvent.KEYCODE_PAGE_DOWN:
			case KeyEvent.KEYCODE_INSERT:
			case KeyEvent.KEYCODE_SYSRQ:
			case KeyEvent.KEYCODE_MENU:
				return true;
			default:
				return kc >= KeyEvent.KEYCODE_F1 && kc <= KeyEvent.KEYCODE_F12;
		}
	}

	@Override
	public void onKey(KeyEvent ev)
	{
		int kc = ev.getKeyCode();
		int action = ev.getAction();
		if (action == KeyEvent.ACTION_MULTIPLE)
		{
			if (ev.getCharacters() != null)
				onText(ev.getCharacters());
			return;
		}
		int code = Vk.fromKeyCode(kc);
		if (Vk.isModifier(code))
		{
			// physical modifier on a hardware keyboard: pass straight through
			vk(code, action == KeyEvent.ACTION_DOWN);
			return;
		}
		if (action != KeyEvent.ACTION_DOWN)
			return;
		boolean hwMods = (ev.getMetaState() & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON |
		                                       KeyEvent.META_META_ON)) != 0;
		if (isSpecialKey(kc) || hwMods)
		{
			if (code != 0)
				press(code);
			return;
		}
		int ch = ev.getUnicodeChar(ev.getMetaState());
		if (ch > 0 && !Character.isISOControl(ch))
		{
			int c2 = anyMod() ? Vk.fromChar((char)ch) : 0;
			if (c2 != 0)
				press(c2);
			else
				onText(new String(Character.toChars(ch)));
			return;
		}
		if (code != 0)
			press(code);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent ev)
	{
		int kc = ev.getKeyCode();
		if (kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_VOLUME_UP ||
		    kc == KeyEvent.KEYCODE_VOLUME_DOWN || kc == KeyEvent.KEYCODE_VOLUME_MUTE ||
		    dialogShowing() || !connected)
			return super.dispatchKeyEvent(ev);
		onKey(ev);
		return true;
	}

	// =============================================================== shortcut keyboard

	private GradientDrawable shape(int color)
	{
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(px(6));
		return g;
	}

	private StateListDrawable keyBg(int color)
	{
		StateListDrawable s = new StateListDrawable();
		s.addState(new int[] { android.R.attr.state_pressed }, shape(C_PRESS));
		s.addState(new int[] {}, shape(color));
		return s;
	}

	private LinearLayout row()
	{
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
		    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
		panel.addView(r, lp);
		return r;
	}

	private TextView key(LinearLayout row, String label, float weight, int color, float textSp,
	                     View.OnClickListener click)
	{
		TextView t = new TextView(this);
		t.setText(label);
		t.setTextColor(Color.WHITE);
		t.setTextSize(textSp);
		t.setGravity(Gravity.CENTER);
		t.setSingleLine(true);
		t.setIncludeFontPadding(false);
		t.setBackground(keyBg(color));
		t.setOnClickListener(click);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
		                                                             weight);
		int m = px(2);
		lp.setMargins(m, m, m, m);
		row.addView(t, lp);
		return t;
	}

	private TextView key(LinearLayout row, String label, View.OnClickListener click)
	{
		return key(row, label, 1f, C_KEY, 14, click);
	}

	private LinearLayout buildPanel()
	{
		panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setBackgroundColor(0xFF15171A);
		panel.setPadding(px(3), px(4), px(3), px(4));

		// 1: escape / modifiers
		LinearLayout r1 = row();
		key(r1, "Esc", v -> press(Vk.ESCAPE));
		key(r1, "Tab", v -> press(Vk.TAB));
		for (int i = 0; i < 4; i++)
		{
			final int idx = i;
			modBtn[i] = key(r1, MOD_NAME[i], v -> setMod(idx, (mod[idx] + 1) % 3));
		}
		key(r1, "⌫", v -> press(Vk.BACK));

		// 2: F keys
		LinearLayout r2 = row();
		for (int i = 0; i < 12; i++)
		{
			final int f = i;
			key(r2, "F" + (i + 1), 1f, C_FN, 11, v -> press(Vk.F1 + f));
		}

		// 3 + 4: navigation cluster on the left, editing on the right
		LinearLayout r3 = row();
		key(r3, "Home", v -> press(Vk.HOME));
		key(r3, "↑", v -> press(Vk.UP));
		key(r3, "End", v -> press(Vk.END));
		key(r3, "PgUp", v -> press(Vk.PRIOR));
		key(r3, "复制", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'C'));
		key(r3, "粘贴", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'V'));
		key(r3, "Del", v -> press(Vk.DELETE));

		LinearLayout r4 = row();
		key(r4, "←", v -> press(Vk.LEFT));
		key(r4, "↓", v -> press(Vk.DOWN));
		key(r4, "→", v -> press(Vk.RIGHT));
		key(r4, "PgDn", v -> press(Vk.NEXT));
		key(r4, "剪切", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'X'));
		key(r4, "撤销", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'Z'));
		key(r4, "回车", v -> press(Vk.RETURN));

		// 5: common combos
		LinearLayout r5 = row();
		key(r5, "全选", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'A'));
		key(r5, "保存", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'S'));
		key(r5, "查找", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'F'));
		key(r5, "重做", 1f, C_FN, 14, v -> combo(Vk.LCONTROL, 'Y'));
		key(r5, "Alt+Tab", 1.4f, C_FN, 13, v -> {
			if (mod[1] != LOCK)
				setMod(1, LOCK);
			vk(Vk.TAB, true);
			vk(Vk.TAB, false);
			ui.removeCallbacks(altRelease);
			ui.postDelayed(altRelease, 1300);
		});
		key(r5, "关窗口", 1.2f, C_FN, 14, v -> combo(Vk.LMENU, Vk.F1 + 3));

		// 6: Windows
		LinearLayout r6 = row();
		key(r6, "开始", 1f, C_FN, 14, v -> press(Vk.LWIN));
		key(r6, "桌面", 1f, C_FN, 14, v -> combo(Vk.LWIN, 'D'));
		key(r6, "文件夹", 1.2f, C_FN, 14, v -> combo(Vk.LWIN, 'E'));
		key(r6, "运行", 1f, C_FN, 14, v -> combo(Vk.LWIN, 'R'));
		key(r6, "截图", 1f, C_FN, 14, v -> combo(Vk.LWIN, Vk.LSHIFT, 'S'));
		key(r6, "任务管理", 1.4f, C_FN, 13, v -> combo(Vk.LCONTROL, Vk.LSHIFT, Vk.ESCAPE));
		// Ctrl+Alt+Del never reaches a remote session; Ctrl+Alt+End is the RDP equivalent
		key(r6, "C+A+Del", 1.4f, C_FN, 12, v -> combo(Vk.LCONTROL, Vk.LMENU, Vk.END));

		// 7: app controls + the button that brings up the phone keyboard
		LinearLayout r7 = row();
		modeBtn = key(r7, "", 1.6f, C_KEY, 13, v -> {
			remote.setTouchpad(!remote.isTouchpad());
			updateModeBtn();
		});
		updateModeBtn();
		key(r7, "右键", 1f, C_KEY, 14, v -> remote.dispatchRightClick());
		key(r7, "缩放复位", 1.4f, C_KEY, 13, v -> remote.resetZoom());
		kbBtn = key(r7, "", 2.2f, C_ACCENT, 15, v -> toggleIme());
		updateKbBtn();
		key(r7, "断开", 1f, C_DANGER, 14, v -> confirmDisconnect());
		return panel;
	}

	private void updateModeBtn()
	{
		modeBtn.setText(remote.isTouchpad() ? "触控板" : "直接点");
	}

	private void updateKbBtn()
	{
		if (kbBtn != null)
			kbBtn.setText(imeVisible ? "收起键盘" : "⌨ 打字");
	}
}
