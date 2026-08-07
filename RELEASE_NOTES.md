# BrightMarket release notes

The top section is published as the body of the next GitHub Release. Add a new
section above the previous one when shipping something worth telling people
about; CI reads only down to the second `## ` heading.

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
