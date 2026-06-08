# Google Stitch UI Design Prompt
## Catholic Catechetical Assistant — Android App

---

Design a complete set of Android UI screens and reusable components for a Catholic catechetical assistant app called **"Catholic Catechist"**. The app runs on Android (Material Design 3), targets compact Android phones (primary device: Samsung Galaxy A12, 720×1280 dp), and is built in Jetpack Compose. All screens must be vertically scrollable and work at small viewport sizes. Bottom actions must always remain reachable without scrolling.

---

## Visual Identity & Theme

- **Style:** Reverent, clean, and readable. Inspired by Catholic liturgical design — dignified but approachable. Not ornate or overly religious in iconography; it should feel like a well-designed study tool.
- **Color palette:**
  - Primary: Deep navy or rich royal blue (e.g., `#1A3A5C` or similar) — evokes liturgical blue
  - Secondary: Warm gold or amber (e.g., `#C9A84C`) — used for accents, Scripture cards, and labels
  - Surface / Background: Off-white or very light warm grey (`#F8F5F0`) — easy on the eyes for reading long text
  - Error: Standard Material3 red
  - User chat bubble: Primary color (navy/blue) with white text
  - Assistant chat bubble: Light surface variant with dark text
  - CCC paragraph cards: White surface with navy primary label
  - Scripture verse cards: Warm gold/amber secondary container with dark italic text
- **Typography:**
  - Headings and labels: A clean serif or semi-serif (e.g., Georgia, Playfair Display, or a clean sans like Inter) — readable at small sizes
  - Body text (CCC paragraphs, Scripture verses): Slightly generous line height (~1.5) for readability of long doctrinal text
  - Message bubbles: `bodyMedium` size (~14sp), `bodySmall` (~12sp) for source cards
- **Iconography:** Material Icons — cross or book icon for the app bar, settings gear, delete/clear, expand/collapse chevrons, send arrow, eye/eye-off for password visibility

---

## Screens to Design

### 1. Splash / First-Launch Seeding Screen

**Purpose:** Shown only on first launch while the local database is being populated from bundled JSON files (can take 5–15 seconds).

**Layout:**
- Full-screen centered layout
- App logo / cross icon centered vertically
- App name: **"Catholic Catechist"** in large serif heading
- Subtitle: *"Your guide to the Catechism of the Catholic Church"*
- Below: A circular `LinearProgressIndicator` (indeterminate)
- Status text below progress bar: *"Preparing your Catechism library… (first launch only)"*
- The background uses the app's off-white/warm background color
- No navigation controls visible — this screen is non-interactive

---

### 2. Chat Screen (Main Screen)

**Purpose:** The primary user-facing screen. Users type a doctrinal question, the app retrieves relevant CCC paragraphs and Scripture verses, sends them to an AI, and displays the grounded answer with expandable source cards.

**Layout — Top Bar:**
- `TopAppBar` with title **"Catholic Catechist"** in the primary color or white-on-primary
- Right side actions:
  - Trash/delete icon button → clears conversation history (with confirmation dialog, see below)
  - Settings gear icon button → navigates to Settings screen

**Layout — Message List (center, scrollable):**
- `LazyColumn` taking all space between top bar and bottom input bar
- Padding: 16dp on all sides, 8dp vertical spacing between messages
- **User message bubbles:** Right-aligned; rounded corners (16dp top-start, 16dp top-end, 16dp bottom-start, 4dp bottom-end); primary color background; white text; max width ~280dp; `bodyMedium` text
- **Assistant message bubbles:** Left-aligned; rounded corners (16dp top-start, 16dp top-end, 4dp bottom-start, 16dp bottom-end); `surfaceVariant` background; `onSurfaceVariant` text; max width ~300dp; `bodyMedium` text
- **Loading state:** When waiting for AI response, show a centered `CircularProgressIndicator` (32dp) as the last item in the list, below any existing messages
- **Empty state:** When no messages exist, show a centered placeholder:
  - A subtle cross or book icon (48dp, muted primary color)
  - Text: *"Ask a question about Catholic teaching"*
  - Subtext: *"Your answer will be grounded in the Catechism of the Catholic Church"*

**Layout — Source Cards (below each assistant bubble):**
Each assistant message has an expandable sources section directly below the bubble:
- A small `TextButton` labeled **"Show Sources ▼"** / **"Hide Sources ▲"** (`labelSmall` style, primary color)
- When expanded (animated):
  - Section label: **"CCC Paragraphs"** (`labelMedium`, primary color, bold)
  - One or more `CccParagraphCard` components (see Components)
  - Spacer
  - Section label: **"Scripture"** (`labelMedium`, secondary/gold color, bold)
  - One or more `ScriptureVerseCard` components (see Components)

