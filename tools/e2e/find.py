"""Print the centre ("x y") of a uiautomator node, or exit 1.

  find.py "TEXT"     first node whose text is TEXT ("TEXT*" matches a prefix)
  find.py @lasttab   the rightmost clickable node in the lowest row (the bottom bar's
                     last tab: BrightMarket's tabs are icons with no text)
"""
import re, sys
xml = sys.stdin.read()
want = sys.argv[1]
nodes = []
for m in re.finditer(r'<node ([^>]*)>', xml):
    a = m.group(1)
    t = re.search(r'text="([^"]*)"', a)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', a)
    if b:
        nodes.append((t.group(1) if t else "", 'clickable="true"' in a, tuple(map(int, b.groups()))))
if want == "@lasttab":
    clickable = [n for n in nodes if n[1]]
    if not clickable:
        sys.exit(1)
    low = max(n[2][3] for n in clickable)
    row = [n for n in clickable if n[2][3] == low]
    x1, y1, x2, y2 = max(row, key=lambda n: n[2][0])[2]
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
for text, _, (x1, y1, x2, y2) in nodes:
    if text == want or (want.endswith("*") and text.startswith(want[:-1])):
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
