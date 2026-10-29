package io.github.icangotoanywhere.clearrdp;

import android.view.KeyEvent;

/** Windows virtual-key codes and Android keycode → VK mapping. */
final class Vk
{
	/**
	 * FreeRDP's VK→scancode lookup only finds "extended" keys (Win, arrows, Home/End,
	 * PgUp/PgDn, Insert/Delete, ...) when this flag is set; without it Win maps to
	 * nothing and the arrows map to the numeric keypad.
	 */
	static final int EXT = 0x100;

	static final int BACK = 0x08, TAB = 0x09, RETURN = 0x0D, ESCAPE = 0x1B, SPACE = 0x20;
	static final int PRIOR = 0x21 | EXT, NEXT = 0x22 | EXT, END = 0x23 | EXT, HOME = 0x24 | EXT;
	static final int LEFT = 0x25 | EXT, UP = 0x26 | EXT, RIGHT = 0x27 | EXT, DOWN = 0x28 | EXT;
	static final int INSERT = 0x2D | EXT, DELETE = 0x2E | EXT, SNAPSHOT = 0x2C | EXT;
	static final int LWIN = 0x5B | EXT, APPS = 0x5D | EXT;
	static final int F1 = 0x70;
	static final int LSHIFT = 0xA0, LCONTROL = 0xA2, LMENU = 0xA4;

	private Vk()
	{
	}

	/** Letter or digit → VK, else 0. */
	static int fromChar(char c)
	{
		if (c >= 'a' && c <= 'z')
			return 'A' + (c - 'a');
		if (c >= 'A' && c <= 'Z')
			return c;
		if (c >= '0' && c <= '9')
			return c;
		switch (c)
		{
			case ' ':
				return SPACE;
			case '\n':
			case '\r':
				return RETURN;
			case '\t':
				return TAB;
			case ';':
				return 0xBA;
			case '=':
				return 0xBB;
			case ',':
				return 0xBC;
			case '-':
				return 0xBD;
			case '.':
				return 0xBE;
			case '/':
				return 0xBF;
			case '`':
				return 0xC0;
			case '[':
				return 0xDB;
			case '\\':
				return 0xDC;
			case ']':
				return 0xDD;
			case '\'':
				return 0xDE;
			default:
				return 0;
		}
	}

	/** Non-printing Android keys → VK, else 0. */
	static int fromKeyCode(int kc)
	{
		if (kc >= KeyEvent.KEYCODE_A && kc <= KeyEvent.KEYCODE_Z)
			return 'A' + (kc - KeyEvent.KEYCODE_A);
		if (kc >= KeyEvent.KEYCODE_0 && kc <= KeyEvent.KEYCODE_9)
			return '0' + (kc - KeyEvent.KEYCODE_0);
		if (kc >= KeyEvent.KEYCODE_F1 && kc <= KeyEvent.KEYCODE_F12)
			return F1 + (kc - KeyEvent.KEYCODE_F1);
		switch (kc)
		{
			case KeyEvent.KEYCODE_DEL:
				return BACK;
			case KeyEvent.KEYCODE_FORWARD_DEL:
				return DELETE;
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_NUMPAD_ENTER:
				return RETURN;
			case KeyEvent.KEYCODE_TAB:
				return TAB;
			case KeyEvent.KEYCODE_ESCAPE:
				return ESCAPE;
			case KeyEvent.KEYCODE_SPACE:
				return SPACE;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				return LEFT;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				return RIGHT;
			case KeyEvent.KEYCODE_DPAD_UP:
				return UP;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				return DOWN;
			case KeyEvent.KEYCODE_MOVE_HOME:
				return HOME;
			case KeyEvent.KEYCODE_MOVE_END:
				return END;
			case KeyEvent.KEYCODE_PAGE_UP:
				return PRIOR;
			case KeyEvent.KEYCODE_PAGE_DOWN:
				return NEXT;
			case KeyEvent.KEYCODE_INSERT:
				return INSERT;
			case KeyEvent.KEYCODE_CTRL_LEFT:
			case KeyEvent.KEYCODE_CTRL_RIGHT:
				return LCONTROL;
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
				return LMENU;
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
				return LSHIFT;
			case KeyEvent.KEYCODE_META_LEFT:
			case KeyEvent.KEYCODE_META_RIGHT:
				return LWIN;
			case KeyEvent.KEYCODE_SYSRQ:
				return SNAPSHOT;
			case KeyEvent.KEYCODE_MENU:
				return APPS;
			default:
				return 0;
		}
	}

	static boolean isModifier(int vk)
	{
		return vk == LCONTROL || vk == LMENU || vk == LSHIFT || vk == LWIN;
	}
}