**Layout — Bottom Input Bar:**
- Pinned to the bottom of the screen, above the system navigation bar (`navigationBarsPadding()`)
- White/surface background with a top divider line
- Horizontal row:
  - Left: Multi-line `OutlinedTextField` with hint text *"Ask about Catholic doctrine…"*, fills available width, max 4 lines before scrolling
  - Right: `IconButton` with a send/arrow-right icon; primary color when text is entered, disabled/grey when empty or loading
- The entire bar is disabled (greyed out, send button inactive) while the AI is loading

**Layout — Error Banner:**
- Appears anchored to the bottom of the screen (above the input bar) when an error occurs
- A `Snackbar`-style strip with:
  - Error message text (e.g., "Invalid API key. Please check your key in Settings.")
  - An X dismiss button on the right
  - Background: error color (red/Material3 `errorContainer`)
- Error messages to design for:
  - *"Invalid API key. Please check your key in Settings."*
  - *"Rate limit reached. Please wait a moment and try again."*
  - *"No internet connection. Showing CCC sources only."*
  - *"The AI service is temporarily unavailable. Try again shortly."*
  - *"No CCC paragraphs matched your question. Try rephrasing."*

**Layout — API Key Setup Banner:**
- A dismissible informational banner shown at the top of the message list (as a pinned item) when no API key has been set
- Soft blue or gold background (info style, not error)
- Text: *"To get AI-powered answers, add your free Gemini API key in Settings."*
- A **"Set Up Now"** text link or small button on the right
- An X dismiss button

**Layout — Clear Conversation Confirmation Dialog:**
- Standard Material3 `AlertDialog`
- Title: **"Clear conversation?"**
- Body: *"This will delete all messages and conversation history. This cannot be undone."*
- Buttons: **"Cancel"** (text button) | **"Clear"** (filled button, error/destructive style)

**Layout — Offline Mode Display (no LLM answer):**
- When offline or no API key, the assistant message bubble shows:
  - A muted info-style bubble (grey/surfaceVariant)
  - Text: *"No internet connection. Here are the CCC paragraphs most relevant to your question:"*
  - Immediately followed by the expanded source cards (already visible, no "Show Sources" toggle needed — sources show automatically in offline mode)

---

### 3. Search / Study Screen

**Purpose:** Direct CCC browsing without an AI call. User searches the Catechism and gets a list of matching CCC paragraph cards to read. This is a study tool, not a duplicate chat experience.

**Layout — Top:**
- Screen title: **"Search the Catechism"** in the top bar (no icons needed, back navigation if coming from nav)
- A `SearchBar` or `OutlinedTextField` below the top bar:
  - Hint: *"Search CCC by keyword…"* 
  - A search icon on the left
  - A clear (X) icon on the right when text is entered

**Layout — Results:**
- `LazyColumn` of `CccParagraphCard` components (expanded by default in this view, or still tappable to expand)
- Result count label above the list: *"5 paragraphs found"* (`labelSmall`, muted)
- Empty results state: centered text *"No paragraphs found. Try different keywords."*
- Loading state: `LinearProgressIndicator` below the search bar

**Layout — Empty / Initial State:**
- Before the user types anything, show:
  - A subtle book or cross icon (48dp)
  - Text: *"Search by topic, keyword, or doctrine"*
  - Subtext: *"e.g. Eucharist, Purgatory, Baptism, Grace"*

---

### 4. Settings Screen

**Purpose:** User enters and manages their Gemini API key, and can clear conversation history.

**Layout:**
- Vertically scrollable `Column` using full screen height with `navigationBarsPadding()`
- Top bar: **"Settings"** title, back arrow on the left

**Section 1 — API Key:**
- Section header label: **"AI Configuration"** (`titleSmall`, muted, uppercase)
- `OutlinedTextField` for API key input:
  - Label: *"Gemini API Key"*
  - Input is masked (password-style) by default
  - A show/hide eye icon toggle on the right end of the field
  - Full width with generous padding
- Helper text below the field: *"Your key is stored securely on this device only."*
- A **"Save API Key"** filled button, full-width below the field
- A clickable info text link: *"Get a free key at aistudio.google.com →"* (secondary/gold color, `labelMedium`)
- On successful save: a `Snackbar` appears at the bottom: *"API key saved."*

**Section 2 — Data:**
- Section header label: **"Data"** (`titleSmall`, muted, uppercase)
- A `ListItem`-style row: *"Clear Conversation History"* with a trailing trash icon button
  - Tapping opens the same confirmation dialog as in Chat screen

**Section 3 — About:**
- Section header label: **"About"** (`titleSmall`, muted, uppercase)
- App name: **"Catholic Catechist"**
- Version: *"Version 1.0"*
- A one-line description: *"Answers grounded in the Catechism of the Catholic Church."*

---

## Reusable Components to Design

### A. CccParagraphCard

A tappable expandable card representing one CCC paragraph.

