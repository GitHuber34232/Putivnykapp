const cheerio = require("cheerio");

const VENUE = {
  name: "Мистецький Арсенал",
  lat: 50.4345,
  lon: 30.5350,
  category: "exhibition",
};

async function scrape() {
  try {
    const res = await fetch("https://artarsenal.in.ua/", {
      headers: { "User-Agent": "PutivnykBot/1.0" },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return [];
    const html = await res.text();
    const $ = cheerio.load(html);
    const events = [];

    $(".event-block").each((i, el) => {
      try {
        const titleEl = $(el).find(".event-block__ttl");
        const title = titleEl.text().trim();
        const href = titleEl.attr("href") || "";
        const imgUrl =
          $(el).find(".img").attr("data-bg") ||
          $(el).find("img").attr("src") ||
          null;

        if (!title) return;

        const isExhibition = href.includes("/vystavka/");
        const isLab = href.includes("/laboratory/");

        const fullUrl = href.startsWith("http")
          ? href
          : `https://artarsenal.in.ua${href}`;

        events.push({
          id: `arsenal-${i}`,
          title,
          description: "Мистецький Арсенал, вул. Івана Мазепи, 28-30",
          category: isExhibition ? "exhibition" : isLab ? "workshop" : "exhibition",
          starts_at: null,
          ends_at: null,
          location_name: VENUE.name,
          latitude: VENUE.lat,
          longitude: VENUE.lon,
          ticket_url: fullUrl,
          price_from: null,
          price_to: null,
          cover_url: imgUrl,
          category_translations: {
            exhibition: "Виставка",
            workshop: "Майстерня",
          },
        });
      } catch (e) {
        console.warn("[arsenal] parse error:", e.message);
      }
    });

    if (events.length === 0) {
      $("a[href*='/vystavka/']").each((i, el) => {
        const title = $(el).text().trim();
        const href = $(el).attr("href") || "";
        if (!title || title.length < 3 || title.length > 200) return;
        if (events.find((e) => e.title === title)) return;

        events.push({
          id: `arsenal-fb-${i}`,
          title,
          description: null,
          category: "exhibition",
          starts_at: null,
          ends_at: null,
          location_name: VENUE.name,
          latitude: VENUE.lat,
          longitude: VENUE.lon,
          ticket_url: href.startsWith("http") ? href : `https://artarsenal.in.ua${href}`,
          price_from: null,
          price_to: null,
          cover_url: null,
          category_translations: { exhibition: "Виставка" },
        });
      });
    }

    return events;
  } catch (e) {
    console.error("[arsenal] scrape failed:", e.message);
    return [];
  }
}

module.exports = { scrape, venue: VENUE };
