package io.github.icangotoanywhere.clearrdp;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * Invisible view that owns input focus so the phone's own soft keyboard stays up.
 * Committed text and key presses are forwarded to the remote session; text that is
 * still being composed (e.g. pinyin before a candidate is picked) stays local.
 */
final class KeyInputView extends View
{
	interface Listener
	{
		void onText(CharSequence text);

		void onComposing(CharSequence text);

		/** Android key event from the IME or a hardware keyboard. */
		void onKey(KeyEvent ev);

		/**
		 * Back key seen before the IME. Return false to let the IME handle it (it closes
		 * the keyboard), true to consume it.
		 */
		boolean onBackPreIme(KeyEvent ev);
	}

	private Listener listener;

	KeyInputView(Context ctx)
	{
		super(ctx);
		setFocusable(true);
		setFocusableInTouchMode(true);
	}

	void setListener(Listener l)
	{
		listener = l;
	}

	@Override
	public boolean onCheckIsTextEditor()
	{
		return true;
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo out)
	{
		out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
		out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN |
		                 EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION;
		out.initialSelStart = 0;
		out.initialSelEnd = 0;
		return new Conn();
	}

	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_BACK && listener != null)
			return listener.onBackPreIme(event);
		return super.onKeyPreIme(keyCode, event);
	}

	private final class Conn extends BaseInputConnection
	{
		Conn()
		{
			super(KeyInputView.this, true);
		}

		private void clearAll()
		{
			Editable e = getEditable();
			if (e != null)
			{
				removeComposingSpans(e);
				e.clear();
			}
			if (listener != null)
				listener.onComposing("");
		}

		@Override
		public boolean commitText(CharSequence text, int newCursorPosition)
		{
			clearAll();
			if (listener != null && text != null && text.length() > 0)
				listener.onText(text);
			return true;
		}

		@Override
		public boolean setComposingText(CharSequence text, int newCursorPosition)
		{
			boolean r = super.setComposingText(text, newCursorPosition);
			if (listener != null)
				listener.onComposing(text == null ? "" : text);
			return r;
		}

		@Override
		public boolean setComposingRegion(int start, int end)
		{
			return true;
		}

		@Override
		public boolean finishComposingText()
		{
			Editable e = getEditable();
			CharSequence pending = "";
			if (e != null)
			{
				int a = getComposingSpanStart(e);
				int b = getComposingSpanEnd(e);
				if (a >= 0 && b > a)
					pending = e.subSequence(a, b).toString();
			}
			clearAll();
			if (pending.length() > 0 && listener != null)
				listener.onText(pending);
			return true;
		}

		@Override
		public boolean deleteSurroundingText(int beforeLength, int afterLength)
		{
			Editable e = getEditable();
			if (e != null && e.length() > 0)
				return super.deleteSurroundingText(beforeLength, afterLength);
			for (int i = 0; i < beforeLength; i++)
				tap(KeyEvent.KEYCODE_DEL);
			for (int i = 0; i < afterLength; i++)
				tap(KeyEvent.KEYCODE_FORWARD_DEL);
			return true;
		}

		@Override
		public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength)
		{
			return deleteSurroundingText(beforeLength, afterLength);
		}

		private void tap(int kc)
		{
			if (listener == null)
				return;
			listener.onKey(new KeyEvent(KeyEvent.ACTION_DOWN, kc));
			listener.onKey(new KeyEvent(KeyEvent.ACTION_UP, kc));
		}

		@Override
		public boolean sendKeyEvent(KeyEvent event)
		{
			Editable e = getEditable();
			if (e != null && e.length() > 0 && event.getKeyCode() == KeyEvent.KEYCODE_DEL)
			{
				// still composing locally: edit the local buffer, nothing goes remote
				if (event.getAction() == KeyEvent.ACTION_DOWN)
				{
					e.delete(e.length() - 1, e.length());
					if (listener != null)
						listener.onComposing(e.toString());
				}
				return true;
			}
			if (listener != null)
				listener.onKey(event);
			return true;
		}

		@Override
		public boolean performEditorAction(int actionCode)
		{
			tap(KeyEvent.KEYCODE_ENTER);
			return true;
		}

		@Override
		public CharSequence getTextBeforeCursor(int n, int flags)
		{
			Editable e = getEditable();
			return e == null ? "" : e.toString();
		}

		@Override
		public CharSequence getTextAfterCursor(int n, int flags)
		{
			return "";
		}
	}
}
