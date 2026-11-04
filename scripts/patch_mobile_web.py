#!/usr/bin/env python3
"""Apply small mobile-WebView defaults to EffectCraft's existing web entry point."""
from __future__ import annotations

import argparse
from pathlib import Path

OLD_VIEWPORT = '<meta name="viewport" content="width=device-width, initial-scale=1">'
NEW_VIEWPORT = '<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">'
MOBILE_STYLE = '''
    /* Android WebView: let egui receive gestures directly and avoid page-level overscroll. */
    html, body {
      width: 100%;
      height: 100%;
      height: 100dvh;
      overscroll-behavior: none;
      -webkit-user-select: none;
      user-select: none;
      -webkit-touch-callout: none;
    }
    canvas {
      touch-action: none;
      -webkit-tap-highlight-color: transparent;
    }
    input, textarea, [contenteditable="true"] {
      -webkit-user-select: text;
      user-select: text;
    }
'''
MARKER = '/* EFFECTCRAFT_ANDROID_MOBILE_PATCH */'


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('index_html', type=Path)
    args = parser.parse_args()
    path = args.index_html
    source = path.read_text(encoding='utf-8')

    if OLD_VIEWPORT in source:
        source = source.replace(OLD_VIEWPORT, NEW_VIEWPORT, 1)
    elif NEW_VIEWPORT not in source:
        raise SystemExit('Expected viewport meta tag was not found; upstream HTML changed.')

    if MARKER not in source:
        closing = source.find('</style>')
        if closing < 0:
            raise SystemExit('Expected inline style block was not found; upstream HTML changed.')
        source = source[:closing] + f'    {MARKER}\n' + MOBILE_STYLE + source[closing:]

    path.write_text(source, encoding='utf-8')


if __name__ == '__main__':
    main()
