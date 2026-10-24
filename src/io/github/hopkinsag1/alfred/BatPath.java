// SPDX-License-Identifier: Apache-2.0
package io.github.hopkinsag1.alfred;

import android.graphics.Path;

/**
 * The Bat Cave bat, as the design kit's bat.svg draws it: a 64 by 32 artboard,
 * the same moveTo, lineTo and quadTo calls as the SVG path.
 */
final class BatPath {
    static final float WIDTH = 64, HEIGHT = 32;

    private BatPath() {}

    static Path build() {
        Path p = new Path();
        p.moveTo(32, 5); p.lineTo(30.6f, 5.2f); p.lineTo(29.2f, .8f); p.lineTo(27.6f, 5.6f);
        p.quadTo(18, 8, 3, 3); p.quadTo(4.5f, 8, 3, 12); p.quadTo(8, 9, 13, 13); p.quadTo(19, 12, 23, 17);
        p.quadTo(28, 16, 30, 24); p.quadTo(31, 22, 32, 30); p.quadTo(33, 22, 34, 24); p.quadTo(36, 16, 41, 17);
        p.quadTo(45, 12, 51, 13); p.quadTo(56, 9, 61, 12); p.quadTo(59.5f, 8, 61, 3); p.quadTo(46, 8, 36.4f, 5.6f);
        p.lineTo(34.8f, .8f); p.lineTo(33.4f, 5.2f); p.close();
        return p;
    }
}
