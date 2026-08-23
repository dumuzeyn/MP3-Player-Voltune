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
    private static final int BRAND_BACKGROUND = 0xff090218;

    private AppIconRenderer() {
    }

    static Bitmap renderLogo(Context context, int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Bitmap source = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.voltune_icon_foreground);
        int inset = Math.round(safeSize * 0.08f);
        int contentSize = Math.max(1, safeSize - inset * 2);
        Bitmap scaledSource = Bitmap.createScaledBitmap(source, contentSize, contentSize, true);
        Bitmap scaled = scaledSource.copy(Bitmap.Config.ARGB_8888, true);
        if (scaledSource != source) {
            scaledSource.recycle();
        }
        source.recycle();
        tintLogo(scaled, primaryColor, secondaryColor);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(scaled, inset, inset, null);
        scaled.recycle();
        return bitmap;
    }

    static Bitmap renderTile(Context context, int backgroundColor,
            int primaryColor, int secondaryColor, int size) {
        int safeSize = Math.max(1, size);
        Bitmap bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(Color.alpha(backgroundColor) == 0
                ? BRAND_BACKGROUND : backgroundColor);
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

    private static void tintLogo(Bitmap bitmap, int primaryColor, int secondaryColor) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int source = pixels[index];
                int alpha = Color.alpha(source);
                if (alpha == 0) {
                    continue;
                }
                float gradient = width == 1 ? 0f : x / (float) (width - 1);
                int themed = blend(primaryColor, secondaryColor, gradient);
                float luminance = (Color.red(source) * 0.2126f
                        + Color.green(source) * 0.7152f
                        + Color.blue(source) * 0.0722f) / 255f;
                float shade = 0.72f + luminance * 0.48f;
                pixels[index] = Color.argb(alpha,
                        clamp(Math.round(Color.red(themed) * shade)),
                        clamp(Math.round(Color.green(themed) * shade)),
                        clamp(Math.round(Color.blue(themed) * shade)));
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static int blend(int first, int second, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * amount),
                Math.round(Color.green(first) * inverse + Color.green(second) * amount),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * amount));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

}
