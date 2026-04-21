package com.pairion.agent.tools.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link MapFocusTool} geocoding and error handling. */
@SuppressWarnings("unchecked")
class MapFocusToolTest {

    private HttpClient httpClient;
    private MapFocusTool tool;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        tool = new MapFocusTool(httpClient, new ObjectMapper());
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("focus_map");
        assertThat(MapFocusTool.TOOL_NAME).isEqualTo("focus_map");
    }

    @Test
    void missingLocationReturnsError() throws Exception {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("error", "missing_parameter");
    }

    @Test
    void successfulGeocodeReturnsFocusData() throws Exception {
        String geoJson = """
                {
                  "results": [{
                    "name": "Tokyo",
                    "country": "Japan",
                    "latitude": 35.6762,
                    "longitude": 139.6503
                  }]
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(geoJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "Tokyo", "zoom", "city"));

        assertThat(result).containsEntry("label", "Tokyo, Japan");
        assertThat(result).containsEntry("zoom", "city");
        assertThat(result).containsEntry("status", "focused");
        assertThat(((Number) result.get("lat")).doubleValue()).isEqualTo(35.6762);
        assertThat(((Number) result.get("lon")).doubleValue()).isEqualTo(139.6503);
    }

    @Test
    void defaultZoomIsCityWhenNotProvided() throws Exception {
        String geoJson = """
                {
                  "results": [{
                    "name": "Dallas",
                    "country": "United States",
                    "latitude": 32.7767,
                    "longitude": -96.797
                  }]
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(geoJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "Dallas"));

        assertThat(result).containsEntry("zoom", "city");
    }

    @Test
    void countryZoomPassedThrough() throws Exception {
        String geoJson = """
                {
                  "results": [{
                    "name": "Japan",
                    "country": "Japan",
                    "latitude": 36.2048,
                    "longitude": 138.2529
                  }]
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(geoJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "Japan", "zoom", "country"));

        assertThat(result).containsEntry("zoom", "country");
        assertThat(result).containsEntry("status", "focused");
    }

    @Test
    void emptyResultsReturnsLocationNotFound() throws Exception {
        String geoJson = """
                { "results": [] }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(geoJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "Xyzzy123Nonexistent"));

        assertThat(result).containsEntry("error", "location_not_found");
    }

    @Test
    void httpErrorReturnsGeocodingFailed() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "Tokyo"));

        assertThat(result).containsEntry("error", "geocoding_failed");
    }

    @Test
    void labelWithoutCountryUsesNameOnly() throws Exception {
        String geoJson = """
                {
                  "results": [{
                    "name": "SomePlace",
                    "latitude": 10.0,
                    "longitude": 20.0
                  }]
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(geoJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "SomePlace"));

        assertThat(result).containsEntry("label", "SomePlace");
    }

    /** Response with no "results" key (isArray() false) is treated as not-found — covers !isArray() branch. */
    @Test
    void missingResultsKeyReturnsLocationNotFound() throws Exception {
        // No "results" key → json.path("results") returns MissingNode → isArray() = false
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Map<String, Object> result = tool.execute(Map.of("location", "Nowhere"));
        assertThat(result).containsEntry("error", "location_not_found");
    }

    /** Default (no-arg) constructor builds successfully — covers the production constructor path. */
    @Test
    void defaultConstructorBuildsSuccessfully() {
        MapFocusTool defaultTool = new MapFocusTool();
        assertThat(defaultTool).isNotNull();
        assertThat(defaultTool.name()).isEqualTo("focus_map");
    }
}
