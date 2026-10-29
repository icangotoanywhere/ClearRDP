/*
   Android FreeRDP JNI Wrapper (trimmed for ClearRDP)

   Based on aFreeRDP's LibFreeRDP.java, Copyright 2013 Thincast Technologies GmbH,
   Author: Martin Fleisz. This Source Code Form is subject to the terms of the
   Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/.

   The package, class name and every native / callback signature below must stay
   exactly as in FreeRDP 3.32.1: the prebuilt libfreerdp-android.so looks them up
   by name.
*/

package com.freerdp.freerdpcore.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LongSparseArray;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibFreeRDP
{
	private static final String TAG = "LibFreeRDP";
	private static EventListener listener;
	private static boolean mHasH264 = false;
	private static String mVersion = "";

	private static final LongSparseArray<Boolean> mInstanceState = new LongSparseArray<>();
	private static final LongSparseArray<UIEventListener> mUi = new LongSparseArray<>();

	static
	{
		System.loadLibrary("freerdp-android");
		String version = freerdp_get_jni_version();
		mVersion = version;
		String[] versions = version.split("[\\.-]");
		if (versions.length > 0)
		{
			System.loadLibrary("freerdp-client" + versions[0]);
			System.loadLibrary("freerdp" + versions[0]);
			System.loadLibrary("winpr" + versions[0]);
		}
		Pattern pattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*");
		Matcher matcher = pattern.matcher(version);
		if (matcher.matches())
		{
			try
			{
				mHasH264 = freerdp_has_h264();
			}
			catch (Throwable t)
			{
				mHasH264 = false;
			}
		}
		Log.i(TAG, "FreeRDP " + version + ", H264=" + mHasH264);
	}

	public static boolean hasH264Support()
	{
		return mHasH264;
	}

	public static String getJniVersion()
	{
		return mVersion;
	}

	private static native boolean freerdp_has_h264();

	private static native boolean freerdp_has_camera_redirection();

	private static native String freerdp_get_jni_version();

	private static native String freerdp_get_version();

	private static native String freerdp_get_build_revision();

	private static native String freerdp_get_build_config();

	private static native long freerdp_new(Context context);

	private static native void freerdp_free(long inst);

	private static native boolean freerdp_parse_arguments(long inst, String[] args);

	private static native boolean freerdp_connect(long inst);

	private static native boolean freerdp_disconnect(long inst);

	private static native boolean freerdp_update_graphics(long inst, Bitmap bitmap, int x, int y,
	                                                      int width, int height);

	private static native boolean freerdp_send_cursor_event(long inst, int x, int y, int flags);

	private static native boolean freerdp_send_key_event(long inst, int keycode, boolean down);

	private static native boolean freerdp_send_unicodekey_event(long inst, int keycode,
	                                                            boolean down);

	private static native boolean freerdp_is_unicode_input_supported(long inst);

	private static native boolean freerdp_send_clipboard_data(long inst, String data);

	private static native boolean freerdp_send_clipboard_image_data(long inst, byte[] data,
	                                                                String mimeType);

	private static native boolean freerdp_send_monitor_layout(long inst, int width, int height);

	private static native String freerdp_get_last_error_string(long inst);

	public static void setEventListener(EventListener l)
	{
		listener = l;
	}

	public static void setUIEventListener(long inst, UIEventListener l)
	{
		synchronized (mUi)
		{
			if (l == null)
				mUi.remove(inst);
			else
				mUi.put(inst, l);
		}
	}

	private static UIEventListener ui(long inst)
	{
		synchronized (mUi)
		{
			return mUi.get(inst);
		}
	}

	public static long newInstance(Context context)
	{
		return freerdp_new(context);
	}

	/** Blocks until the session thread has finished; call off the UI thread. */
	public static void freeInstance(long inst)
	{
		synchronized (mInstanceState)
		{
			if (mInstanceState.get(inst, false))
				freerdp_disconnect(inst);
			long deadline = System.currentTimeMillis() + 15000;
			while (mInstanceState.get(inst, false) && System.currentTimeMillis() < deadline)
			{
				try
				{
					mInstanceState.wait(500);
				}
				catch (InterruptedException e)
				{
					break;
				}
			}
		}
		setUIEventListener(inst, null);
		freerdp_free(inst);
	}

	public static boolean parseArguments(long inst, List<String> args)
	{
		return freerdp_parse_arguments(inst, args.toArray(new String[0]));
	}

	public static boolean connect(long inst)
	{
		// mark as "session thread running" from the start, so freeInstance waits for it
		synchronized (mInstanceState)
		{
			mInstanceState.put(inst, true);
		}
		boolean ok = freerdp_connect(inst);
		if (!ok)
		{
			synchronized (mInstanceState)
			{
				mInstanceState.remove(inst);
				mInstanceState.notifyAll();
			}
		}
		return ok;
	}

	public static boolean disconnect(long inst)
	{
		return freerdp_disconnect(inst);
	}

	public static String getLastErrorString(long inst)
	{
		try
		{
			return freerdp_get_last_error_string(inst);
		}
		catch (Throwable t)
		{
			return "";
		}
	}

	public static boolean updateGraphics(long inst, Bitmap bitmap, int x, int y, int width,
	                                     int height)
	{
		return freerdp_update_graphics(inst, bitmap, x, y, width, height);
	}

	public static boolean sendCursorEvent(long inst, int x, int y, int flags)
	{
		return freerdp_send_cursor_event(inst, x, y, flags);
	}

	/** keycode is a Windows virtual-key code (VK_*). */
	public static boolean sendKeyEvent(long inst, int keycode, boolean down)
	{
		return freerdp_send_key_event(inst, keycode, down);
	}

	public static boolean sendUnicodeKeyEvent(long inst, int keycode, boolean down)
	{
		return freerdp_send_unicodekey_event(inst, keycode, down);
	}

	public static boolean sendClipboardData(long inst, String data)
	{
		return freerdp_send_clipboard_data(inst, data);
	}

	public static boolean sendMonitorLayout(long inst, int width, int height)
	{
		return freerdp_send_monitor_layout(inst, width, height);
	}

	public static String getVersion()
	{
		return freerdp_get_version();
	}

	// ---- callbacks invoked from native code (names/signatures must not change) ----

	private static void OnConnectionSuccess(long inst)
	{
		synchronized (mInstanceState)
		{
			mInstanceState.append(inst, true);
			mInstanceState.notifyAll();
		}
		if (listener != null)
			listener.OnConnectionSuccess(inst);
	}

	private static void OnConnectionFailure(long inst)
	{
		synchronized (mInstanceState)
		{
			mInstanceState.remove(inst);
			mInstanceState.notifyAll();
		}
		if (listener != null)
			listener.OnConnectionFailure(inst);
	}

	private static void OnPreConnect(long inst)
	{
		if (listener != null)
			listener.OnPreConnect(inst);
	}

	private static void OnDisconnecting(long inst)
	{
		if (listener != null)
			listener.OnDisconnecting(inst);
	}

	private static void OnDisconnected(long inst)
	{
		synchronized (mInstanceState)
		{
			mInstanceState.remove(inst);
			mInstanceState.notifyAll();
		}
		if (listener != null)
			listener.OnDisconnected(inst);
	}

	private static void OnSettingsChanged(long inst, int width, int height, int bpp)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnSettingsChanged(width, height, bpp);
	}

	private static boolean OnAuthenticate(long inst, StringBuilder username, StringBuilder domain,
	                                      StringBuilder password)
	{
		UIEventListener u = ui(inst);
		return u != null && u.OnAuthenticate(username, domain, password);
	}

	private static boolean OnGatewayAuthenticate(long inst, StringBuilder username,
	                                             StringBuilder domain, StringBuilder password)
	{
		UIEventListener u = ui(inst);
		return u != null && u.OnAuthenticate(username, domain, password);
	}

	private static int OnVerifyCertificateEx(long inst, String host, long port, String commonName,
	                                         String subject, String issuer, String fingerprint,
	                                         long flags)
	{
		return 1; // accept (same as /cert:ignore)
	}

	private static int OnVerifyChangedCertificateEx(long inst, String host, long port,
	                                                String commonName, String subject,
	                                                String issuer, String fingerprint,
	                                                String oldSubject, String oldIssuer,
	                                                String oldFingerprint, long flags)
	{
		return 1;
	}

	private static boolean OnExperimentalFeature(long inst, int feature)
	{
		return true;
	}

	private static void OnGraphicsUpdate(long inst, int x, int y, int width, int height)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnGraphicsUpdate(x, y, width, height);
	}

	private static void OnGraphicsResize(long inst, int width, int height, int bpp)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnGraphicsResize(width, height, bpp);
	}

	private static void OnRemoteClipboardChanged(long inst, String data)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnRemoteClipboardChanged(data);
	}

	private static void OnRemoteClipboardImageChanged(long inst, byte[] data)
	{
	}

	private static void OnPointerSet(long inst, int[] pixels, int width, int height, int hotX,
	                                 int hotY)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnPointerSet(pixels, width, height, hotX, hotY);
	}

	private static void OnPointerSetNull(long inst)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnPointerSetNull();
	}

	private static void OnPointerSetDefault(long inst)
	{
		UIEventListener u = ui(inst);
		if (u != null)
			u.OnPointerSetDefault();
	}

	private static void OnRailWindowUpdate(long inst, long windowId, int width, int height,
	                                       int[] pixels)
	{
	}

	private static void OnRailWindowMove(long inst, long windowId, int x, int y, int w, int h)
	{
	}

	private static void OnRailWindowHide(long inst, long windowId)
	{
	}

	private static void OnRailWindowDestroy(long inst, long windowId)
	{
	}

	private static void OnRailSessionEnd(long inst)
	{
	}

	private static void OnRailMonitoredDesktop(long inst, long[] windowIds, long activeWindowId)
	{
	}

	public interface EventListener
	{
		void OnPreConnect(long instance);

		void OnConnectionSuccess(long instance);

		void OnConnectionFailure(long instance);

		void OnDisconnecting(long instance);

		void OnDisconnected(long instance);
	}

	public interface UIEventListener
	{
		void OnSettingsChanged(int width, int height, int bpp);

		boolean OnAuthenticate(StringBuilder username, StringBuilder domain,
		                       StringBuilder password);

		void OnGraphicsUpdate(int x, int y, int width, int height);

		void OnGraphicsResize(int width, int height, int bpp);

		void OnRemoteClipboardChanged(String data);

		void OnPointerSet(int[] pixels, int width, int height, int hotX, int hotY);

		void OnPointerSetNull();

		void OnPointerSetDefault();
	}
}
