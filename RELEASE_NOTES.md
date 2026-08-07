# BrightMarket release notes

The top section is published as the body of the next GitHub Release. Add a new
section above the previous one when shipping something worth telling people
about; CI reads only down to the second `## ` heading.

## v1.14

**Uninstall actually works.** It needed `REQUEST_DELETE_PACKAGES` in the
manifest, which wasn't there. Without it Android refuses the request before any
UI appears, so the button did nothing regardless of how big it was — v1.12 made
the tap target larger, which was a real problem but not this one. The permission
grants no power to remove anything: the system still shows its own confirmation
and you still have to agree. It only buys the right to ask.

It's also gone from the downloads list. One place to uninstall from, and it's
the app's own page — a destructive action repeated down every row of a list you
scroll is asking for it.

## v1.13

**Nightly builds are a channel you choose now, not the default.**

Until today every push to this repo cut a release, and BrightMarket and
Obtainium both offered it to everybody within the hour. A typo fix and a real
feature arrived identically, and anyone who updates promptly was testing work in
progress without having agreed to. Twelve releases went out in one day. That is
fine for a project with one user and wrong for one people rely on.

A push now publishes a **nightly**, marked as a prerelease. The catalogue has
always skipped prereleases, so a nightly reaches nobody who hasn't asked for
one. An official release is asked for deliberately.

**Settings → Updates** picks your channel. Official releases only is the
default, and staying there means you'll see fewer updates and each one will have
been meant. Nightly builds gets you everything as it's made, including the ones
that turn out to be wrong.

A nightly is checked exactly as hard as a release: the index downloads it,
hashes it, and reads its versionCode out of the APK, so an install off the
nightly channel verifies the same way. And a nightly is only ever offered when
it's genuinely ahead — cut an official release after the last nightly and
everyone on the channel moves to it, rather than sitting on something older than
the people who didn't opt in.

Turning the channel off never downgrades you; Android won't install backwards.
It stops offering nightlies, and the next official release catches you up.

## v1.12

**The uninstall button is now big enough to press.** It shipped as small text
with no padding around it, so the thing you could actually hit was about the
height of the letters — roughly 20dp, against the 48dp Android treats as the
minimum. Near misses landed on the row underneath and opened the app's page,
which looks exactly like a button that doesn't work.

It's full-size on both the list and the app's own page now, with the padding
inside the tap target rather than around it. And if the system uninstaller
can't be opened for some reason, it says so instead of failing silently — a tap
that does nothing and says nothing is the same thing as a broken button from
where you're sitting.

## v1.11

**Uninstall is in the app now.** Removing something you'd installed meant going
to LightOS's own settings and finding it there, which is several screens from
the list of installed apps and not somewhere anyone thinks to look. There's an
UNINSTALL under each row in Updates and on each app's page. It sits below the
row rather than beside the name on purpose — putting a destructive action next
to the one you tap constantly is how it gets tapped by accident. Android still
shows its own confirmation, and BrightMarket won't offer to uninstall itself.

**Add an app by pasting a link, not just by scanning one.** The scan screen has
a field above the viewfinder that takes a GitHub repo, a `brightmarket.gzl.dev`
link, or a direct `.apk` URL. Scanning assumed the thing you wanted was on
another screen you could point a camera at; often it arrived in a message on
this phone, and there was no way to use it.

The field sits above the camera permission prompt, so declining the camera no
longer means you can't add apps at all. A direct `.apk` link installs once and
isn't tracked afterwards — a file on a web server has no releases to watch, and
a row that could never update would just look broken. It has to be `https`:
there's no hash to check an unlisted APK against, so the connection is the only
thing standing between you and whatever else the network felt like sending.

## v1.10

**You have to reinstall this one.** Sorry. Every build up to v1.9 was signed with a
key that lived in this repository with its password written three lines under it,
which meant anybody could produce an APK that Android would happily accept as an
update to BrightMarket. That was fine when the only person installing it was me. It
is not fine for an app whose entire job is installing software.

The key is now a CI secret and the replacement has never been in the tree. Android
identifies an app by package name *and* signing certificate and refuses to install
across a change of either, so this one time the update has to be an uninstall and a
fresh install. Your installed apps are not touched — only BrightMarket's own
settings, which is Focus mode and your tracked repos.

Building locally still works and still gives you an installable APK; it is signed
with your debug key and won't install over a release, which is the correct outcome
rather than an inconvenience.

Also: a licence file, at last (MIT, same as the rest), and the store's own listing
finally has screenshots.

## v1.9

**The catalogue has its own address: `brightmarket.gzl.dev`.** The old
`gi-os.github.io/brightmarket-index` links still work and always will — GitHub
redirects them — but the app now fetches the index from the short domain
directly rather than through a redirect, and the "turn Focus mode off" screen
tells you the address you'd actually type.

The install link is `brightmarket.gzl.dev/apk`, which always points at the
current signed release. That is what the setup QR encodes now, so a code printed
in a README doesn't go stale the next time this ships.

## v1.8

**Refresh tells you what it found.** A refresh that turns up nothing new
changes nothing on screen, which is indistinguishable from the button not
working. It now reports the result — "up to date", or how many updates are
waiting — and shows that it is busy while it runs. The fetch on launch stays
silent; announcing that one would be noise on every start.

## v1.7

**Fixes the installed list properly.** Since Android 11 an app cannot see other
installed packages at all unless it says so in its manifest — every lookup threw
and was quietly read as "not installed". That is why nothing ever joined the
installed section, why apps plainly sitting on the phone were listed as not
installed, and why no update was ever offered for anything. BrightMarket now
declares the visibility it needs.

The two previous attempts at this were fixing the wrong thing: the app was
asking the right question at the right moment and being refused an answer.

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
