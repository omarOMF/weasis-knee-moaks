package kneemoaks.ui;

import java.awt.geom.Path2D;

/**
 * Reads SVG path data (M L H V C S Q T Z, absolute and relative) into a Java2D path, so the
 * illustration shapes can be written exactly as in the design mockup.
 */
public final class SvgPath {

  private final String d;
  private int pos;

  private SvgPath(String d) {
    this.d = d;
  }

  public static Path2D.Double parse(String d) {
    return new SvgPath(d).run();
  }

  private Path2D.Double run() {
    Path2D.Double p = new Path2D.Double();
    double x = 0;
    double y = 0;
    double startX = 0;
    double startY = 0;
    double lastCx = 0;
    double lastCy = 0;
    char cmd = 0;
    char prev = 0;
    while (true) {
      skipSeparators();
      if (pos >= d.length()) {
        break;
      }
      char c = d.charAt(pos);
      if (Character.isLetter(c)) {
        cmd = c;
        pos++;
      } else if (cmd == 0) {
        throw new IllegalArgumentException("Path data must start with a command: " + d);
      } else if (cmd == 'M') {
        cmd = 'L';
      } else if (cmd == 'm') {
        cmd = 'l';
      }
      boolean rel = Character.isLowerCase(cmd);
      double ox = rel ? x : 0;
      double oy = rel ? y : 0;
      switch (Character.toUpperCase(cmd)) {
        case 'M' -> {
          x = ox + num();
          y = oy + num();
          p.moveTo(x, y);
          startX = x;
          startY = y;
        }
        case 'L' -> {
          x = ox + num();
          y = oy + num();
          p.lineTo(x, y);
        }
        case 'H' -> {
          x = ox + num();
          p.lineTo(x, y);
        }
        case 'V' -> {
          y = oy + num();
          p.lineTo(x, y);
        }
        case 'C' -> {
          double x1 = ox + num();
          double y1 = oy + num();
          double x2 = ox + num();
          double y2 = oy + num();
          x = ox + num();
          y = oy + num();
          p.curveTo(x1, y1, x2, y2, x, y);
          lastCx = x2;
          lastCy = y2;
        }
        case 'S' -> {
          boolean smooth = "CcSs".indexOf(prev) >= 0;
          double x1 = smooth ? 2 * x - lastCx : x;
          double y1 = smooth ? 2 * y - lastCy : y;
          double x2 = ox + num();
          double y2 = oy + num();
          x = ox + num();
          y = oy + num();
          p.curveTo(x1, y1, x2, y2, x, y);
          lastCx = x2;
          lastCy = y2;
        }
        case 'Q' -> {
          double x1 = ox + num();
          double y1 = oy + num();
          x = ox + num();
          y = oy + num();
          p.quadTo(x1, y1, x, y);
          lastCx = x1;
          lastCy = y1;
        }
        case 'T' -> {
          boolean smooth = "QqTt".indexOf(prev) >= 0;
          double x1 = smooth ? 2 * x - lastCx : x;
          double y1 = smooth ? 2 * y - lastCy : y;
          x = ox + num();
          y = oy + num();
          p.quadTo(x1, y1, x, y);
          lastCx = x1;
          lastCy = y1;
        }
        case 'Z' -> {
          p.closePath();
          x = startX;
          y = startY;
        }
        default -> throw new IllegalArgumentException("Unsupported path command " + cmd + " in " + d);
      }
      prev = cmd;
    }
    return p;
  }

  private void skipSeparators() {
    while (pos < d.length()) {
      char c = d.charAt(pos);
      if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') {
        pos++;
      } else {
        break;
      }
    }
  }

  private double num() {
    skipSeparators();
    int start = pos;
    if (pos < d.length() && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) {
      pos++;
    }
    boolean dot = false;
    boolean exp = false;
    while (pos < d.length()) {
      char c = d.charAt(pos);
      if (Character.isDigit(c)) {
        pos++;
      } else if (c == '.' && !dot && !exp) {
        dot = true;
        pos++;
      } else if ((c == 'e' || c == 'E') && !exp) {
        exp = true;
        pos++;
        if (pos < d.length() && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) {
          pos++;
        }
      } else {
        break;
      }
    }
    if (start == pos) {
      throw new IllegalArgumentException("Number expected at " + start + " in " + d);
    }
    return Double.parseDouble(d.substring(start, pos));
  }
}
