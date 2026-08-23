package com.dumuzeyn.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Central renderer for every runtime Voltune icon used by the application UI. */
final class AppIconRenderer {
    private static final int BRAND_BACKGROUND = 0xff090218;

    private AppIconRenderer() {
    }

    static Bitmap renderLogo(Context context, int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Bitmap source = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.voltune_icon_foreground);
        Canvas canvas = new Canvas(bitmap);
        int inset = Math.round(safeSize * 0.08f);
        canvas.drawBitmap(source, new Rect(0, 0, source.getWidth(), source.getHeight()),
                new RectF(inset, inset, safeSize - inset, safeSize - inset), null);
        source.recycle();
        return bitmap;
    }

    static Bitmap renderTile(Context context, int backgroundColor,
            int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(BRAND_BACKGROUND);
        float radius = safeSize * 0.22f;
        canvas.drawRoundRect(0, 0, safeSize, safeSize, radius, radius, background);

        int inset = Math.round(safeSize * 0.10f);
        Bitmap logo = renderLogo(context, primaryColor, secondaryColor, safeSize - inset * 2);
        canvas.drawBitmap(logo, inset, inset, null);
        logo.recycle();
        return bitmap;
    }

    static Bitmap renderPreview(Context context, int backgroundColor,
            int primaryColor, int secondaryColor, int size) {
        try {
            return renderTile(context, backgroundColor, primaryColor, secondaryColor, size);
        } catch (RuntimeException error) {
            return BitmapFactory.decodeResource(context.getResources(),
                    context.getApplicationInfo().icon);
        }
    }

    static Bitmap renderLauncherPreview(Context context, ComponentName component,
            int fallbackBackground, int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        try {
            Drawable icon = context.getPackageManager().getActivityIcon(component);
            Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, safeSize, safeSize);
            icon.draw(new Canvas(bitmap));
            return bitmap;
        } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException error) {
            return renderPreview(context, fallbackBackground,
                    primaryColor, secondaryColor, safeSize);
        }
    }

}
