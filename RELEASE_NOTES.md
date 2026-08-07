# BrightMarket release notes

The top section is published as the body of the next GitHub Release. Add a new
section above the previous one when shipping something worth telling people
about; CI reads only down to the second `## ` heading.

## v1.3

**Obtainium import now actually keeps your apps.** It read the export, counted
what matched, and dropped the rest — a migration that only looked finished.
Everything is kept now: apps BrightMarket indexes become normal entries, and
everything else is tracked for updates under **NOT IN BRIGHTMARKET**.

Entries with no GitHub repo behind them are reported rather than silently lost.
There is nothing to check for updates against, so they can't be tracked, and
saying so is better than a number that doesn't add up.

Re-importing the same export doesn't duplicate anything — repos are matched
case-insensitively, the way GitHub treats them.

## v1.2

**Refresh.** A refresh control in the top bar, which refetches the catalogue
*and* re-checks every tracked repo. Those are two halves of one question — "is
anything newer" — and refreshing only one would leave a stale answer on screen.

**Icons.** The bottom bar is icons now, drawn rather than shipped as assets.
That also lifts a real constraint: LightBottomBar allows five icon items but
only three when any of them is text.

**Markdown in descriptions and release notes.** Every app's notes contain
markdown — today all twenty include at least `**Full Changelog**` — and shown as
plain text those asterisks appear literally, which reads as a bug. Bold, italic,
code, links, headings and bullets now render. It is deliberately a small reader
rather than a library: a full parser is a dependency, and this is two screens of
text on a phone with very little storage.

**Focus mode is stricter.** With it on, the phone shows Updates and Settings
only — the separate "Installed" tab was rendering the same screen as Updates, so
it was a duplicate of itself. Focus mode can no longer be switched off in
Settings; the only way back is scanning the OFF code on the desktop catalogue,
which is now large and alone at the bottom of that page. Turning it *on* is
still one tap in Settings.

That is a commitment device, not a security boundary, and it is worth saying
plainly: the code is an ordinary `brightmarket://focus/off` link and anyone
determined can make one. It makes the easy path the intentional one. It does not
stop the owner of the device.

## v1.1

**Scan a QR to install.** The camera is in, carried over from LightQR — ZXing
decoding the luminance plane, which works on LightOS because it needs no Google
Play Services. SCAN sits in the top bar rather than the bottom, because
LightBottomBar allows at most three items when any of them is text and
Browse/Updates/Settings already fills it.

Two kinds of code work:

- a `brightmarket://` link from the desktop catalogue, which opens that app
- **any GitHub repo URL**, which starts tracking it for updates

**Apps that aren't in BrightMarket.** That second one is the point: plenty of
what people sideload will never be in a curated index — a fork, a personal
build, something nobody has submitted. Those now appear under **NOT IN
BRIGHTMARKET** and get update checks like anything else.

They are deliberately marked rather than blended in. An indexed app has passed
the submission checks and ships with a hash this app verifies before installing.
A tracked repo has had none of that, and **its download cannot be verified** —
nothing generated a hash for it. Presenting the two identically would imply a
guarantee only one of them has.

**Obtainium import now imports everything.** It used to report a count of what
didn't match and drop the rest, which is the worst kind of migration: it looks
finished. Everything in the export is now kept, listed as unlisted where
BrightMarket doesn't index it.

**Also in this release**

- Updates tab: what's installed, what's newer, and Update All
- Focus mode, chosen on first launch, for browsing on a desktop instead
- Screenshots on the detail page
- The marketplace can update itself, and does so last in a batch — installing it
  kills the process, so anything queued behind it would silently never run

## v1.0

First release. Browse the index, install with the system installer after
verifying the download against the published hash, and check for updates.
