const cheerio = require("cheerio");

const VENUE = {
  name: "Молодий театр",
  lat: 50.4481,
  lon: 30.5127,
  category: "theater",
};

function buildDaysWithShows($) {
  const days = [];
  $(".calendar-table a, .calendar a, a[href*='/afisha/2']").each((_, el) => {
    const href = $(el).attr("href") || "";
    const match = href.match(/\/afisha\/(\d{4}-\d{2}-\d{2})/);
    if (match) days.push(match[1]);
  });
  return [...new Set(days)];
}

async function scrapeDayPage(date) {
  const res = await fetch(`https://molodyytheatre.com/afisha/${date}`, {
    headers: { "User-Agent": "PutivnykBot/1.0" },
    signal: AbortSignal.timeout(10000),
  });
  if (!res.ok) return [];
  const html = await res.text();
  const $ = cheerio.load(html);
  const events = [];

  $(".views-row").each((i, el) => {
    try {
      const dayNum = $(el).find(".t1").text().trim();
      const monthDay = $(el).find(".t2").text().trim();
      const time = $(el).find(".t3").text().trim() || "18:00";
      const title = $(el).find(".views-field-field-event-title a").text().trim();
      const href = $(el).find(".views-field-field-event-title a").attr("href") || "";
      const img = $(el).find(".views-field-field-images img").attr("src") || null;
      const scene = $(el).find(".views-field-field-category .field-content").text().trim();
      const author = $(el).find(".views-field-field-author .field-content").text().trim();
      const director = $(el).find(".views-field-field-director .field-content").text().trim();
      const genre = $(el).find(".views-field-field-genre .field-content").text().trim();
      const ticketHref = $(el).find("a[href*='/tickets/']").attr("href") || "";
      const age = $(el).find(".views-field-field-age .field-content").text().trim();

      if (!title) return;

      const descParts = [genre, author ? `Автор: ${author}` : "", director ? `Режисер: ${director}` : "", scene, age].filter(Boolean);

      const startsAt = `${date}T${time}:00+02:00`;
      const ticketUrl = ticketHref
        ? `https://molodyytheatre.com${ticketHref}`
        : `https://molodyytheatre.com${href}`;

      events.push({
        id: `molodiy-${date}-${i}`,
        title,
        description: descParts.join(". ") || null,
        category: "theater",
        starts_at: startsAt,
        ends_at: null,
        location_name: `${VENUE.name} — ${scene || "Основна сцена"}`,
        latitude: VENUE.lat,
        longitude: VENUE.lon,
        ticket_url: ticketUrl,
        price_from: null,
        price_to: null,
        cover_url: img,
        category_translations: { theater: "Театр" },
      });
    } catch (e) {
      console.warn("[molodiy] parse error:", e.message);
    }
  });

  return events;
}

async function scrape() {
  try {
    const res = await fetch("https://molodyytheatre.com/afisha/", {
      headers: { "User-Agent": "PutivnykBot/1.0" },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return [];
    const html = await res.text();
    const $ = cheerio.load(html);

    const days = buildDaysWithShows($);

    const today = new Date().toISOString().slice(0, 10);
    const futureDays = days.filter((d) => d >= today).slice(0, 14);

    const results = await Promise.allSettled(
      futureDays.map((d) => scrapeDayPage(d))
    );

    return results
      .filter((r) => r.status === "fulfilled")
      .flatMap((r) => r.value);
  } catch (e) {
    console.error("[molodiy] scrape failed:", e.message);
    return [];
  }
}

module.exports = { scrape, venue: VENUE };
