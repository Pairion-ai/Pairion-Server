# Pairion Layer Architecture

**Version:** 1.0
**Date:** 2026-04-21
**Status:** Foundational — replaces Pairion-Scene-Architecture.md
**Purpose:** Defines the composable background + overlay layer system for Pairion's visual surface.

---

## 1. Overview

Pairion's display is a composable stack of independent visual layers. At the bottom is a **background layer** — a full-screen base image or map. On top of it are zero or more **overlay layers** — transparent data visualizations that render over the background. Persistent **HUD elements** (ring system, transcript strip, top bar) sit above everything.

The background and overlays are independently swappable. The user can view ADS-B aircraft on a VFR sectional chart, then swap the background to OpenStreetMap without touching the aircraft overlay. They can add weather radar on top of the aircraft. They can remove the aircraft and keep the weather. Any combination works because each layer is self-contained.

This replaces the previous scene-based architecture where each use case required a monolithic custom QML component. Layers are simpler to build, simpler to combine, and produce more visual combinations without additional code.

---

## 2. The Layer Stack

```
┌──────────────────────────────────────────────────┐
│              Persistent HUD (top)                │
│   RingSystem · TopBar · TranscriptStrip · FPS    │
├──────────────────────────────────────────────────┤
│           Dashboard Panels (optional)            │
│   News · Inbox · Todo · Homestead                │
├──────────────────────────────────────────────────┤
│              Overlay N (topmost)                 │
│              ...                                 │
│              Overlay 2                           │
│              Overlay 1 (bottommost)              │
├──────────────────────────────────────────────────┤
│              Background Layer                    │
│   (exactly one, always present)                  │
└──────────────────────────────────────────────────┘
```

**Rendering order (bottom to top):**
1. Background layer — opaque, fills the entire 2560×1440 viewport
2. Overlay layers — transparent, stacked in add-order
3. Dashboard panels — semi-transparent panels along the bottom (can be hidden when overlays need full screen)
4. Persistent HUD — ring system, top bar, transcript strip, FPS counter

---

## 3. Background Layers

A background layer is a full-screen visual that fills the viewport. Exactly one background is active at all times. The default is the NASA globe.

### 3.1 Available Backgrounds

