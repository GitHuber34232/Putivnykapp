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

module.exports = { MONTHS_UK, parseMonthUk };
