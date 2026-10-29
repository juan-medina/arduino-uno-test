// SPDX-FileCopyrightText: 2026 Juan Medina
// SPDX-License-Identifier: MIT

package com.juanmedina.arduino;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.led.LedCanvas;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.util.Delay;

@Board(ArduinoUnoR4WiFiBoard.class)
public final class App {
    private static final int BAT_WIDTH = 12;
    private static final int BAT_HEIGHT = 8;
    private static final int ANIM_FRAMES = 32;

    // 3 wing-flap poses (8 row bitmasks per pose, 12 bits per row), kept in Flash
    private static final int[] BAT_FLAPS = {
        // Pose 0: Classic level wings (mid-stroke)
        0x090, 0x6F6, 0xFFF, 0xFFF, 0x9F9, 0x0F0, 0x060, 0x000,
        // Pose 1: Wings raised high (upstroke)
        0xC93, 0xEF7, 0x7FE, 0x3FC, 0x0F0, 0x0F0, 0x060, 0x000,
        // Pose 2: Wings swept low (downstroke)
        0x090, 0x0F0, 0x3FC, 0x7FE, 0xFFF, 0xCF3, 0x861, 0x000
    };

    // 8-step wing-flap cycle: Mid -> Up -> Up -> Mid -> Down -> Down -> Mid -> Mid
    private static final int[] FLAP_CYCLE = {
        0, 1, 1, 0, 2, 2, 0, 0
    };

    // 16-angle cosine and sine lookup tables scaled by 64 (0..360 degrees in 22.5-degree steps)
    private static final int[] COS_64 = {
        64, 59, 45, 24, 0, -24, -45, -59, -64, -59, -45, -24, 0, 24, 45, 59
    };
    private static final int[] SIN_64 = {
        0, 24, 45, 59, 64, 59, 45, 24, 0, -24, -45, -59, -64, -59, -45, -24
    };

    // 32-frame timeline: 16 frames upright flapping (0..15), then 16 frames 360-deg spin (16..31)
    private static final int[] FRAME_ANGLE = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        1, 2, 3, 4, 5, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 15
    };

    // Fixed-point inverse scale per frame (64 = 100% size, >64 = zoomed out, <64 = zoomed in)
    private static final int[] FRAME_INV_SCALE = {
        64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
        70, 76, 82, 86, 82, 76, 70, 64, 64, 60, 56, 52, 48, 52, 56, 60
    };

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(ArduinoUno.getGpio().getBuiltinLed());
        LedMatrix matrix = ArduinoUno.getLedMatrix();
        matrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.MAX_WIDTH];
        int width = LedCanvas.width();

        int step = 0;
        while (true) {
            int angleIdx = FRAME_ANGLE[step];
            int invScale = FRAME_INV_SCALE[step];
            int flapPose = FLAP_CYCLE[step & 7] << 3;
            int cos = COS_64[angleIdx];
            int sin = SIN_64[angleIdx];

            LedCanvas.clear(frame);
            for (int y = 0; y < LedCanvas.HEIGHT; y++) {
                int dy = (y << 1) - 6;
                for (int x = 0; x < width; x++) {
                    int dx = (x << 1) - 11;
                    int rotX = (dx * cos - dy * sin) * invScale;
                    int rotY = (dx * sin + dy * cos) * invScale;
                    int srcX = ((rotX >> 12) + 11) >> 1;
                    int srcY = ((rotY >> 12) + 6) >> 1;

                    if (srcX >= 0 && srcX < BAT_WIDTH && srcY >= 0 && srcY < BAT_HEIGHT) {
                        int rowMask = BAT_FLAPS[flapPose + srcY];
                        if ((rowMask & (1 << (11 - srcX))) != 0) {
                            LedCanvas.setPixel(frame, x, y);
                        }
                    }
                }
            }

            LedCanvas.show(frame);
            if (angleIdx == 0) {
                led.high();
            } else {
                led.low();
            }
            Delay.millis(65);
            step = (step + 1) & (ANIM_FRAMES - 1);
        }
    }
}
