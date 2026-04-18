/**
 * WebSocket message types for the Pairion real-time protocol.
 *
 * <p>Defines the sealed {@link com.pairion.core.ws.WebSocketMessage} hierarchy that models every
 * JSON envelope exchanged over the {@code /ws/v1} WebSocket channel. The {@code type} field is the
 * Jackson polymorphic discriminator.
 */
package com.pairion.core.ws;
