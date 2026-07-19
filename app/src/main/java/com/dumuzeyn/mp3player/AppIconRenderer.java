package com.dumuzeyn.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/** Central renderer for every runtime Voltune icon used by the application UI. */
final class AppIconRenderer {
    private static final int SOURCE_PRIMARY = 0xff8a2cc8;
    private static final int SOURCE_SECONDARY = 0xffffd000;

    private AppIconRenderer() {
    }

    static Bitmap renderLogo(Context context, int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Drawable logo = context.getResources().getDrawable(R.drawable.ic_music_vector_user);
        logo.setBounds(0, 0, safeSize, safeSize);
        logo.draw(new Canvas(bitmap));
        replaceAccentColors(bitmap, primaryColor, secondaryColor);
        return bitmap;
    }

    static Bitmap renderTile(Context context, int backgroundColor,
            int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(backgroundColor);
        float radius = safeSize * 0.22f;
        canvas.drawRoundRect(0, 0, safeSize, safeSize, radius, radius, background);

        int inset = Math.round(safeSize * 0.08f);
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

    private static void replaceAccentColors(Bitmap bitmap, int primaryColor, int secondaryColor) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            int alpha = Color.alpha(pixel);
            if (alpha == 0) {
                continue;
            }
            int replacement = colorDistance(pixel, SOURCE_SECONDARY)
                    < colorDistance(pixel, SOURCE_PRIMARY) ? secondaryColor : primaryColor;
            pixels[index] = Color.argb(alpha, Color.red(replacement),
                    Color.green(replacement), Color.blue(replacement));
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static int colorDistance(int first, int second) {
        int red = Color.red(first) - Color.red(second);
        int green = Color.green(first) - Color.green(second);
        int blue = Color.blue(first) - Color.blue(second);
        return red * red + green * green + blue * blue;
    }
}
