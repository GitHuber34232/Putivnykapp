/**
 * Shared utilities for scrapers.
 */

/** Ukrainian genitive month names → zero-padded month numbers */
const MONTHS_UK = {
  "січня": "01", "лютого": "02", "березня": "03", "квітня": "04",
  "травня": "05", "червня": "06", "липня": "07", "серпня": "08",
  "вересня": "09", "жовтня": "10", "листопада": "11", "грудня": "12",
};

/**
 * Parse a Ukrainian month name (case-insensitive) into a zero-padded number.
 * @param {string} name — e.g. "СІЧНЯ", "січня"
 * @returns {string|undefined} — e.g. "01", or undefined if not found
 */
function parseMonthUk(name) {
  return MONTHS_UK[(name || "").toLowerCase()];
}

/** IANA zone for Kyiv; older ICU builds only know the "Europe/Kiev" spelling. */
const KYIV_TIME_ZONE = (() => {
  for (const zone of ["Europe/Kyiv", "Europe/Kiev"]) {
    try {
      new Intl.DateTimeFormat("en-US", { timeZone: zone });
      return zone;
    } catch (_) {
      // try the next spelling
    }
  }
  return "UTC";
})();

const kyivPartsFormatter = new Intl.DateTimeFormat("en-US", {
  timeZone: KYIV_TIME_ZONE,
  hourCycle: "h23",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

/** Wall-clock parts of a UTC instant in Kyiv. */
function kyivParts(utcMillis) {
  const parts = {};
  for (const part of kyivPartsFormatter.formatToParts(new Date(utcMillis))) {
    parts[part.type] = part.value;
  }
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour),
    minute: Number(parts.minute),
    second: Number(parts.second),
  };
}

/** Kyiv offset from UTC in minutes at a UTC instant (+120 in winter, +180 in summer). */
function kyivOffsetMinutes(utcMillis) {
  const p = kyivParts(utcMillis);
  const wallAsUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return Math.round((wallAsUtc - Math.floor(utcMillis / 1000) * 1000) / 60000);
}

const pad2 = (n) => String(n).padStart(2, "0");

/**
 * ISO-8601 string for a Kyiv wall-clock time with the correct (DST-aware) offset,
 * e.g. kyivIso(2026, 7, 15, "19:00") → "2026-07-15T19:00:00+03:00".
 * @param {number|string} year
 * @param {number|string} month 1-12
 * @param {number|string} day
 * @param {string} time "HH:MM"
 * @param {number} addMinutes shifts the wall-clock time (e.g. a duration)
 */
function kyivIso(year, month, day, time = "00:00", addMinutes = 0) {
  const [hh = 0, mi = 0] = String(time || "00:00").split(":").map((n) => parseInt(n, 10) || 0);
  // The wall-clock time expressed as if it were UTC; adding minutes here keeps days/months correct.
  const wall = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), hh, mi + addMinutes));
  // Two passes settle the offset around DST switches.
  let offset = kyivOffsetMinutes(wall.getTime() - 120 * 60000);
  offset = kyivOffsetMinutes(wall.getTime() - offset * 60000);
  const sign = offset >= 0 ? "+" : "-";
  const abs = Math.abs(offset);
  return `${wall.getUTCFullYear()}-${pad2(wall.getUTCMonth() + 1)}-${pad2(wall.getUTCDate())}` +
    `T${pad2(wall.getUTCHours())}:${pad2(wall.getUTCMinutes())}:00` +
    `${sign}${pad2(Math.floor(abs / 60))}:${pad2(abs % 60)}`;
}

/** Today's date in Kyiv as "YYYY-MM-DD" (UTC dates are off around midnight). */
function kyivToday(now = Date.now()) {
  const p = kyivParts(now);
  return `${p.year}-${pad2(p.month)}-${pad2(p.day)}`;
}

/**
 * Year for a day/month shown without a year on a listing page. Listings show upcoming
 * dates, so a date more than ~2 months in the past belongs to next year (December → January).
 */
function inferEventYear(month, day, now = Date.now()) {
  const today = kyivParts(now);
  const candidate = Date.UTC(today.year, Number(month) - 1, Number(day));
  const todayUtc = Date.UTC(today.year, today.month - 1, today.day);
  const sixtyDays = 60 * 24 * 60 * 60 * 1000;
  return candidate < todayUtc - sixtyDays ? today.year + 1 : today.year;
}

module.exports = {
  MONTHS_UK,
  parseMonthUk,
  kyivIso,
  kyivToday,
  kyivOffsetMinutes,
  inferEventYear,
};
