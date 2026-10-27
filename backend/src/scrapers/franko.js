const cheerio = require("cheerio");
const { parseMonthUk, kyivIso, inferEventYear } = require("./utils");

const VENUE = {
  name: "Театр імені Івана Франка",
  lat: 50.4437,
  lon: 30.5191,
  category: "theater",
};

const DAY_ABBR = {
  "ПН": true, "ВТ": true, "СР": true, "ЧТ": true, "ПТ": true, "СБ": true, "НД": true,
};

async function scrape() {
  try {
    const res = await fetch("https://ft.org.ua/", {
      headers: { "User-Agent": "PutivnykBot/1.0" },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return [];
    const html = await res.text();
    const $ = cheerio.load(html);
    const events = [];

    const links = [];
    $("a[href*='sales.ft.org.ua/events/']").each((_, el) => {
      const href = $(el).attr("href") || "";
      const text = $(el).text().trim();
      if (href && text && !links.find((l) => l.href === href)) {
        links.push({ href, text });
      }
    });

    for (const link of links) {
      try {
        const raw = link.text;
        const dateMatch = raw.match(/(\d{1,2})\s+(СІЧНЯ|ЛЮТОГО|БЕРЕЗНЯ|КВІТНЯ|ТРАВНЯ|ЧЕРВНЯ|ЛИПНЯ|СЕРПНЯ|ВЕРЕСНЯ|ЖОВТНЯ|ЛИСТОПАДА|ГРУДНЯ)/i);
        if (!dateMatch) continue;

        const dd = dateMatch[1].padStart(2, "0");
        const mm = parseMonthUk(dateMatch[2]);
        if (!mm) continue;

        const titleParts = raw.split(dateMatch[0]);
        let title = (titleParts[0] || "").trim();
        // The weekday sits next to the date ("КАЙДАШЕВА СІМ'Я ПТ, 9 СІЧНЯ"), on either side of the title.
        title = title
          .replace(/^(ПН|ВТ|СР|ЧТ|ПТ|СБ|НД),?\s*/i, "")
          .replace(/(?:^|\s+)(ПН|ВТ|СР|ЧТ|ПТ|СБ|НД),?$/i, "")
          .trim();
        if (!title) {
          const upper = raw.match(/^[А-ЯІЇЄҐ\s''\-–()]+/u);
          title = upper ? upper[0].trim() : raw.slice(0, 60);
        }

        const cleanTitle = title
          .replace(/\s+/g, " ")
          .replace(/^(.+?)\1$/i, "$1")
          .trim();

        if (!cleanTitle) continue;

        const timeMatch = raw.match(/(\d{2}:\d{2})/);
        const time = timeMatch ? timeMatch[1] : "18:00";
        const durationMatch = raw.match(/(\d+)\s*хвилин/);
        const durationMin = durationMatch ? parseInt(durationMatch[1]) : null;

        const year = inferEventYear(mm, dd);
        const startsAt = kyivIso(year, mm, dd, time);
        // Computed on the Kyiv wall clock; relabelling a UTC string as +02:00 shifted it by hours.
        const endsAt = durationMin ? kyivIso(year, mm, dd, time, durationMin) : null;

        events.push({
          id: `franko-${mm}${dd}-${events.length}`,
          title: cleanTitle,
          description: durationMin ? `Тривалість: ${durationMin} хв` : null,
          category: "theater",
          starts_at: startsAt,
          ends_at: endsAt,
          location_name: VENUE.name,
          latitude: VENUE.lat,
          longitude: VENUE.lon,
          ticket_url: link.href,
          price_from: null,
          price_to: null,
          cover_url: null,
          category_translations: { theater: "Театр" },
        });
      } catch (e) {
        console.warn("[franko] parse error:", e.message);
      }
    }

    return events;
  } catch (e) {
    console.error("[franko] scrape failed:", e.message);
    return [];
  }
}

module.exports = { scrape, venue: VENUE };