- **Collapsed state:**
  - Card with white/surface background, slight elevation or outlined border
  - Header row (full width):
    - Left: **"CCC §123"** label (`labelLarge`, primary/navy color, bold)
    - Right: chevron-down icon (`ExpandMore`)
  - Below header: first ~120 characters of the paragraph text followed by `…` (`bodySmall`, `onSurfaceVariant`, 1 line clipped)
- **Expanded state:**
  - Same header, chevron-up icon (`ExpandLess`)
  - Full paragraph text rendered below (`bodySmall`, `onSurface`, generous line height, scrollable if very long)
- Tap anywhere on the card to toggle
- Smooth animated expand/collapse transition
- Card fills full available width; used inside message bubbles and in the Search screen

---

### B. ScriptureVerseCard

A non-expandable card displaying one Scripture verse.

- Card with warm gold/amber `secondaryContainer` background
- Top: verse reference in bold (`labelMedium`, secondary/gold color) — e.g., **"Romans 3:23"**
- Below: verse text in italic (`bodySmall`, `onSecondaryContainer`, generous line height)
- Card fills full available width
- Used inside message bubbles below CCC paragraph cards

---

### C. Message Input Bar

The pinned bottom input area of the Chat screen.

- White/surface background
- A hairline divider at the top
- Horizontal layout:
  - A multi-line `OutlinedTextField` (rounded corners, no border label, hint text only, max 4 lines)
  - A circular `IconButton` with a send/arrow icon to the right
    - Active (primary color filled circle) when text is non-empty and not loading
    - Disabled (grey, 38% opacity) when empty or loading
- The whole bar has `16dp` horizontal padding and `8dp` vertical padding
- Uses `navigationBarsPadding()` to stay above the system gesture bar

---

### D. Error Banner / Snackbar

- Full-width strip anchored above the input bar (or at the bottom of the screen on Settings)
- `errorContainer` background, `onErrorContainer` text
- Dismiss X button on the far right
- Used for: API key errors, rate limit errors, network errors, seeding errors

---

### E. API Key Setup Banner (Info Banner)

- Full-width strip at the top of the chat message list
- Soft info blue or warm amber background (not error)
- Left: small info icon
- Center: short message text
- Right: **"Set Up"** text button link + X dismiss
- Only visible when no API key is configured

---

### F. Bottom Navigation Bar

Three tabs, always visible at the bottom:

| Tab | Icon | Label |
|---|---|---|
| Chat | `Chat` bubble icon | **Chat** |
| Search | `Search` magnifier icon | **Study** |
| Settings | `Settings` gear icon | **Settings** |

- Active tab: primary color fill on icon and label
- Inactive tabs: muted grey
- Standard Material3 `NavigationBar`

---

## Screen Flow Summary (for reference)

```
[First Launch]
     │
     ▼
Splash / Seeding Screen
(progress indicator, 5–15 sec)
     │
     ▼ (seeding complete)
     │
     ├─► [No API key] → API Key Setup Banner visible on Chat Screen
     │
     ▼
Chat Screen  ◄──────────────────────────────────┐
  ├── User types question                        │
  ├── Loading indicator appears                  │
  ├── Assistant answer appears with source cards │
  ├── "Show Sources" expands CCC + Scripture     │
  └── Settings icon ──────────────────────────► Settings Screen
                                                  ├── Enter API key
                                                  ├── Save key
                                                  └── Back ──────────┘
Bottom Nav:
  Chat  |  Study/Search  |  Settings
```

---

## Key Design Constraints

1. **All screens must scroll vertically at all times** — every screen uses a scrollable root container (e.g., `verticalScroll` or `LazyColumn`) so that no content is ever clipped or hidden regardless of screen height, keyboard state, or system UI insets.
2. **Bottom input bar and bottom navigation must always remain visible and reachable** — implement these as fixed/pinned elements that sit above the scrollable content area, never inside it. They must not be pushed off-screen by expanding content, a soft keyboard, or a tall message list. Use `imePadding()` and `navigationBarsPadding()` on the bottom bar so it rises with the keyboard rather than being hidden behind it.
3. **Never place the bottom navigation bar or message input bar inside a scrollable column** — doing so causes them to scroll out of view. They must be anchored outside the scroll region at all times (e.g., as `bottomBar` in a `Scaffold`, or in a fixed-position container).
4. **CCC paragraph cards and Scripture verse cards must have scrollable content** when text is long — expanded card content must not overflow a fixed-height container.
5. **No fixed-height non-scrollable containers** for paragraph or verse text.
6. **Settings and splash screens must be fully scrollable** so that on very short viewports (or when the soft keyboard is open), all fields and buttons remain accessible by scrolling.
7. **Text contrast** must meet WCAG AA (4.5:1) — the app is a reading tool used in dim settings (church, study).
8. **Loading and error states must be visually distinct** — never silent failures.
9. Typography must support **long doctrinal text** readably — generous line height, adequate font size (min 12sp body, 14sp preferred).
