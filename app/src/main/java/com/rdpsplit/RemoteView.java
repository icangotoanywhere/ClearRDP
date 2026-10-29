package com.rdpsplit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Draws the remote desktop and turns touches into mouse input.
 *
 * Touchpad mode: one finger moves the pointer, tap = left click, tap then drag =
 * drag, long press = right click, two-finger tap = right click, two fingers up/down =
 * wheel, pinch = zoom (view follows the pointer).
 *
 * Direct mode: tap where you want to click, long press = right click, press and drag
 * = drag, two fingers = wheel (or pan when zoomed in), pinch = zoom.
 */
final class RemoteView extends View
{
	interface Host
	{
		void sendMouse(int x, int y, int flags);

		void onUserTouch();
	}

	static final int PTR_WHEEL = 0x0200, PTR_WHEEL_NEG = 0x0100, PTR_MOVE = 0x0800,
	                 PTR_DOWN = 0x8000, PTR_B1 = 0x1000, PTR_B2 = 0x2000;

	private final Host host;
	private final Handler ui = new Handler(Looper.getMainLooper());
	private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path arrow = new Path();
	private final float density;
	private final int touchSlop;

	private Bitmap bitmap;
	private Bitmap cursorBmp;
	private int hotX, hotY;
	private boolean cursorHidden;
	private String message;

	private float cx, cy; // pointer, remote pixels
	private float zoom = 1f, fit = 1f, tx, ty;
	private boolean touchpad = true;
	private float speed = 1.4f;

	// gesture state
	private float downX, downY, lastX, lastY;
	private long downTime, lastTapUpTime;
	private float lastTapX, lastTapY;
	private boolean moved, dragArmed, dragging, consumed, multi;
	private int multiMode; // 0 undecided, 1 pinch, 2 scroll/pan
	private float startSpan, lastSpan, lastMidX, lastMidY, scrollAcc;
	private long multiStart;
	private boolean multiMoved;
	private final Runnable longPress = this::onLongPress;

	RemoteView(Context ctx, Host host)
	{
		super(ctx);
		this.host = host;
		density = ctx.getResources().getDisplayMetrics().density;
		touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
		setBackgroundColor(Color.BLACK);
		textPaint.setColor(0xFFDDDDDD);
		textPaint.setTextSize(16 * density);
		textPaint.setTextAlign(Paint.Align.CENTER);
		arrowFill.setColor(Color.WHITE);
		arrowStroke.setColor(Color.BLACK);
		arrowStroke.setStyle(Paint.Style.STROKE);
		arrowStroke.setStrokeWidth(1.2f * density);
	}

	// ---------------------------------------------------------------- state

	void setTouchpad(boolean on)
	{
		touchpad = on;
		invalidate();
	}

	boolean isTouchpad()
	{
		return touchpad;
	}

	void setMessage(String m)
	{
		message = m;
		postInvalidate();
	}

	synchronized void setBitmap(Bitmap b)
	{
		bitmap = b;
		if (b != null)
		{
			cx = Math.min(Math.max(cx, 0), b.getWidth() - 1);
			cy = Math.min(Math.max(cy, 0), b.getHeight() - 1);
			if (cx == 0 && cy == 0)
			{
				cx = b.getWidth() / 2f;
				cy = b.getHeight() / 2f;
			}
		}
		ui.post(() -> {
			computeFit();
			requestLayout();
			invalidate();
		});
	}

	synchronized Bitmap getBitmap()
	{
		return bitmap;
	}

	void setCursor(int[] pixels, int w, int h, int hx, int hy)
	{
		Bitmap b = null;
		try
		{
			if (w > 0 && h > 0)
				b = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
		}
		catch (Throwable ignored)
		{
		}
		final Bitmap fb = b;
		ui.post(() -> {
			cursorBmp = fb;
			hotX = hx;
			hotY = hy;
			cursorHidden = false;
			invalidate();
		});
	}

	void setCursorHidden(boolean hidden)
	{
		ui.post(() -> {
			cursorHidden = hidden;
			invalidate();
		});
	}

	void setCursorDefault()
	{
		ui.post(() -> {
			cursorBmp = null;
			cursorHidden = false;
			invalidate();
		});
	}

	float getZoom()
	{
		return zoom;
	}

	void resetZoom()
	{
		zoom = 1f;
		computeFit();
		invalidate();
	}

