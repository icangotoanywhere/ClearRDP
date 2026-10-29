package io.github.icangotoanywhere.clearrdp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Saved connections, kept in the app's private storage. */
final class HostStore
{
	static final class Host
	{
		String id = UUID.randomUUID().toString();
		String name = "";
		String address = "";
		int port = 3389;
		String user = "";
		String password = "";
		String domain = "";
		int scale = 175;          // Windows display scaling, percent
		boolean smooth = false;   // H.264 (smooth) vs progressive (sharp)
		boolean sound = true;
		boolean touchpad = true;

		String title()
		{
			return name.isEmpty() ? address : name;
		}

		JSONObject toJson() throws Exception
		{
			JSONObject o = new JSONObject();
			o.put("id", id);
			o.put("name", name);
			o.put("address", address);
			o.put("port", port);
			o.put("user", user);
			o.put("password", password);
			o.put("domain", domain);
			o.put("scale", scale);
			o.put("smooth", smooth);
			o.put("sound", sound);
			o.put("touchpad", touchpad);
			return o;
		}

		static Host fromJson(JSONObject o)
		{
			Host h = new Host();
			h.id = o.optString("id", h.id);
			h.name = o.optString("name");
			h.address = o.optString("address");
			h.port = o.optInt("port", 3389);
			h.user = o.optString("user");
			h.password = o.optString("password");
			h.domain = o.optString("domain");
			h.scale = o.optInt("scale", 175);
			h.smooth = o.optBoolean("smooth", false);
			h.sound = o.optBoolean("sound", true);
			h.touchpad = o.optBoolean("touchpad", true);
			return h;
		}
	}

	private final SharedPreferences prefs;

	HostStore(Context ctx)
	{
		prefs = ctx.getSharedPreferences("hosts", Context.MODE_PRIVATE);
	}

	List<Host> load()
	{
		List<Host> out = new ArrayList<>();
		try
		{
			JSONArray a = new JSONArray(prefs.getString("list", "[]"));
			for (int i = 0; i < a.length(); i++)
				out.add(Host.fromJson(a.getJSONObject(i)));
		}
		catch (Exception ignored)
		{
		}
		return out;
	}

	void save(List<Host> list)
	{
		try
		{
			JSONArray a = new JSONArray();
			for (Host h : list)
				a.put(h.toJson());
			prefs.edit().putString("list", a.toString()).apply();
		}
		catch (Exception ignored)
		{
		}
	}

	Host find(String id)
	{
		for (Host h : load())
			if (h.id.equals(id))
				return h;
		return null;
	}

	void put(Host h)
	{
		List<Host> list = load();
		boolean found = false;
		for (int i = 0; i < list.size(); i++)
			if (list.get(i).id.equals(h.id))
			{
				list.set(i, h);
				found = true;
			}
		if (!found)
			list.add(h);
		save(list);
	}

	void remove(String id)
	{
		List<Host> list = load();
		for (int i = list.size() - 1; i >= 0; i--)
			if (list.get(i).id.equals(id))
				list.remove(i);
		save(list);
	}
}
