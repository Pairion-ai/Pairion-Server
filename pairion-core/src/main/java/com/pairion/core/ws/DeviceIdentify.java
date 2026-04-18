package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client-to-server identification message sent immediately after WebSocket connection.
 *
 * @param type the message type discriminator, always {@code "DeviceIdentify"}
 * @param deviceId unique identifier for the connecting device
 * @param bearerToken authentication token for the device
 * @param clientVersion semantic version of the connecting client
 */
public record DeviceIdentify(
        @JsonProperty("type") String type,
        @JsonProperty("deviceId") String deviceId,
        @JsonProperty("bearerToken") String bearerToken,
        @JsonProperty("clientVersion") String clientVersion)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "DeviceIdentify";
}