	// ---------------------------------------------------------------- geometry

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh)
	{
		computeFit();
	}

	private void computeFit()
	{
		Bitmap b = bitmap;
		int vw = getWidth(), vh = getHeight();
		if (b == null || vw == 0 || vh == 0)
			return;
		float f = Math.min(vw / (float)b.getWidth(), vh / (float)b.getHeight());
		// a few pixels of rounding: draw 1:1 so text stays sharp
		if (f >= 0.985f && f <= 1.03f)
			f = 1f;
		fit = f;
		clampPan(true);
	}

	private float scale()
	{
		return fit * zoom;
	}

	private void clampPan(boolean center)
	{
		Bitmap b = bitmap;
		if (b == null)
			return;
		float s = scale();
		float bw = b.getWidth() * s, bh = b.getHeight() * s;
		int vw = getWidth(), vh = getHeight();
		if (bw <= vw)
			tx = (vw - bw) / 2f;
		else
		{
			if (center && zoom == 1f)
				tx = (vw - bw) / 2f;
			tx = Math.min(0, Math.max(vw - bw, tx));
		}
		if (bh <= vh)
			ty = (vh - bh) / 2f;
		else
		{
			if (center && zoom == 1f)
				ty = (vh - bh) / 2f;
			ty = Math.min(0, Math.max(vh - bh, ty));
		}
	}

	private void zoomAround(float factor, float px, float py)
	{
		float old = scale();
		float nz = Math.max(1f, Math.min(5f, zoom * factor));
		if (nz == zoom)
			return;
		zoom = nz;
		float s = scale();
		// keep remote point under (px,py) fixed
		float rx = (px - tx) / old, ry = (py - ty) / old;
		tx = px - rx * s;
		ty = py - ry * s;
		clampPan(false);
	}

	private void followCursor()
	{
		if (zoom <= 1f)
			return;
		float s = scale();
		float vx = tx + cx * s, vy = ty + cy * s;
		float m = 40 * density;
		int vw = getWidth(), vh = getHeight();
		if (vx < m)
			tx += m - vx;
		else if (vx > vw - m)
			tx -= vx - (vw - m);
		if (vy < m)
			ty += m - vy;
		else if (vy > vh - m)
			ty -= vy - (vh - m);
		clampPan(false);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void onDraw(Canvas c)
	{
		Bitmap b = bitmap;
		if (b != null)
		{
			float s = scale();
			c.save();
			c.translate(tx, ty);
			c.scale(s, s);
			bmpPaint.setFilterBitmap(s != 1f);
			synchronized (this)
			{
				c.drawBitmap(b, 0, 0, bmpPaint);
			}
			c.restore();
			if (!cursorHidden)
				drawCursor(c, tx + cx * s, ty + cy * s, s);
		}
		if (message != null)
		{
			float y = getHeight() / 2f;
			for (String line : message.split("\n"))
			{
				c.drawText(line, getWidth() / 2f, y, textPaint);
				y += textPaint.getTextSize() * 1.4f;
			}
		}
	}

	private void drawCursor(Canvas c, float x, float y, float s)
	{
		Bitmap cb = cursorBmp;
		if (cb != null)
		{
			float l = x - hotX * s, t = y - hotY * s;
			c.drawBitmap(cb, null, new RectF(l, t, l + cb.getWidth() * s, t + cb.getHeight() * s),
			             bmpPaint);
			return;
		}
		float u = 1.1f * density;
		arrow.reset();
		arrow.moveTo(x, y);
		arrow.lineTo(x, y + 17 * u);
		arrow.lineTo(x + 4.5f * u, y + 13 * u);
		arrow.lineTo(x + 8 * u, y + 20 * u);
		arrow.lineTo(x + 10.5f * u, y + 19 * u);
		arrow.lineTo(x + 7 * u, y + 12 * u);
		arrow.lineTo(x + 12.5f * u, y + 12 * u);
		arrow.close();
		c.drawPath(arrow, arrowFill);
		c.drawPath(arrow, arrowStroke);
	}

	// ---------------------------------------------------------------- input

	private void send(int flags)
	{
		Bitmap b = bitmap;
		if (b == null)
			return;
		int x = Math.round(Math.min(Math.max(cx, 0), b.getWidth() - 1));
		int y = Math.round(Math.min(Math.max(cy, 0), b.getHeight() - 1));
		host.sendMouse(x, y, flags);
	}

	private void moveTo(float vx, float vy)
	{
		float s = scale();
		cx = (vx - tx) / s;
		cy = (vy - ty) / s;
		clampCursor();
	}

	private void clampCursor()
	{
		Bitmap b = bitmap;
		if (b == null)
			return;
		cx = Math.min(Math.max(cx, 0), b.getWidth() - 1);
		cy = Math.min(Math.max(cy, 0), b.getHeight() - 1);
	}

	private void click(int button)
	{
		send(PTR_DOWN | button);
		send(button);
	}

	/** Right-click at the current pointer position (used by the 右键 button). */
	void dispatchRightClick()
	{
		send(PTR_MOVE);
		click(PTR_B2);
	}

	private void onLongPress()
	{
		if (moved || multi || dragging || consumed)
			return;
		consumed = true;
		performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
		if (!touchpad)
		{
			moveTo(downX, downY);
			send(PTR_MOVE);
		}
		click(PTR_B2);
		invalidate();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e)
	{
		if (bitmap == null)
			return true;
		host.onUserTouch();
		final int action = e.getActionMasked();
		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
			{
				downX = lastX = e.getX();
				downY = lastY = e.getY();
				downTime = SystemClock.uptimeMillis();
				moved = consumed = multi = dragging = false;
				dragArmed = touchpad && (downTime - lastTapUpTime < 300) &&
				            Math.hypot(downX - lastTapX, downY - lastTapY) < 60 * density;
				ui.postDelayed(longPress, 550);
				break;
			}
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				ui.removeCallbacks(longPress);
				if (dragging)
				{
					send(PTR_B1);
					dragging = false;
				}
				if (e.getPointerCount() == 2)
				{
					multi = true;
					multiMode = 0;
					multiMoved = false;
					multiStart = SystemClock.uptimeMillis();
					startSpan = lastSpan = span(e);
					lastMidX = midX(e);
					lastMidY = midY(e);
					scrollAcc = 0;
				}
				break;
			}
			case MotionEvent.ACTION_MOVE:
			{
				if (multi)
				{
					if (e.getPointerCount() >= 2)
						handleMulti(e);
					break;
				}
				float x = e.getX(), y = e.getY();
				if (!moved && Math.hypot(x - downX, y - downY) > touchSlop)
				{
					moved = true;
					ui.removeCallbacks(longPress);
					if (touchpad && dragArmed)
					{
						dragging = true;
						send(PTR_DOWN | PTR_B1);
					}
					else if (!touchpad && !consumed)
					{
						moveTo(downX, downY);
						send(PTR_MOVE);
						dragging = true;
						send(PTR_DOWN | PTR_B1);
					}
				}
				if (moved)
				{
					if (touchpad)
					{
						float s = scale();
						float dx = x - lastX, dy = y - lastY;
						float d = (float)Math.hypot(dx, dy);
						float accel = 1f + Math.min(1.5f, d / (14 * density)) * 0.6f;
						cx += dx / s * speed * accel;
						cy += dy / s * speed * accel;
						clampCursor();
						followCursor();
					}
					else
						moveTo(x, y);
					send(PTR_MOVE);
					invalidate();
				}
				lastX = x;
				lastY = y;
				break;
			}
			case MotionEvent.ACTION_POINTER_UP:
			{
				if (e.getPointerCount() == 2 && multi && multiMode == 0 && !multiMoved &&
				    SystemClock.uptimeMillis() - multiStart < 350)
				{
					if (!touchpad)
					{
						moveTo(lastMidX, lastMidY);
						send(PTR_MOVE);
					}
					click(PTR_B2);
					consumed = true;
					invalidate();
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			{
				ui.removeCallbacks(longPress);
				long now = SystemClock.uptimeMillis();
				if (dragging)
				{
					send(PTR_B1);
					dragging = false;
				}
				else if (!multi && !moved && !consumed && now - downTime < 400)
				{
					if (!touchpad)
					{
						moveTo(downX, downY);
						send(PTR_MOVE);
					}
					click(PTR_B1);
					lastTapUpTime = now;
					lastTapX = downX;
					lastTapY = downY;
				}
				invalidate();
				break;
			}
			case MotionEvent.ACTION_CANCEL:
			{
				ui.removeCallbacks(longPress);
				if (dragging)
				{
					send(PTR_B1);
					dragging = false;
				}
				break;
			}
		}
		return true;
	}

	private void handleMulti(MotionEvent e)
	{
		float sp = span(e), mx = midX(e), my = midY(e);
		float dSpan = sp - startSpan;
		if (multiMode == 0)
		{
			if (Math.abs(dSpan) > 28 * density)
				multiMode = 1;
			else if (Math.hypot(mx - lastMidX, my - lastMidY) > 6 * density)
				multiMode = 2;
		}
		if (multiMode != 0)
			multiMoved = true;
		if (multiMode == 1)
		{
			if (lastSpan > 0)
				zoomAround(sp / lastSpan, mx, my);
			if (touchpad)
				followCursor();
			invalidate();
		}
		else if (multiMode == 2)
		{
			float dx = mx - lastMidX, dy = my - lastMidY;
			if (!touchpad && zoom > 1.01f)
			{
				tx += dx;
				ty += dy;
				clampPan(false);
				invalidate();
			}
			else
			{
				// natural scrolling: fingers up = content up = wheel down
				scrollAcc += dy;
				float step = 22 * density;
				while (Math.abs(scrollAcc) >= step)
				{
					if (scrollAcc > 0)
					{
						send(PTR_WHEEL | 0x0078);
						scrollAcc -= step;
					}
					else
					{
						send(PTR_WHEEL | PTR_WHEEL_NEG | 0x0088);
						scrollAcc += step;
					}
				}
			}
		}
		lastSpan = sp;
		lastMidX = mx;
		lastMidY = my;
	}

	private static float span(MotionEvent e)
	{
		return (float)Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
	}

	private static float midX(MotionEvent e)
	{
		return (e.getX(0) + e.getX(1)) / 2f;
	}

	private static float midY(MotionEvent e)
	{
		return (e.getY(0) + e.getY(1)) / 2f;
	}

	/** Region of the bitmap that changed, in remote pixels. */
	void invalidateRemote(int x, int y, int w, int h)
	{
		float s = scale();
		Rect r = new Rect((int)Math.floor(tx + x * s) - 1, (int)Math.floor(ty + y * s) - 1,
		                  (int)Math.ceil(tx + (x + w) * s) + 1, (int)Math.ceil(ty + (y + h) * s) + 1);
		postInvalidate(r.left, r.top, r.right, r.bottom);
	}
}
