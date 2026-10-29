package io.github.icangotoanywhere.clearrdp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity
{
	static final int[] SCALES = { 100, 125, 150, 175, 200, 250, 300 };

	private HostStore store;
	private LinearLayout list;
	private float dp;

	@Override
	protected void onCreate(Bundle b)
	{
		super.onCreate(b);
		dp = getResources().getDisplayMetrics().density;
		store = new HostStore(this);

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(0xFF121417);
		root.setFitsSystemWindows(true);
		int p = px(16);
		root.setPadding(p, p, p, p);

		TextView title = new TextView(this);
		title.setText("无遮远程");
		title.setTextColor(Color.WHITE);
		title.setTextSize(24);
		root.addView(title);

		TextView hint = new TextView(this);
		hint.setText("上方显示远程桌面，下方是快捷键盘；要打字时弹出手机键盘。\n点一台电脑即可连接，长按可编辑或删除。");
		hint.setTextColor(0xFF9AA3AD);
		hint.setTextSize(13);
		hint.setPadding(0, px(6), 0, px(14));
		root.addView(hint);

		ScrollView sv = new ScrollView(this);
		list = new LinearLayout(this);
		list.setOrientation(LinearLayout.VERTICAL);
		sv.addView(list);
		root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		Button add = new Button(this);
		add.setText("＋ 添加电脑");
		add.setTextSize(16);
		add.setOnClickListener(v -> edit(null));
		root.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));

		setContentView(root);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		refresh();
	}

	private int px(float v)
	{
		return Math.round(v * dp);
	}

	private void refresh()
	{
		list.removeAllViews();
		List<HostStore.Host> hosts = store.load();
		if (hosts.isEmpty())
		{
			TextView t = new TextView(this);
			t.setText("还没有保存的电脑，点下方「添加电脑」。");
			t.setTextColor(0xFF9AA3AD);
			t.setPadding(0, px(24), 0, 0);
			t.setGravity(Gravity.CENTER_HORIZONTAL);
			list.addView(t);
			return;
		}
		for (HostStore.Host h : hosts)
		{
			LinearLayout card = new LinearLayout(this);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setPadding(px(16), px(14), px(16), px(14));
			GradientDrawable bg = new GradientDrawable();
			bg.setColor(0xFF1E2228);
			bg.setCornerRadius(px(12));
			card.setBackground(bg);

			TextView name = new TextView(this);
			name.setText(h.title());
			name.setTextColor(Color.WHITE);
			name.setTextSize(18);
			card.addView(name);

			TextView sub = new TextView(this);
			String s = h.address + (h.port != 3389 ? ":" + h.port : "");
			if (!h.user.isEmpty())
				s = h.user + " @ " + s;
			sub.setText(s);
			sub.setTextColor(0xFF9AA3AD);
			sub.setTextSize(13);
			card.addView(sub);

			card.setOnClickListener(v -> connect(h));
			card.setOnLongClickListener(v -> {
				new AlertDialog.Builder(this)
				    .setTitle(h.title())
				    .setItems(new String[] { "编辑", "删除" }, (d, which) -> {
					    if (which == 0)
						    edit(h);
					    else
						    new AlertDialog.Builder(this)
						        .setMessage("删除「" + h.title() + "」？")
						        .setPositiveButton("删除", (d2, w2) -> {
							        store.remove(h.id);
							        refresh();
						        })
						        .setNegativeButton("取消", null)
						        .show();
				    })
				    .show();
				return true;
			});

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.bottomMargin = px(10);
			list.addView(card, lp);
		}
	}

	private void connect(HostStore.Host h)
	{
		Intent i = new Intent(this, SessionActivity.class);
		i.putExtra("id", h.id);
		startActivity(i);
	}

	private EditText field(LinearLayout form, String label, String value, int type)
	{
		TextView l = new TextView(this);
		l.setText(label);
		l.setTextSize(13);
		l.setPadding(0, px(10), 0, 0);
		form.addView(l);
		EditText e = new EditText(this);
		e.setText(value);
		e.setInputType(type);
		e.setSingleLine(true);
		form.addView(e);
		return e;
	}

	private void edit(HostStore.Host existing)
	{
		final HostStore.Host h = existing != null ? existing : new HostStore.Host();
		ScrollView sv = new ScrollView(this);
		LinearLayout form = new LinearLayout(this);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(px(20), px(8), px(20), px(8));
		sv.addView(form);

		final EditText name = field(form, "名称（可不填）", h.name, InputType.TYPE_CLASS_TEXT);
		final EditText addr = field(form, "电脑地址（IP 或域名，可带 :端口）",
		                            h.address + (h.port != 3389 && !h.address.isEmpty() ? ":" + h.port : ""),
		                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		final EditText user = field(form, "Windows 用户名", h.user, InputType.TYPE_CLASS_TEXT);
		final EditText pass = field(form, "密码（不填则连接时再问）", h.password,
		                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		final EditText dom = field(form, "域（一般不用填）", h.domain, InputType.TYPE_CLASS_TEXT);

		TextView sl = new TextView(this);
		sl.setText("远程界面大小（Windows 缩放比例，越大字越大）");
		sl.setTextSize(13);
		sl.setPadding(0, px(12), 0, 0);
		form.addView(sl);
		final Spinner scale = new Spinner(this);
		String[] labels = new String[SCALES.length];
		int sel = 3;
		for (int i = 0; i < SCALES.length; i++)
		{
			labels[i] = SCALES[i] + "%" + (SCALES[i] == 175 ? "（推荐）" : "");
			if (SCALES[i] == h.scale)
				sel = i;
		}
		scale.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
		scale.setSelection(sel);
		form.addView(scale);

		TextView ql = new TextView(this);
		ql.setText("画质");
		ql.setTextSize(13);
		ql.setPadding(0, px(12), 0, 0);
		form.addView(ql);
		final Spinner quality = new Spinner(this);
		quality.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
		                                      new String[] { "清晰（文字锐利，适合办公）",
		                                                     "流畅（H.264，适合看视频、网速慢）" }));
		quality.setSelection(h.smooth ? 1 : 0);
		form.addView(quality);

		final CheckBox sound = new CheckBox(this);
		sound.setText("远程声音在手机上播放");
		sound.setChecked(h.sound);
		form.addView(sound);

		final CheckBox tp = new CheckBox(this);
		tp.setText("默认触控板模式（不勾选则为直接点按）");
		tp.setChecked(h.touchpad);
		form.addView(tp);

		new AlertDialog.Builder(this)
		    .setTitle(existing == null ? "添加电脑" : "编辑")
		    .setView(sv)
		    .setPositiveButton("保存", (d, w) -> {
			    String a = addr.getText().toString().trim();
			    if (a.isEmpty())
			    {
				    Toast.makeText(this, "地址不能为空", Toast.LENGTH_SHORT).show();
				    return;
			    }
			    int port = 3389;
			    int colon = a.lastIndexOf(':');
			    if (colon > 0 && a.indexOf(':') == colon)
			    {
				    try
				    {
					    port = Integer.parseInt(a.substring(colon + 1));
					    a = a.substring(0, colon);
				    }
				    catch (NumberFormatException ignored)
				    {
				    }
			    }
			    h.name = name.getText().toString().trim();
			    h.address = a;
			    h.port = port;
			    h.user = user.getText().toString().trim();
			    h.password = pass.getText().toString();
			    h.domain = dom.getText().toString().trim();
			    h.scale = SCALES[scale.getSelectedItemPosition()];
			    h.smooth = quality.getSelectedItemPosition() == 1;
			    h.sound = sound.isChecked();
			    h.touchpad = tp.isChecked();
			    store.put(h);
			    refresh();
		    })
		    .setNegativeButton("取消", null)
		    .show();
	}
}
