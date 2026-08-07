# BrightMarket release notes

The top section is published as the body of the next GitHub Release. Add a new
section above the previous one when shipping something worth telling people
about; CI reads only down to the second `## ` heading.

## v1.6

**Installed apps really do move into the installed section now.** The previous
fix listened for the package event but registered the listener only while the
app was on screen — and the system installer is a full screen, so BrightMarket
is stopped for the entire install. The listener was unregistered at exactly the
moment the event fired. It now lives for as long as the screen does.

## v1.5

**Installed apps now actually move into the installed section.** Returning from
the system installer dialog resumes the app, but the install finishes a moment
after that — so the phone still reported the app as absent and the list never
updated. BrightMarket now listens for the package event itself, which is the
authoritative signal and also catches installs that finish while it is in the
foreground.

## v1.4

**Imported apps show up whether or not they're installed.** An Obtainium
import matched the apps BrightMarket indexes and then did nothing visible with
them, because the Updates tab only ever listed what the phone reported as
installed. They now appear under **IN YOUR LIST, NOT INSTALLED**, one tap from
installing, and are watched from then on.

**Remove things.** Anything in your list can be removed, and any tracked repo
can be forgotten.

**Scanned repos can finally be updated.** A GitHub release doesn't say which
package it installs, so a scanned repo had no applicationId — meaning the app
could never tell whether it was installed and never offered an update for it.
The APK is now read with the platform's own parser as it installs, which is the
one moment that information exists, and remembered.

**The wheel and shake-to-report.** Both from light-common, the same shared layer
every other app in the portfolio uses: the wheel scrolls the lists, and shaking
the phone files a report.

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