| ID | Name | Source | Description |
|---|---|---|---|
| `globe` | NASA Globe | NASA Blue Marble textures (bundled) | 3D interactive globe with day/night textures. Current default. Pan/zoom via LLM or user interaction. |
| `osm` | OpenStreetMap | MapLibre GL Native or raster tiles | Dark-styled street map. Configurable center, zoom, tilt. Pan/zoom interactive. |
| `vfr` | VFR Sectional Chart | FAA AeroNav GeoTIFF (bundled, processed) | Aviation sectional chart. Centered on home. Dark-tinted to match HUD palette. |
| `satellite` | Satellite Imagery | MapBox/Bing/ESRI satellite tiles | Real satellite photography. For geographic context. |
| `space` | Star Field | Procedural (current implementation) | Animated star field with nebula effects. For astronomy conversations. |
| `dark` | Dark Canvas | Solid color | Plain dark navy (#0a0e1a). Clean backdrop for data-heavy overlays. Minimal distraction. |
| `radar_screen` | Radar Display | Procedural | Classic ATC green-on-black radar aesthetic with sweep line and range rings. No map data — pure radar look. |

### 3.2 Background Interface

Every background QML component conforms to this interface:

```
Item {
    id: background
    anchors.fill: parent

    // Set by LayerManager
    property var params: ({})      // center, zoom, style, etc.
    property string hudState: ""   // idle/listening/thinking/speaking

    // Backgrounds that support geographic positioning expose these:
    // function latLonToScreen(lat, lon) → {x, y}  — converts coordinates to pixel position
    // function screenToLatLon(x, y) → {lat, lon}  — converts pixel to coordinates
    // property real visibleLatMin, visibleLatMax, visibleLonMin, visibleLonMax — current viewport bounds

    // Backgrounds can signal readiness (e.g., after tiles load)
    signal ready()
}
```

The critical function is `latLonToScreen(lat, lon)`. Overlays that display geographic data (aircraft, weather, markers) call this on the active background to position their elements. This means an overlay works on ANY geographic background — the same ADS-B overlay positions aircraft correctly whether the background is OpenStreetMap, VFR sectional, or the NASA globe.

### 3.3 Background Switching

When the background changes:
1. New background loads and renders behind the current one
2. 600ms crossfade transition (current fades out, new fades in)
3. Old background is destroyed after transition
4. Active overlays remain — they call `latLonToScreen` on the new background to reposition
5. If the new background has a different coordinate system or viewport, overlays animate their elements to the new positions

---

## 4. Overlay Layers

An overlay layer is a transparent visualization that renders on top of the background. Multiple overlays can be active simultaneously. Each overlay is independent — it has its own data source, its own rendering logic, and no knowledge of other overlays.

### 4.1 Available Overlays

| ID | Name | Data Source | Description |
|---|---|---|---|
| `adsb` | ADS-B Aircraft | OpenSky Network API (poll 10s) | Aircraft icons with heading rotation, callout boxes (callsign, type, altitude, speed, route). Color-coded: cyan for GA, amber for airline. |
| `weather_radar` | Precipitation Radar | RainViewer API (tile-based) | Animated precipitation overlay. Color-coded intensity. Time slider for history playback. |
| `wind` | Wind Flow | Open-Meteo gridded data | Animated particle streams along wind vectors. Color-coded by speed. |
| `severe_alerts` | Severe Weather | NWS API (weather.gov) | Watch/warning/advisory polygons overlaid on map. Color-coded by severity. |
| `route` | Navigation Route | OSRM or GraphHopper | Route polyline with turn markers, distance, ETA. |
| `news_pins` | News Locations | News API + geocoding | Glowing amber pins at news event locations. Current globe implementation. |
| `markers` | Custom Markers | LLM-generated | Arbitrary pins/markers placed by the LLM. For "show me where X is." |
| `weather_current` | Current Conditions | Open-Meteo (already integrated) | Metric panel overlay — temperature, wind, humidity, conditions. Positioned in corner, not full-screen. |
| `forecast` | Weather Forecast | Open-Meteo forecast API | Multi-day forecast strip. Hourly breakdown. Sunrise/sunset. |
| `heatmap` | Temperature/Data Heatmap | Open-Meteo gridded data | Color gradient overlay showing temperature, pressure, or other gridded data. |
| `traffic` | Traffic Conditions | Traffic API (TomTom, HERE) | Color-coded road segments (green/yellow/red) showing traffic flow. |
| `cameras` | Security Cameras | RTSP/ONVIF streams | Picture-in-picture video feeds from home cameras. |

### 4.2 Overlay Interface

Every overlay QML component conforms to this interface:

```
Item {
    id: overlay
    anchors.fill: parent

    // Set by LayerManager
    property var overlayData: ({})     // Live data from server (auto-updated)
    property var params: ({})          // Parameters from LLM tool call
    property string hudState: ""       // idle/listening/thinking/speaking

    // The active background's coordinate converter
    // Overlays call this to position geographic elements
    property var latLonToScreen: function(lat, lon) { return {x: 0, y: 0} }

    // Overlay must be transparent — only render its own elements
    // Background shows through everywhere else
}
```

### 4.3 Geographic Overlays vs. Non-Geographic Overlays

**Geographic overlays** (adsb, weather_radar, wind, route, markers, news_pins, severe_alerts, heatmap, traffic) position elements using `latLonToScreen()` from the active background. They work on any geographic background automatically.

**Non-geographic overlays** (weather_current, forecast, cameras) position elements absolutely (corner-anchored, panel-style). They work on any background including non-geographic ones (space, dark).

### 4.4 Overlay Stacking

Overlays are rendered in the order they were added. The most recently added overlay is on top. If overlays visually conflict (e.g., weather radar obscures aircraft icons), the user can reorder or remove specific overlays.

### 4.5 Overlay Interaction with Dashboard Panels

When data-heavy overlays are active (adsb, weather_radar), the dashboard panels at the bottom can optionally collapse or become transparent to give the overlay more screen space. The LLM controls this:
- `set_panels(visible: false)` — hide dashboard panels
- `set_panels(visible: true)` — show dashboard panels
- Default: panels visible. Hidden automatically when overlays would be obscured.

---

## 5. LLM Tools

The LLM manages the layer stack through four tools:

### 5.1 `set_background`

Switches the background layer.

```json
{
    "name": "set_background",
    "description": "Switch the background map or visual. Options: globe (3D NASA earth), osm (OpenStreetMap dark style), vfr (FAA VFR sectional chart), satellite (satellite imagery), space (animated star field), dark (plain dark canvas), radar_screen (ATC-style radar display). Geographic backgrounds accept center coordinates and zoom.",
    "parameters": {
        "background_id": { "type": "string", "required": true },
        "center_lat": { "type": "number" },
        "center_lon": { "type": "number" },
        "zoom": { "type": "number" },
        "transition": { "type": "string", "enum": ["crossfade", "instant"], "default": "crossfade" }
    }
}
```

### 5.2 `add_overlay`

Adds an overlay layer to the stack.

```json
{
    "name": "add_overlay",
    "description": "Add a data overlay on top of the current background. Multiple overlays can be active simultaneously. Options: adsb (live aircraft), weather_radar (precipitation), wind (wind flow), severe_alerts (NWS warnings), route (navigation), news_pins (news locations), markers (custom pins), weather_current (conditions panel), forecast (multi-day forecast).",
    "parameters": {
        "overlay_id": { "type": "string", "required": true },
        "params": { "type": "object", "description": "Overlay-specific parameters" }
    }
}
```

### 5.3 `remove_overlay`

Removes a specific overlay from the stack.

```json
{
    "name": "remove_overlay",
    "description": "Remove a specific overlay from the display.",
    "parameters": {
        "overlay_id": { "type": "string", "required": true }
    }
}
```

### 5.4 `clear_overlays`

Removes all overlays, returning to background + HUD only.

```json
{
    "name": "clear_overlays",
    "description": "Remove all overlays from the display, leaving only the background and HUD.",
    "parameters": {}
}
```

### 5.5 Example Conversations

**"Show me flights overhead"**
```
LLM calls: set_background("vfr", center_lat=33.814, center_lon=-96.582)
LLM calls: add_overlay("adsb", { radius_nm: 8 })
```

**"Switch to the street map"** (while ADS-B is showing)
```
LLM calls: set_background("osm", center_lat=33.814, center_lon=-96.582, zoom=10)
// ADS-B overlay stays — aircraft reposition onto street map automatically
```

**"Add weather radar too"**
```
LLM calls: add_overlay("weather_radar")
// Now showing: OSM background + ADS-B aircraft + weather radar
```

**"Remove the aircraft, just show weather"**
```
LLM calls: remove_overlay("adsb")
// Now showing: OSM background + weather radar
```

**"What's the weather in Tokyo?"**
```
LLM calls: set_background("globe", center_lat=35.68, center_lon=139.69)
LLM calls: clear_overlays()
LLM calls: add_overlay("weather_current", { city: "Tokyo" })
LLM calls: add_overlay("markers", { pins: [{ lat: 35.68, lon: 139.69, label: "Tokyo" }] })
```

**"Show me how to get to the airport"**
```
LLM calls: set_background("osm", center_lat=33.814, center_lon=-96.582, zoom=11)
LLM calls: clear_overlays()
LLM calls: add_overlay("route", { origin: [33.814, -96.582], destination: "DFW Airport" })
LLM calls: add_overlay("traffic")
```

**"Goodnight"** (skill trigger)
```
LLM calls: set_background("dark")
LLM calls: clear_overlays()
// Clean dark screen with just the ring system
```

---

## 6. LayerManager Architecture

### 6.1 Client-Side: LayerManager.qml

The `LayerManager` replaces `SceneManager`. It manages the layer stack.

```
LayerManager.qml
├── backgroundLoader: Loader { }     — loads the active background component
├── overlayStack: Item { }           — parent for all active overlay instances
├── _backgrounds: {}                  — registry: ID → QML component path
├── _overlays: {}                     — registry: ID → QML component path
├── _activeOverlays: []               — ordered list of active overlay instances
│
├── setBackground(id, params)         — swap background with transition
├── addOverlay(id, params)            — instantiate and stack overlay
├── removeOverlay(id)                 — destroy specific overlay
├── clearOverlays()                   — destroy all overlays
├── getLatLonToScreen()               — returns active background's converter
```

### 6.2 Component Registry

Backgrounds and overlays are registered by convention:

```
qml/Backgrounds/
├── GlobeBackground.qml
├── OsmBackground.qml
├── VfrBackground.qml
├── SatelliteBackground.qml
├── SpaceBackground.qml
├── DarkBackground.qml
└── RadarScreenBackground.qml

qml/Overlays/
├── AdsbOverlay.qml
├── WeatherRadarOverlay.qml
├── WindOverlay.qml
├── SevereAlertsOverlay.qml
├── RouteOverlay.qml
├── NewsPinsOverlay.qml
├── MarkersOverlay.qml
├── WeatherCurrentOverlay.qml
├── ForecastOverlay.qml
├── HeatmapOverlay.qml
├── TrafficOverlay.qml
└── CamerasOverlay.qml
```

Adding a new background or overlay = adding one QML file to the appropriate directory plus registering its ID in the LayerManager registry map. No manifest, no YAML, no build system changes.

### 6.3 Coordinate Bridge

The key architectural mechanism: overlays don't know which background they're running on. They ask the LayerManager for a coordinate converter.

```
LayerManager
    │
    ├── activeBackground.latLonToScreen(lat, lon) → {x, y}
    │
    └── passes this function to each overlay as a property
        │
        └── overlay.latLonToScreen = activeBackground.latLonToScreen
```

When the background switches:
1. LayerManager gets the new background's `latLonToScreen` function
2. Updates all active overlays with the new function
3. Overlays recalculate all their element positions
4. Elements animate to new positions (300ms ease)

This is what makes the layer system composable. An ADS-B overlay that positions aircraft at screen coordinates derived from lat/lon works identically on OSM, VFR sectional, globe, or satellite — because the background provides the coordinate conversion.

**Non-geographic backgrounds** (space, dark, radar_screen) provide a default `latLonToScreen` that maps to a simple equirectangular projection centered on home coordinates. Geographic overlays still work — they just use a simpler projection.

### 6.4 Data Flow

Same as the existing SceneDataPush mechanism. No changes.

```
Server data adapter → SceneDataPush { modelId: "adsb", data: [...] }
    → ConnectionState.sceneData["adsb"] = [...]
        → LayerManager routes to overlay with matching data binding
            → AdsbOverlay.overlayData = ConnectionState.sceneData["adsb"]
                → QML property bindings auto-update the visual
```

---

## 7. WebSocket Protocol

### 7.1 New Messages (Server → Client)

**BackgroundChange:**
```json
{
    "type": "BackgroundChange",
    "backgroundId": "osm",
    "params": { "center_lat": 33.814, "center_lon": -96.582, "zoom": 10 },
    "transition": "crossfade"
}
```

**OverlayAdd:**
```json
{
    "type": "OverlayAdd",
    "overlayId": "adsb",
    "params": { "radius_nm": 8 }
}
```

**OverlayRemove:**
```json
{
    "type": "OverlayRemove",
    "overlayId": "adsb"
}
```

**OverlayClear:**
```json
{
    "type": "OverlayClear"
}
```

**SceneDataPush** (unchanged):
```json
{
    "type": "SceneDataPush",
    "modelId": "adsb",
    "data": [...]
}
```

### 7.2 Messages Replaced

The following messages from the scene architecture are superseded:

| Old Message | Replaced By |
|---|---|
| `SceneChange` | `BackgroundChange` + `OverlayAdd` |
| `SceneClear` | `BackgroundChange("globe")` + `OverlayClear` |

`SceneDataPush` remains unchanged — data delivery is independent of the display architecture.

---

## 8. Relationship to Existing Code

### 8.1 What Gets Refactored

| Current | Becomes |
|---|---|
| `SceneManager.qml` | `LayerManager.qml` |
| `qml/Scenes/globe/GlobeScene.qml` | `qml/Backgrounds/GlobeBackground.qml` |
| `qml/Scenes/space/SpaceScene.qml` | `qml/Backgrounds/SpaceBackground.qml` |
| `qml/Scenes/dashboard/DashboardScene.qml` | Dashboard panels become a persistent HUD element, not a scene |
| `qml/Scenes/adsb-radar/AdsbRadarScene.qml` | Split into `qml/Backgrounds/VfrBackground.qml` + `qml/Overlays/AdsbOverlay.qml` |
| `ContextBackground.qml` | Removed (already replaced by SceneManager, now replaced by LayerManager) |
| `HemisphereMap.qml` | Content moves into `GlobeBackground.qml` |
| `ConnectionState.activeSceneId` | `ConnectionState.activeBackgroundId` + `ConnectionState.activeOverlayIds` |
| `SetSceneTool` (server) | `SetBackgroundTool` + `AddOverlayTool` + `RemoveOverlayTool` + `ClearOverlaysTool` |
| `SceneChange` message | `BackgroundChange` message |

### 8.2 What Stays Unchanged

| Component | Why |
|---|---|
| `PairionHUD.qml` | Still the root HUD container |
| `RingSystem.qml` | Still the persistent center visual |
| `TopBar.qml` | Still the persistent top bar |
| `TranscriptStrip.qml` | Still the persistent transcript |
| `DashboardPanels.qml` | Becomes a persistent panel layer, not tied to a specific scene |
| `FpsCounter.qml` | Unchanged |
| `PairionScene SDK` (PairionStyle, HudPanel, etc.) | Unchanged — used by both backgrounds and overlays |
| Data model adapters (server) | Unchanged — data flow is the same |
| `SceneDataPush` message | Unchanged |
| Audio pipeline | Unchanged |
| WebSocket protocol (existing messages) | Unchanged |

### 8.3 What's New

| Component | Purpose |
|---|---|
| `LayerManager.qml` | Manages background + overlay stack |
| `BackgroundBase.qml` | Base type for backgrounds (in PairionScene SDK) |
| `OverlayBase.qml` | Base type for overlays (in PairionScene SDK) |
| `qml/Backgrounds/` directory | Background components |
| `qml/Overlays/` directory | Overlay components |
| `SetBackgroundTool` (server) | LLM tool to switch background |
| `AddOverlayTool` (server) | LLM tool to add overlay |
| `RemoveOverlayTool` (server) | LLM tool to remove overlay |
| `ClearOverlaysTool` (server) | LLM tool to clear all overlays |
| `BackgroundChange`, `OverlayAdd`, `OverlayRemove`, `OverlayClear` messages | WebSocket protocol additions |

---

## 9. PairionScene SDK Additions

### 9.1 BackgroundBase.qml

```
import PairionScene 1.0

Item {
    id: backgroundRoot
    anchors.fill: parent

    property var params: ({})
    property string hudState: "idle"

    // Geographic backgrounds MUST implement this
    // Non-geographic backgrounds get a default equirectangular fallback
    function latLonToScreen(lat, lon) {
        // Default: simple equirectangular centered on home
        var homeLat = params.center_lat || 33.814
        var homeLon = params.center_lon || -96.582
        var scale = params.zoom || 10
        // ... default implementation ...
        return { x: 0, y: 0 }
    }

    signal ready()
}
```

### 9.2 OverlayBase.qml

```
import PairionScene 1.0

Item {
    id: overlayRoot
    anchors.fill: parent

    property var overlayData: ({})
    property var params: ({})
    property string hudState: "idle"
    property var latLonToScreen: function(lat, lon) { return { x: 0, y: 0 } }

    // Overlays MUST be transparent — background shows through
    // Only render overlay-specific elements
}
```

---

## 10. Dashboard Panels — Repositioned

In the scene architecture, the dashboard was a scene. In the layer architecture, the dashboard panels (News, Inbox, Todo, Homestead) are a **persistent HUD element** — like the ring system and transcript strip.

They sit in the bottom quarter of the screen, semi-transparent, always visible unless explicitly hidden. They are NOT an overlay and NOT a background. They are part of the HUD chrome.

**Visibility control:**
- Default: visible
- When overlays need full screen (e.g., full weather radar, route planning): `set_panels(visible: false)`
- LLM controls this, or panels auto-hide when 3+ overlays are active
- User can toggle via keyboard shortcut (P key)

---

## 11. Plugin Extensibility

### 11.1 Adding a New Background

1. Create `qml/Backgrounds/MyBackground.qml` extending `BackgroundBase`
2. Implement `latLonToScreen()` if geographic
3. Add the ID to LayerManager's registry map
4. Add the ID to the `set_background` tool description in the SOUL prompt

### 11.2 Adding a New Overlay

1. Create `qml/Overlays/MyOverlay.qml` extending `OverlayBase`
2. Use `latLonToScreen()` for geographic positioning
3. Add the ID to LayerManager's registry map
4. Add the ID to the `add_overlay` tool description in the SOUL prompt
5. Create the corresponding data model adapter on the server if the overlay needs live data

### 11.3 Future: External Plugins

The same directory-scan approach from the scene architecture works here:
- `~/.pairion/backgrounds/` — user-installed background components
- `~/.pairion/overlays/` — user-installed overlay components
- Each with a `manifest.yaml` describing the component, its data requirements, and trigger phrases

This is a future enhancement. For now, all backgrounds and overlays ship in the client binary.

---

## 12. Security Model

Same boundaries as the scene architecture:

- Backgrounds and overlays are pre-built QML components, not dynamically generated code
- The LLM selects which components to activate — it doesn't generate rendering code
- Data flows from server to client via SceneDataPush — overlays don't make network requests
- Overlays access only their own `overlayData`, `params`, `latLonToScreen`, and PairionStyle
- Overlays cannot access ConnectionState, the WebSocket client, or other overlays' state

---

## 13. Development Roadmap

### Phase 1 — Core Infrastructure (2 prompts)
- LayerManager replacing SceneManager
- BackgroundBase and OverlayBase in PairionScene SDK
- Refactor globe → GlobeBackground
- Refactor space → SpaceBackground
- DarkBackground (trivial)
- `set_background` / `add_overlay` / `remove_overlay` / `clear_overlays` server tools
- `BackgroundChange` / `OverlayAdd` / `OverlayRemove` / `OverlayClear` messages
- Proof: say "show me the globe" → globe loads. Say "switch to dark" → dark canvas.

### Phase 2 — Existing Features as Layers (2 prompts)
- VfrBackground (from current AdsbRadarScene chart)
- AdsbOverlay (from current AdsbRadarScene aircraft rendering)
- NewsPinsOverlay (from current globe news pins)
- WeatherCurrentOverlay (from current weather tool result display)
- MarkersOverlay (from current focus_map pin behavior)
- Proof: "show me flights overhead" → VFR background + ADS-B overlay. "Switch to street map" → OSM background, aircraft stay.

### Phase 3 — OpenStreetMap + New Overlays (2-3 prompts)
- OsmBackground (MapLibre GL Native or raster tile approach)
- RouteOverlay (OSRM/GraphHopper integration)
- WeatherRadarOverlay (RainViewer API)
- WindOverlay (Open-Meteo gridded wind data)
- SevereAlertsOverlay (NWS API)
- Proof: "how do I get to the airport" → OSM background + route overlay. "Add weather" → weather radar stacks on top.

### Phase 4 — Extended Backgrounds and Overlays (ongoing)
- SatelliteBackground
- RadarScreenBackground
- ForecastOverlay
- HeatmapOverlay
- TrafficOverlay
- CamerasOverlay
- Each is an independent prompt, buildable in any order

---

## 14. Advantages Over Scene Architecture

| Aspect | Scene Architecture | Layer Architecture |
|---|---|---|
| Combinability | One scene at a time | Unlimited background + overlay combinations |
| Code reuse | Each scene rebuilds map rendering | Backgrounds shared across overlays |
| Adding a feature | New monolithic scene (200+ lines) | New overlay (50-100 lines) using existing background |
| Background swapping | Requires rebuilding the whole scene | One tool call, overlays stay |
| Weather + ADS-B | Would need a combined "aviation weather" scene | Just stack both overlays |
| Complexity | Scene manages everything (map + data + layout) | Each component does one thing |
| Plugin authoring | Must understand full scene lifecycle | Write one overlay, use existing backgrounds |

---

## 15. Open Questions

1. **Overlay ordering UI:** Should the user be able to reorder overlays? ("Move weather behind aircraft") Or is add-order sufficient?

2. **Overlay opacity:** Should each overlay have a configurable opacity? ("Make the weather radar more transparent") This could be a tool parameter.

3. **Panel auto-hide logic:** What triggers auto-hiding the dashboard panels? Number of active overlays? Specific overlay types? Always manual?

4. **Background presets:** Should common combinations be named? "Aviation mode" = VFR + ADS-B + weather radar. "Navigation mode" = OSM + route + traffic. The LLM could call these by name instead of composing manually.

5. **State persistence:** Should the current layer stack persist across sessions? If the user had ADS-B + weather radar showing last night, should it restore on startup?

6. **Overlay conflict detection:** What if two overlays both render in the same screen region and become unreadable? Does the LayerManager warn, or is this the LLM's responsibility?
