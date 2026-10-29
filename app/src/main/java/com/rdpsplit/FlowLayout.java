package com.rdpsplit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left-to-right, wrapping to new rows; each row is stretched to full width. */
final class FlowLayout extends ViewGroup
{
	private final int gap;

	FlowLayout(Context ctx, int gapPx)
	{
		super(ctx);
		gap = gapPx;
	}

	@Override
	protected void onMeasure(int wSpec, int hSpec)
	{
		int width = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
		int x = 0, rowH = 0, total = 0;
		int n = getChildCount();
		for (int i = 0; i < n; i++)
		{
			View c = getChildAt(i);
			if (c.getVisibility() == GONE)
				continue;
			c.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
			          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			int cw = c.getMeasuredWidth();
			if (x > 0 && x + gap + cw > width)
			{
				total += rowH + gap;
				x = 0;
				rowH = 0;
			}
			x += (x > 0 ? gap : 0) + cw;
			rowH = Math.max(rowH, c.getMeasuredHeight());
		}
		total += rowH;
		setMeasuredDimension(MeasureSpec.getSize(wSpec),
		                     total + getPaddingTop() + getPaddingBottom());
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		int width = r - l - getPaddingLeft() - getPaddingRight();
		int n = getChildCount();
		int i = 0, y = getPaddingTop();
		while (i < n)
		{
			// collect one row
			int start = i, used = 0, rowH = 0, count = 0;
			while (i < n)
			{
				View c = getChildAt(i);
				if (c.getVisibility() == GONE)
				{
					i++;
					continue;
				}
				int cw = c.getMeasuredWidth();
				if (count > 0 && used + gap + cw > width)
					break;
				used += (count > 0 ? gap : 0) + cw;
				rowH = Math.max(rowH, c.getMeasuredHeight());
				count++;
				i++;
			}
			if (count == 0)
				break;
			// stretch the row to fill the width
			int extra = (width - used) / count;
			int x = getPaddingLeft();
			for (int k = start; k < i; k++)
			{
				View c = getChildAt(k);
				if (c.getVisibility() == GONE)
					continue;
				int cw = c.getMeasuredWidth() + extra;
				c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
				          MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY));
				c.layout(x, y, x + cw, y + rowH);
				x += cw + gap;
			}
			y += rowH + gap;
		}
	}
}
