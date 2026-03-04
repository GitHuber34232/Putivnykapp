const opera = require("./opera");
const molodiy = require("./molodiy");
const franko = require("./franko");
const arsenal = require("./arsenal");
const khanenko = require("./khanenko");
const vdng = require("./vdng");

const scrapers = [opera, molodiy, franko, arsenal, khanenko, vdng];

const CACHE_TTL_MS = 30 * 60 * 1000;

let cache = null;
let cacheTimestamp = 0;
let scrapeInProgress = null;

async function runAllScrapers() {
  console.log(`[scraper] Starting scrape of ${scrapers.length} sources...`);
  const start = Date.now();

  const results = await Promise.allSettled(
    scrapers.map(async (s) => {
      try {
        const events = await s.scrape();
        console.log(`[scraper] ${s.venue.name}: ${events.length} events`);
        return events;
      } catch (err) {
        console.error(`[scraper] ${s.venue.name} FAILED:`, err.message);
        return [];
      }
    })
  );

  const allEvents = results
    .filter((r) => r.status === "fulfilled")
    .flatMap((r) => r.value);

  const elapsed = ((Date.now() - start) / 1000).toFixed(1);
  console.log(`[scraper] Done in ${elapsed}s — ${allEvents.length} total events`);

  return allEvents;
}

async function getEvents() {
  const now = Date.now();

  if (cache && now - cacheTimestamp < CACHE_TTL_MS) {
    return cache;
  }

  if (scrapeInProgress) {
    return scrapeInProgress;
  }

  scrapeInProgress = runAllScrapers()
    .then((events) => {
      cache = events;
      cacheTimestamp = Date.now();
      scrapeInProgress = null;
      return events;
    })
    .catch((err) => {
      console.error("[scraper] Fatal error:", err.message);
      scrapeInProgress = null;
      return cache || [];
    });

  return scrapeInProgress;
}

function getCacheInfo() {
  return {
    cached: !!cache,
    eventCount: cache ? cache.length : 0,
    cacheAge: cache ? Math.round((Date.now() - cacheTimestamp) / 1000) : null,
    cacheTtl: CACHE_TTL_MS / 1000,
    sources: scrapers.map((s) => s.venue.name),
  };
}

module.exports = { getEvents, getCacheInfo };
