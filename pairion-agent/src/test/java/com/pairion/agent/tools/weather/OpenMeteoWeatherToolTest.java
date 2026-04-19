package com.pairion.agent.tools.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link OpenMeteoWeatherTool}. */
class OpenMeteoWeatherToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nameIsGetCurrentWeather() {
        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool();
        assertThat(tool.name()).isEqualTo("get_current_weather");
    }

    @Test
    void returnsErrorWhenCityMissing() throws Exception {
        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool();
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("error", "missing_parameter");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsWeatherDataOnSuccess() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);

        String geoJson =
                """
                {"results":[{"name":"Dallas","country":"US","latitude":32.78,"longitude":-96.8}]}
                """;
        String forecastJson =
                """
                {"current":{"temperature_2m":72.5,"weathercode":0,"windspeed_10m":8.5}}
                """;

        HttpResponse<String> geoResponse = mock(HttpResponse.class);
        when(geoResponse.statusCode()).thenReturn(200);
        when(geoResponse.body()).thenReturn(geoJson);

        HttpResponse<String> forecastResponse = mock(HttpResponse.class);
        when(forecastResponse.statusCode()).thenReturn(200);
        when(forecastResponse.body()).thenReturn(forecastJson);

        when(mockClient.send(any(), any()))
                .thenReturn((java.net.http.HttpResponse) geoResponse)
                .thenReturn((java.net.http.HttpResponse) forecastResponse);

        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool(mockClient, MAPPER);
        Map<String, Object> result = tool.execute(Map.of("city", "Dallas"));

        assertThat(result).containsKey("city");
        assertThat(result.get("city").toString()).contains("Dallas");
        assertThat(result).containsKey("temperature_f");
        assertThat(result).containsKey("conditions");
        assertThat(result.get("conditions")).isEqualTo("Clear sky");
        assertThat(result).containsKey("wind_speed_mph");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsErrorWhenGeocodeHttpFails() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> badResponse = mock(HttpResponse.class);
        when(badResponse.statusCode()).thenReturn(500);
        when(badResponse.body()).thenReturn("{}");
        when(mockClient.send(any(), any())).thenReturn((java.net.http.HttpResponse) badResponse);

        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool(mockClient, MAPPER);
        Map<String, Object> result = tool.execute(Map.of("city", "Nowhere"));
        assertThat(result).containsEntry("error", "geocoding_failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsErrorWhenCityNotFoundInGeocode() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> emptyResponse = mock(HttpResponse.class);
        when(emptyResponse.statusCode()).thenReturn(200);
        when(emptyResponse.body()).thenReturn("{\"results\":[]}");
        when(mockClient.send(any(), any())).thenReturn((java.net.http.HttpResponse) emptyResponse);

        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool(mockClient, MAPPER);
        Map<String, Object> result = tool.execute(Map.of("city", "Atlantis"));
        assertThat(result).containsEntry("error", "city_not_found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsErrorWhenForecastHttpFails() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);

        String geoJson =
                "{\"results\":[{\"name\":\"Dallas\",\"country\":\"US\","
                        + "\"latitude\":32.78,\"longitude\":-96.8}]}";

        HttpResponse<String> geoResponse = mock(HttpResponse.class);
        when(geoResponse.statusCode()).thenReturn(200);
        when(geoResponse.body()).thenReturn(geoJson);

        HttpResponse<String> badForecast = mock(HttpResponse.class);
        when(badForecast.statusCode()).thenReturn(503);
        when(badForecast.body()).thenReturn("{}");

        when(mockClient.send(any(), any()))
                .thenReturn((java.net.http.HttpResponse) geoResponse)
                .thenReturn((java.net.http.HttpResponse) badForecast);

        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool(mockClient, MAPPER);
        Map<String, Object> result = tool.execute(Map.of("city", "Dallas"));
        assertThat(result).containsEntry("error", "forecast_failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsErrorWhenResultsIsNotArray() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> nonArrayResponse = mock(HttpResponse.class);
        when(nonArrayResponse.statusCode()).thenReturn(200);
        when(nonArrayResponse.body()).thenReturn("{\"results\": \"bad\"}");
        when(mockClient.send(any(), any())).thenReturn((java.net.http.HttpResponse) nonArrayResponse);

        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool(mockClient, MAPPER);
        Map<String, Object> result = tool.execute(Map.of("city", "Nowhere"));
        assertThat(result).containsEntry("error", "city_not_found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cityNameOmitsCountryWhenEmpty() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);

        // Geo response with empty country field
        String geoJson =
                "{\"results\":[{\"name\":\"Springfield\",\"country\":\"\","
                        + "\"latitude\":40.0,\"longitude\":-90.0}]}";
        String forecastJson =
                "{\"current\":{\"temperature_2m\":65.0,\"weathercode\":2,\"windspeed_10m\":5.0}}";

        HttpResponse<String> geoResponse = mock(HttpResponse.class);
        when(geoResponse.statusCode()).thenReturn(200);
        when(geoResponse.body()).thenReturn(geoJson);

        HttpResponse<String> forecastResponse = mock(HttpResponse.class);
        when(forecastResponse.statusCode()).thenReturn(200);
        when(forecastResponse.body()).thenReturn(forecastJson);

        when(mockClient.send(any(), any()))
                .thenReturn((java.net.http.HttpResponse) geoResponse)
                .thenReturn((java.net.http.HttpResponse) forecastResponse);

        OpenMeteoWeatherTool tool = new OpenMeteoWeatherTool(mockClient, MAPPER);
        Map<String, Object> result = tool.execute(Map.of("city", "Springfield"));

        // No ", " country appended when country is empty
        assertThat(result.get("city").toString()).isEqualTo("Springfield");
    }

    @Test
    void describeWeatherCodeClearSky() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(0)).isEqualTo("Clear sky");
    }

    @Test
    void describeWeatherCodeMainlyClear() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(1)).isEqualTo("Mainly clear");
    }

    @Test
    void describeWeatherCodePartlyCloudy() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(2)).isEqualTo("Partly cloudy");
    }

    @Test
    void describeWeatherCodeRain() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(63)).isEqualTo("Rain");
    }

    @Test
    void describeWeatherCodeThunderstorm() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(95)).isEqualTo("Thunderstorm");
    }

    @Test
    void describeWeatherCodeOvercast() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(3)).isEqualTo("Overcast");
    }

    @Test
    void describeWeatherCodeFoggy() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(46)).isEqualTo("Foggy");
    }

    @Test
    void describeWeatherCodeDrizzle() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(53)).isEqualTo("Drizzle");
    }

    @Test
    void describeWeatherCodeFreezingDrizzle() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(56)).isEqualTo("Freezing drizzle");
    }

    @Test
    void describeWeatherCodeFreezingRain() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(66)).isEqualTo("Freezing rain");
    }

    @Test
    void describeWeatherCodeSnow() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(73)).isEqualTo("Snow");
    }

    @Test
    void describeWeatherCodeRainShowers() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(81)).isEqualTo("Rain showers");
    }

    @Test
    void describeWeatherCodeSnowShowers() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(85)).isEqualTo("Snow showers");
    }

    @Test
    void describeWeatherCodeThunderstormWithHail() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(97)).isEqualTo("Thunderstorm with hail");
    }

    @Test
    void describeWeatherCodeUnknown() {
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(42)).contains("42");
    }

    @Test
    void describeWeatherCodeUnknownHighCode() {
        // code=100: reaches the >= 96 range check (A=true), but > 99 (B=false) → unknown
        assertThat(OpenMeteoWeatherTool.describeWeatherCode(100)).contains("100");
    }
}
