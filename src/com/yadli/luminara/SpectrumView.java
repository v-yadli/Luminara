package com.yadli.luminara;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {

	public SpectrumView(Context context, AttributeSet attrs) {
		super(context, attrs);
		spectrum = new float[24];
		paint = new Paint();
		paint.setAntiAlias(true);
	}

	private float[] spectrum;
	private int h = 200, w = 24 * 50;
	private Paint paint;

	@Override
	public void onDraw(Canvas canvas) {
		float barW = w / 24.0f;
		paint.setColor(0xFF000000);
		canvas.drawLine(0, 0, w, 0, paint);
		canvas.drawLine(0, 0, 0, h, paint);
		canvas.drawLine(w, 0, w, h, paint);
		canvas.drawLine(0, h, w, h, paint);
		for (int i = 0; i < 24; ++i) {
			int strength = (int) (spectrum[i] * 255);
			paint.setColor(0xFF000000 | strength | (strength << 8) | (strength << 16));
			canvas.drawRect(barW * i + 1, h * (1 - spectrum[i]), barW * (i + 1) - 1, h,
					paint);
		}

		paint.setColor(0xFFEEEEEE);

		for (int i = 0; i < 23; ++i)
			canvas.drawLine(barW * i, 0, barW * i, h, paint);
		
		paint.setColor(pivotColor);
		
		float pivotCenter = w * pivotPercent;
		canvas.drawRect(pivotCenter - pivotIntensity * 5, 0, pivotCenter + pivotIntensity * 5, h, paint);

	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(measureWidth(widthMeasureSpec),
				measureHeight(heightMeasureSpec));
	}

	private int measureHeight(int heightMeasureSpec) {
		int result = 0;
		int specMode = MeasureSpec.getMode(heightMeasureSpec);
		int specSize = MeasureSpec.getSize(heightMeasureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			// Measure the text (beware: ascent is a negative number)
			result = 200;
			if (specMode == MeasureSpec.AT_MOST) {
				// Respect AT_MOST value if that was what is called for by
				// measureSpec
				result = Math.min(result, specSize);
			}
		}
		h = result;
		return result;
	}

	private int measureWidth(int widthMeasureSpec) {
		int result = 0;
		int specMode = MeasureSpec.getMode(widthMeasureSpec);
		int specSize = MeasureSpec.getSize(widthMeasureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			// Measure the text
			result = 24 * 50;
			if (specMode == MeasureSpec.AT_MOST) {
				// Respect AT_MOST value if that was what is called for by
				// measureSpec
				result = Math.min(result, specSize);
			}
		}

		w = result;
		return result;
	}

	public void update(float[] data) {
		for (int i = 0; i < 24; ++i)
			spectrum[i] = data[i];
		invalidate();
	}
	
	private float pivotPercent = 0.f;
	private int pivotColor;
	private float pivotIntensity = 0.f;
	
	public void setPivot(float percent, int r, int g, int b, float intensity)
	{
		pivotPercent = percent;
		pivotColor = 0xFF000000 | (r<<16) | (g<<8) | b;
		pivotIntensity = intensity;
	}
}
