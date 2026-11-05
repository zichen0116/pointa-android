# Android UI contract

The UI is a stateless application boundary (`PointaApp`) around local navigation and layout drafts. `UiState` reports real connection status; `Preferences` is persisted and validated by the application layer; `UiActions` delegates all device operations. No UI code owns sockets, scanner, credentials, lifecycle or volume-key interception.

V5 uses night-blue surfaces, six independent UI palettes, separate laser colors, three shipping control modes and mirrored one-hand layouts. Four edge margins are normalized against the original touch container, with 42% minimum width and 40% minimum height. Adjacent margin increases the pager rail width. Edits remain drafts until saved; cancel and reset never write settings.

Touch output is bounded, relative by default for one-hand and absolute for full-screen. The full-screen absolute canvas letterboxes to the reported monitor aspect. Release, cancellation, navigation and edit mode hide the pointer. Pages, errors and device state are never simulated. A transient layout lock disables navigation and configuration. First-run guidance is skippable; every setting and UI label has Chinese and English variants.

Scope: UI, pure geometry/mapping helpers and meaningful JVM tests. Hardware gestures, TalkBack traversal and extreme font sizes still need device verification. Gyroscope is P1 and is not presented as a working mode.
