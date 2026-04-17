/**
 * Bundled weather skill using Open-Meteo API.
 *
 * @remarks
 * Exposes a `get_current_weather` tool that the LLM can call to retrieve
 * current weather conditions for a location. Uses Open-Meteo (free, no
 * API key required) as the data source.
 */

import type { SkillDefinition } from '../registry.js';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('skill-weather');

/** Open-Meteo API base URL. */
const OPEN_METEO_BASE = 'https://api.open-meteo.com/v1/forecast';

/** Open-Meteo geocoding API for location name resolution. */
const GEOCODING_BASE = 'https://geocoding-api.open-meteo.com/v1/search';

/** WMO weather code descriptions. */
const WMO_CODES: Record<number, string> = {
  0: 'clear sky',
  1: 'mainly clear',
  2: 'partly cloudy',
  3: 'overcast',
  45: 'fog',
  48: 'rime fog',
  51: 'light drizzle',
  53: 'moderate drizzle',
  55: 'dense drizzle',
  61: 'slight rain',
  63: 'moderate rain',
  65: 'heavy rain',
  71: 'slight snow',
  73: 'moderate snow',
  75: 'heavy snow',
  80: 'slight rain showers',
  81: 'moderate rain showers',
  82: 'violent rain showers',
  95: 'thunderstorm',
};

/** Weather skill input arguments. */
interface WeatherArgs {
  location: string;
  units?: 'metric' | 'imperial';
}

/**
 * Fetches current weather for a location.
 *
 * @param args - The location and optional unit preference.
 * @param fetchFn - Fetch function (injectable for testing).
 * @returns Formatted weather string.
 */
export async function getWeather(
  args: WeatherArgs,
  fetchFn: typeof fetch = fetch,
): Promise<string> {
  const { location, units = 'imperial' } = args;

  // Geocode the location name to lat/lon
  const geoUrl = `${GEOCODING_BASE}?name=${encodeURIComponent(location)}&count=1`;
  log.info({ location, geoUrl }, 'Geocoding location');

  const geoRes = await fetchFn(geoUrl);
  if (!geoRes.ok) {
    return `Could not find location "${location}".`;
  }

  const geoData = (await geoRes.json()) as { results?: Array<{ latitude: number; longitude: number; name: string; country: string }> };
  if (!geoData.results?.length) {
    return `Could not find location "${location}".`;
  }

  const firstResult = geoData.results[0];
  /* v8 ignore next 3 -- defensive guard; length check above prevents this */
  if (!firstResult) {
    return `Could not find location "${location}".`;
  }
  const { latitude, longitude, name, country } = firstResult;

  // Fetch weather
  const tempUnit = units === 'metric' ? 'celsius' : 'fahrenheit';
  const weatherUrl = `${OPEN_METEO_BASE}?latitude=${latitude}&longitude=${longitude}&current_weather=true&temperature_unit=${tempUnit}`;

  const weatherRes = await fetchFn(weatherUrl);
  if (!weatherRes.ok) {
    return `Could not fetch weather for ${name}.`;
  }

  const weatherData = (await weatherRes.json()) as {
    current_weather: { temperature: number; weathercode: number; windspeed: number };
  };

  const { temperature, weathercode, windspeed } = weatherData.current_weather;
  const conditions = WMO_CODES[weathercode] ?? 'unknown conditions';
  const unitLabel = units === 'metric' ? '°C' : '°F';
  const windUnit = units === 'metric' ? 'km/h' : 'mph';

  return `Currently ${temperature}${unitLabel} with ${conditions} in ${name}, ${country}. Wind speed: ${windspeed} ${windUnit}.`;
}

/**
 * The weather skill definition for registration with the SkillRegistry.
 */
export const weatherSkill: SkillDefinition = {
  name: 'get_current_weather',
  description: 'Get the current weather conditions for a location. Returns temperature, conditions, and wind speed.',
  inputSchema: {
    type: 'object',
    properties: {
      location: {
        type: 'string',
        description: 'The location to get weather for (city name, e.g. "San Francisco" or "London, UK")',
      },
      units: {
        type: 'string',
        enum: ['metric', 'imperial'],
        description: 'Temperature units (default: imperial)',
      },
    },
    required: ['location'],
  },
  invoke: async (args: Record<string, unknown>): Promise<string> => {
    return getWeather({
      location: args['location'] as string,
      units: (args['units'] as 'metric' | 'imperial') ?? 'imperial',
    });
  },
};
