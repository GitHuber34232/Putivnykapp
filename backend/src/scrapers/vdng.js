const cheerio = require("cheerio");
const { MONTHS_UK, kyivIso, inferEventYear } = require("./utils");

const VENUE = {
  name: "ВДНГ",
  lat: 50.3807,
  lon: 30.4797,
  category: "concert",
};

async function scrape() {
  try {
    const res = await fetch("https://vdng.ua/events/", {
      headers: { "User-Agent": "PutivnykBot/1.0" },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return [];
    const html = await res.text();
    const $ = cheerio.load(html);
    const events = [];

    $(".c-events-list__item").each((i, el) => {
      try {
        const $el = $(el);
        const title =
          $el.find(".card-event-lg-description__name h2").first().text().trim() ||
          $el.find(".card-event-lg-description__name").first().text().trim();
        const dateText = $el.find("time").first().attr("datetime") ||
          $el.find("time").first().text().trim() || "";
        const href =
          $el.find(".card-event-lg__link").first().attr("href") || "";
        const img = $el.find("img").first().attr("src") || null;
        const priceText =
          $el.find(".card-event-lg-description__price").first().text().trim();
        const descEl = $el.find(".card-event-lg-back p, .card-event-lg-back .card-event-lg-description__name p");
        const description = descEl.first().text().trim() || null;

        if (!title || title.length < 3) return;

        const fullUrl = href.startsWith("http")
          ? href
          : `https://vdng.ua${href}`;

        const isFree =
          priceText.includes("вільний") || priceText.includes("безкоштовно");

        let startsAt = null;
        let endsAt = null;
        const dateRangeMatch = dateText.match(
          /(\d{1,2})\s+(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня),?\s*(\d{1,2}:\d{2})/i
        );
        if (dateRangeMatch) {
          const dd = dateRangeMatch[1].padStart(2, "0");
          const mm = MONTHS_UK[dateRangeMatch[2].toLowerCase()];
          const time = dateRangeMatch[3];
          const year = inferEventYear(mm, dd);
          startsAt = kyivIso(year, mm, dd, time);

          const endMatch = dateText.match(
            /[–\-]\s*(\d{1,2})\s+(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня),?\s*(\d{1,2}:\d{2})/i
          );
          if (endMatch) {
            const edd = endMatch[1].padStart(2, "0");
            const emm = MONTHS_UK[endMatch[2].toLowerCase()];
            // An end in an earlier month than the start is in the following year.
            const endYear = Number(emm) < Number(mm) ? year + 1 : year;
            endsAt = kyivIso(endYear, emm, edd, endMatch[3]);
          }
        }

        const guessCategory =
          title.match(/концерт|фестиваль|dantes|музик/i) ? "concert"
          : title.match(/виставк|expo|форум/i) ? "exhibition"
          : title.match(/майстер|workshop/i) ? "workshop"
          : "concert";

        events.push({
          id: `vdng-${i}`,
          title: title.replace(/\s+/g, " "),
          description,
          category: guessCategory,
          starts_at: startsAt,
          ends_at: endsAt,
          location_name: VENUE.name,
          latitude: VENUE.lat,
          longitude: VENUE.lon,
          ticket_url: fullUrl,
          price_from: isFree ? 0 : null,
          price_to: isFree ? 0 : null,
          cover_url: img,
          category_translations: {
            concert: "Концерт",
            exhibition: "Виставка",
            workshop: "Майстер-клас",
          },
        });
      } catch (e) {
        console.warn("[vdng] parse error:", e.message);
      }
    });

    return events;
  } catch (e) {
    console.error("[vdng] scrape failed:", e.message);
    return [];
  }
}

module.exports = { scrape, venue: VENUE };
