export const TELEMETRY_WINDOW_HOURS = 4;

export const TELEMETRY_BUCKET_MINUTES = Math.max(1, Math.round((TELEMETRY_WINDOW_HOURS * 60) / 120));

export const TELEMETRY_WINDOW_LABEL = `últimas ${TELEMETRY_WINDOW_HOURS} h`;
