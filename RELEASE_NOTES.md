# BrightMarket release notes

The top section is published as the body of the next GitHub Release. Add a new
section above the previous one when shipping something worth telling people
about; CI reads only down to the second `## ` heading.

## v1.27

**One app could not be updated on its own, and the nightly channel is now set
per app instead of for the whole phone.**

*The bug.* An app's own page decided whether it had an update by comparing the
catalogue's stable release against what was on the phone — while the button
underneath installed whatever the channel resolved to. On nightlies those are two
different builds, so the page compared the wrong one, concluded there was nothing
to do, and labelled the button INSTALLED for an app the Updates tab was listing as
needing an update. There was no way to update that app by itself. Update all kept
working because it never asked the page. The page and the row now read one
resolved target and one shared verdict, so they cannot disagree about the same
app: the version, the download size, the What's new notes and the button label all
describe the build that will actually install.

*Nightly, per app.* The channel was one switch for the whole phone, which is the
wrong unit. Nobody wants every app on prereleases; they want the one app they are
helping test on prereleases and the keyboard left alone. Any app that publishes
nightlies now has a NIGHTLY line on its own page. Apps that publish none don't
show the line at all, rather than offering a choice that resolves to the same
build either way.

*The Settings switch is now the default.* It still does what it always did for
apps nobody has decided about, and it no longer overrides an app you set by hand —
so turning it off does not silently drag your one nightly app back to stable.
Settings says how many apps are set on their own.

## v1.26

**Icons are black and white and framed, and a description is no longer cut off
after two lines.**

*Black and white.* The conversion happens in the index repo, not here, and that
is the point: one answer, both clients, nothing to remember. Three of the
catalogued apps ship the stock green Android robot and one ships an orange bird,
and on this phone a colour icon isn't a brighter version of a grey one, it is the
wrong thing in the row.

*Framed.* A hairline white border around every icon, and around the lettered
fallback too. White artwork on a black ground, drawn on a black screen, has no
visible edge — so a small mark and a large one read as different amounts of
nothing, and a column of them has no left edge to line up on. The image is drawn
two dp inside the frame rather than under it, because an icon that fills its box
to the pixel paints over the hairline on any edge where its own artwork is light.
It is the only box in the app, and it is here because the icons are the only
thing in the app that isn't type.

*Four lines, not two.* v1.25 rewrote every summary to say what an app does now,
and then the browse row ended most of them in an ellipsis — which is the one
place a description is no use at all. Rows show four lines, and the index repo's
bound moved from 200 characters to 320 to match what the phone will actually
draw. Six summaries that had been trimmed to fit the old limit are back in full,
BrightControl's among them: it names six subsystems and had been cut to three.

## v1.25

**Every app now shows its icon.**

The list was fifty-nine names in the same typeface. Names are the right thing to
read once you know what you are looking for; finding a row you half-remember is
what a mark is for, and every other store has had one for fifteen years.

Icons come from the index, one 192px PNG per app, and they appear in the browse
list, in Updates, and at four grid units beside the summary on an app's own page.
The rows keep the height they always had -- the mark sits beside the two lines of
text rather than above them, so a fifty-nine app list still scrolls the same
distance.

Getting them was the work, and it happened in the index repo rather than here.
About half the catalogue is ours and keeps a `docs/icon.png`, which is free to
read. The other half is not, and there is nothing to guess at: AAPT2 minifies
resource names, so a real launcher icon is called `res/o-.png` and
`res/color/mtrl_chip_close_icon_tint.xml` is not an icon at all. The manifest's
icon attribute is the only reliable way in, followed through the resource table
and -- for an adaptive icon -- one level further into its background and
foreground layers, composited, then cropped to the 72/108 safe zone that a
launcher actually shows. Forty-one of fifty-nine have a mark this way.

The other eighteen declare no icon anywhere. Most are SDK tools, and LightOS
never asked them for one, so this is a real answer rather than a failure: those
apps carry no `icon` key, and the row draws the app's first letter. The letter is
also the loading state, because a letter turning into a mark reads as an image
arriving where a blank square reads as a broken row.

Icons are cached to disk, not just in memory. A screenshot is looked at once; an
icon is every row of the list on a phone that is regularly on no network at all,
and re-downloading fifty-nine of them on each cold start is both the slow way and
the wrong way to spend someone's data. A cached copy is re-fetched after a week,
and a stale copy is still drawn if that fetch fails.

**And the catalog now describes the apps as they are.** Every summary for a
gi-os app was rewritten against its current README. Sports claimed thirteen
leagues and has twenty-two. Authenticator and Sync still opened with IN
DEVELOPMENT. Roll's line predated video, BrightLibrary's predated comics and
Calibre, BrightMusic's predated radio fingerprinting, and BrightControl was still
described as "the wheel and camera button" about three subsystems ago.

## v1.24

**"Update all" updated one app, and a refresh on an app's own page couldn't
change what that page was showing.**

Two separate bugs, both of which made a button look dead.

