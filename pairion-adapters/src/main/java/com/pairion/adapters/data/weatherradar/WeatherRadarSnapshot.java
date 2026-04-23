package com.pairion.adapters.data.weatherradar;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Immutable snapshot of RainViewer weather radar tile metadata pushed to the client.
 *
 * <p>The client uses this snapshot to request radar tiles from the RainViewer CDN using the URL
 * pattern: {@code {host}{path}/256/{z}/{x}/{y}/{colorScheme}/{options}.png}.
 *
 * @param host        RainViewer tile cache host (e.g. {@code "https://tilecache.rainviewer.com"})
 * @param frames      ordered list of available radar frames, oldest first
 * @param latestPath  path of the most recent radar frame
 * @param tileSize    tile pixel size (always {@code 256})
 * @param colorScheme RainViewer color scheme index (1–8)
 * @param options     RainViewer smooth/snow options string
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeatherRadarSnapshot(
        String host,
        List<WeatherRadarFrame> frames,
        String latestPath,
        int tileSize,
        int colorScheme,
        String options) {}
