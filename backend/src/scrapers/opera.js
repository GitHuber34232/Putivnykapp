const cheerio = require("cheerio");

const VENUE = {
  name: "Національна опера України",
  lat: 50.4469,
  lon: 30.5104,
  category: "theater",
};

async function scrape() {
  const res = await fetch("https://opera.com.ua/afisha", {
    headers: { "User-Agent": "PutivnykBot/1.0" },
    signal: AbortSignal.timeout(10000),
  });
  if (!res.ok) return [];
  const html = await res.text();
  const $ = cheerio.load(html);
  const events = [];
  const now = new Date();
  const year = now.getFullYear();

  $(".views-row").each((i, el) => {
    try {
      const item = $(el).find(".item");
      if (!item.length) return;

      const day = item.find(".date").text().trim();
      const startTimeRaw = item.find(".row_date b").first().text().trim();
      const endTimeRaw = item.find(".row_date b").last().text().trim();
      const title = item.find(".right_part .title a").text().trim();
      const href = item.find(".right_part .title a").attr("href") || "";
      const author = item.find(".right_part .author").text().trim();
      const description = item.find(".left_part .row").first().text().trim();
      const img = item.find(".photo img").attr("src") || null;

      if (!title || !day) return;

      const [dd, mm] = day.split("/");
      if (!dd || !mm) return;

      const startsAt = `${year}-${mm}-${dd}T${startTimeRaw || "19:00"}:00+02:00`;
      const endsAt = endTimeRaw
        ? `${year}-${mm}-${dd}T${endTimeRaw}:00+02:00`
        : null;

      const ticketUrl = item.find(".bay a").attr("href") || `https://opera.com.ua${href}`;

      events.push({
        id: `opera-${mm}${dd}-${i}`,
        title,
        description: [author, description].filter(Boolean).join(". ") || null,
        category: "theater",
        starts_at: startsAt,
        ends_at: endsAt,
        location_name: VENUE.name,
        latitude: VENUE.lat,
        longitude: VENUE.lon,
        ticket_url: ticketUrl,
        price_from: null,
        price_to: null,
        cover_url: img,
        category_translations: { theater: "Театр" },
      });
    } catch (e) {
      console.warn("[opera] parse error:", e.message);
    }
  });

  return events;
}

module.exports = { scrape, venue: VENUE };
