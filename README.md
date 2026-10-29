# NeoClient 1.1.0

Fabric client mod for Minecraft 1.21.11. Requires Java 21, Fabric Loader 0.18+, and Fabric API 0.141.1+.

## Current modules
- ClickGUI: **Right Shift**, with a centered dark glass-style layout, left-side sections, rounded controls, and blue, violet, mint, and amber accents. The Visuals section can switch the frosted-glass UI surfaces on or off.
- Camera: **F6** Freecam and **Left Alt** Freelook. Camera rotation is handled on the detached camera; Freelook leaves the player rotation intact. Movement keys still move the player in Freelook. Freecam uses WASD, Space/Shift, sprint boost, and optional scroll speed.
- HUD editor: open Right Shift → HUD → **Edit HUD layout**, then drag HUD panels. Positions save locally. Current draggable panels include Info/Coordinates, Donut Tracker, Waypoints, Spotify, Floating Lyrics, Keystrokes, Armor Status, Potion Effects, and Auction.
- HUD modules: FPS/coordinates/direction, Keystrokes, armor durability, potion timers, earnings/balance tracker, custom crosshair, waypoint distance, and Spotify.
- Visual settings: fullbright, clear weather, zoom with eased transitions, no hurt camera, no view bobbing, theme accents, crosshair size/gap, first-person viewmodel X/Y/Z adjustments, and optional visibility for entities with the Invisibility potion effect.
- Spotify HUD: album art, track/artist/album, progress, synced/floating lyrics with fades, theme accents, and rounded panels.
- Waypoints: enter name and coordinates under Right Shift → Points; the HUD shows a square, name, and distance. Nether distance uses the 8:1 Overworld scale. Waypoint scale is adjustable under HUD.
- Auction tracker: set an item, starting bid, and countdown under Right Shift → Auction. Matching payment chat lines add to the top bid and can record a bidder name. At zero it displays red **AUCTION ENDED** and the recorded winner/amount. This widget only reads chat; it does not issue commands or payments.

## Module keybinds
Open **Options → Controls → Key Binds → NeoClient**. Assign keys to the HUD module toggles, Freecam, Freelook, Zoom, and menu. Newly added HUD-module bindings default to unbound.

## Build from GitHub
1. Upload the contents of this project folder into the root of your GitHub repository. Keep `.github/workflows/build.yml`.
2. Push/commit to the default branch. GitHub Actions runs **Build NeoClient** automatically; it can also be started manually from Actions.
3. Download the `neoclient-jar` artifact from a successful run. Install `neoclient-1.1.0.jar` from it (not the `-sources.jar`) in `.minecraft/mods`.

The workflow uses Java 21 and Gradle 9.2.1. Locally, run `gradle build --no-daemon` with those tools installed; output is in `build/libs/`.

## Spotify setup
1. In the Spotify Developer Dashboard, register the Redirect URI exactly as `http://127.0.0.1:8888/callback`.
2. In the client, open Right Shift → Spotify, enter that app's Client ID, and connect.

Tokens are stored in Minecraft's `config/neoclient.json`. Keep that file private and never commit it.

## Donut balance and auction notes
The balance tracker reads the visible sidebar scoreboard and matching chat snapshots; it cannot query DonutSMP's private balance API. Enable it and make sure the server displays a balance line in the sidebar or chat so the first matching value sets the baseline. `balanceRegex` in `config/neoclient.json` (also editable in Right Shift → Visual) controls the pattern and capture group 1 must contain the amount.

The Donut Profit Tracker has compact and detailed HUD modes, movable positioning through the HUD editor, a configurable local daily reset hour, revenue, spending, and net totals. It records matching earnings/spending lines in `config/neoclient-transactions.csv`; edit `donutRegex` or `donutSpendRegex` if server chat wording differs.

The auction widget uses `auctionRegex` to identify payment lines. Capture group 1 may be the bidder and the last capture group must be the amount; for older one-group patterns, the only group is treated as the amount and the name is inferred from common chat formats. These patterns only observe incoming chat.

The glass styling is a client-drawn translucent UI/HUD treatment. It does not install or replace a Minecraft world shader pack.

## Fair-play scope
The client focuses on cosmetic and informational overlays. It does not include Anti-invis or other features that reveal hidden players, bypass server rules, or automate gameplay/economy actions.