*Update all.* `PackageInstaller.Session.commit()` returns the moment the session
is handed to the system — long before anything is installed. On the dialog path
the next thing that happens is a broadcast asking us to show the system's
confirmation screen, and the real answer arrives in a second broadcast after you
tap INSTALL. So an install "succeeding" only ever meant the session was accepted.
For one app that distinction is invisible. For a batch it was the whole bug: the
loop committed every session inside a few hundred milliseconds, each confirmation
activity launching over the last, and only the final dialog left standing could
be answered. One app updated. Everything else silently didn't, with no error,
because nothing had failed.

The batch is now a queue that waits for each install to actually reach a terminal
state before starting the next, so you get one dialog at a time, in order, and it
says at the end how many landed. A dialog nobody answers times out after five
minutes rather than stranding the rest of the queue behind it.

And a batch update was not recording which release it installed — the single-app
path did, the batch path had simply omitted the argument. That record is the only
exact input the update comparison has, so an app updated through "update all"
kept right on offering the same update afterwards. It records it now.

*Refresh on an app's page.* The detail screen holds an app object, not a package
name. Refreshing re-fetched the catalogue and rebuilt every one of those objects,
but nothing re-pointed the open page at its new one — so the single screen where
the button had to change something was the only screen where it never could. The
version line, and whether an update was offered, stayed exactly as they were
until you backed out and opened the page again. The open page now follows the
refresh.

Its toast also answers the question that was asked. Pressing refresh on one app
used to report a count for the whole catalogue ("3 updates available"), which
says nothing about the app you were looking at. It now reads
`Up to date · v1.9` or `Update available · v1.8 → v1.9`.

## v1.23

**Tracked apps that publish more than one APK could try to install the wrong
one — and Android's error for it names nothing.**

Obtainium (tracked because it isn't in the catalogue) ships nine assets per
release: three CPU-architecture builds, an F-Droid-flavored copy of each, and
a universal build. The F-Droid one isn't just a different build — it's a
different app, `dev.imranr.obtainium.fdroid` instead of `dev.imranr.obtainium`.
BrightMarket was taking whichever `.apk` GitHub happened to list first, which
is upload order and not something anyone chose. When that landed on the
F-Droid build while the phone already had the regular one, the install session
was opened for one app and handed another. Android notices and refuses —
as `INSTALL_FAILED_INVALID_APK`, which reads exactly like a corrupt download.
It wasn't: the file matched its own published checksum byte for byte.

Picking an asset out of a multi-APK release now prefers, in order: not an
F-Droid-style flavor build, not a CPU-specific split, and the largest file if
there's still a tie — which lands on the universal build every time, the same
one most people would pick by hand. And if a mismatch like this ever happens
again anyway, for this repo or a new one, the install now stops before
touching the installer at all and says which two applicationIds it saw,
instead of leaving Android's string to be mistaken for a bad download.

## v1.22


**If both routes to GitHub fail, the app offers to tell me.** Same chip as a
crash — a small prompt in the corner, which you can ignore — and tapping it sends
the exact reason rather than "it didn't work".

The report carries the HTTP code, how much of the rate limit was left, when it
resets, and precisely where the fallback gave up: whether the release page
redirected somewhere unexpected, or the assets fragment 404'd, or no `.apk` was
linked on it. Those are different problems with different fixes, and from the
outside they look identical.

It only asks when *both* the API and the plain web pages have failed, which
after v1.21 should be close to never. One prompt per repo per hour, so an app
that fails on every refresh doesn't nag you into turning reporting off.

## v1.21

**Tracked apps no longer depend on GitHub's API budget.**

v1.19 explained the "no APK release found" message properly and cut how often
the app asks. It didn't fix the underlying problem, which is worse than it
looked: GitHub allows 60 unauthenticated API requests an hour **per IP address**,
and on a mobile network that address is shared with everyone else behind the same
carrier NAT. The allowance can be gone before this phone has made a single
request. That's why one tracked app could work and then all of them fail at once
without you doing anything differently.

So when the API refuses, the app now asks github.com instead, which has no such
limit. It reads the same two things the API gave it — the newest release that
isn't a prerelease, and the name of the APK attached to it — from the ordinary
release pages. Checked against the eight apps this was reported with: all eight
resolve, every one matching what the API says.

You'll still see the rate limit message if both routes fail, which now means
something is genuinely wrong rather than that you have several apps.

## v1.20

**Room between Update and Uninstall.** There was supposed to be a gap. There
wasn't: the uninstall's own top padding is part of its tap target, and it
extended upward through almost the whole spacer, leaving about 1dp of clear
space between "update this app" and "remove it". Android asks for at least 8.

Now there's a wide gap, a short rule to mark uninstall as a different kind of
thing, and another gap — about 38dp of clear space, and no top padding on the
target so it can't creep back up into it. Update also got a proper tap target of
its own; it was the bare height of the letters, which is a strange thing for the
button you actually came to press.

## v1.19

**Forks now credit their upstream.** The catalogue index carries an `upstream`
field for every app that forks a community tool (BrightChat, BrightLibrary,
BrightMusic, BrightNews, BrightTransit, LightKeyboard, Chirp), and the app's
detail page shows a "Fork of github.com/owner/repo" line under the version.
The browse site shows the same credit as a link. Original apps are unchanged --
a blank field renders nothing.

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
