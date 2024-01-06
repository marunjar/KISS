package fr.neamar.kiss.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_CIRCLE;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_HEXAGON;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_OCTAGON;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_ROUND_RECT;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_SQUARE;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_SQUIRCLE;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_SYSTEM;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_TEARDROP_BL;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_TEARDROP_BR;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_TEARDROP_RND;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_TEARDROP_TL;
import static fr.neamar.kiss.utils.DrawableUtils.SHAPE_TEARDROP_TR;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
public class DrawableUtilsTest {

    @BeforeEach
    void setUp() {

    }

    @AfterEach
    void tearDown() {

    }

    @Test
    public void drawableToBitmapForBitmapDrawable() {
        BitmapDrawable drawable = mock(BitmapDrawable.class);
        Bitmap bitmap = mock(Bitmap.class);
        when(drawable.getBitmap()).thenReturn(bitmap);

        Bitmap result = DrawableUtils.drawableToBitmap(drawable);

        assertThat(result, sameInstance(bitmap));
    }

    @Test
    public void drawableToBitmapWithoutSize() {
        Bitmap bitmap = mock(Bitmap.class);
        try (MockedStatic<Bitmap> staticBitmap = mockStatic(Bitmap.class);
             MockedConstruction<Canvas> constructedCanvas = mockConstruction(Canvas.class, (mock, context) -> {
                 doNothing().when(mock).setDrawFilter(new PaintFlagsDrawFilter(0, Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
                 when(mock.getWidth()).thenReturn(1);
                 when(mock.getHeight()).thenReturn(1);
             })) {
            Drawable drawable = mock(Drawable.class);
            lenient().when(drawable.getIntrinsicWidth()).thenReturn(-1);
            lenient().when(drawable.getIntrinsicHeight()).thenReturn(-1);
            staticBitmap.when(() -> Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)).thenReturn(bitmap);
            doNothing().when(drawable).setBounds(0, 0, 1, 1);

            Bitmap result = DrawableUtils.drawableToBitmap(drawable);

            assertThat(result, sameInstance(bitmap));
        }
    }

    @Test
    public void drawableToBitmapWithSize() {
        Bitmap bitmap = mock(Bitmap.class);
        try (MockedStatic<Bitmap> staticBitmap = mockStatic(Bitmap.class);
             MockedConstruction<Canvas> constructedCanvas = mockConstruction(Canvas.class, (mock, context) -> {
                 doNothing().when(mock).setDrawFilter(new PaintFlagsDrawFilter(0, Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
                 when(mock.getWidth()).thenReturn(10);
                 when(mock.getHeight()).thenReturn(10);
             })) {
            Drawable drawable = mock(Drawable.class);
            lenient().when(drawable.getIntrinsicWidth()).thenReturn(10);
            lenient().when(drawable.getIntrinsicHeight()).thenReturn(10);
            staticBitmap.when(() -> Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)).thenReturn(bitmap);
            doNothing().when(drawable).setBounds(0, 0, 10, 10);

            Bitmap result = DrawableUtils.drawableToBitmap(drawable);

            assertThat(result, sameInstance(bitmap));
        }
    }

    @ParameterizedTest
    @MethodSource("shapeProvider")
    public void getScaleToFit(int shape, float scale) {
        assertThat(DrawableUtils.getScaleToFit(shape), is(scale));
    }

    private static Stream<Arguments> shapeProvider() {
        return Stream.of(
                Arguments.of(SHAPE_SYSTEM, 0.2071f),
                Arguments.of(SHAPE_CIRCLE, 0.2071f),
                Arguments.of(SHAPE_SQUARE, 0.0f),
                Arguments.of(SHAPE_SQUIRCLE, 0.1f),
                Arguments.of(SHAPE_ROUND_RECT, 0.05f),
                Arguments.of(SHAPE_TEARDROP_BR, 0.2071f),
                Arguments.of(SHAPE_TEARDROP_BL, 0.2071f),
                Arguments.of(SHAPE_TEARDROP_TL, 0.2071f),
                Arguments.of(SHAPE_TEARDROP_TR, 0.2071f),
                Arguments.of(SHAPE_TEARDROP_RND, 0.0f),
                Arguments.of(SHAPE_HEXAGON, 0.26f),
                Arguments.of(SHAPE_OCTAGON, 0.25f)
        );
    }

}