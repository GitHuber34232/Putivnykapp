const cheerio = require("cheerio");
const { MONTHS_UK } = require("./utils");

const VENUE = {
  name: "Музей Ханенків",
  lat: 50.4396,
  lon: 30.5115,
  category: "exhibition",
};

async function scrape() {
  try {
    const res = await fetch("https://khanenko.museum/podiyi-ta-programy/", {
      headers: { "User-Agent": "PutivnykBot/1.0" },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return [];
    const html = await res.text();
    const $ = cheerio.load(html);
    const events = [];

    $("#events_grid_content .grid_item, #events_grid_content a.grid_item").each((i, el) => {
      try {
        const $el = $(el);
        const title = $el.find(".title").text().trim();
        const dateText = $el.find(".date").text().trim();
        const href = $el.attr("href") || "";
        const img = $el.find("figure img").attr("src") || null;

        if (!title) return;

        const fullUrl = href.startsWith("http")
          ? href
          : `https://khanenko.museum${href}`;

        let startsAt = null;
        let endsAt = null;

        const singleDate = dateText.match(
          /(\d{1,2})\s+(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня)/i
        );
        const timeMatch = dateText.match(/(\d{1,2}:\d{2})/);

        if (singleDate) {
          const dd = singleDate[1].padStart(2, "0");
          const mm = MONTHS_UK[singleDate[2].toLowerCase()];
          const yyyy = dateText.match(/(\d{4})/)
            ? dateText.match(/(\d{4})/)[1]
            : new Date().getFullYear();
          const time = timeMatch ? timeMatch[1] : "10:30";
          startsAt = `${yyyy}-${mm}-${dd}T${time.padStart(5, "0")}:00+02:00`;

          const endTimeMatch = dateText.match(/[–\-]\s*(\d{1,2}:\d{2})/);
          if (endTimeMatch) {
            endsAt = `${yyyy}-${mm}-${dd}T${endTimeMatch[1].padStart(5, "0")}:00+02:00`;
          }
        }

        const isExhibition = title.includes("Виставка") || title.includes("виставк");
        const isExcursion = title.includes("Екскурсія") || title.includes("екскурсі");
        const category = isExcursion ? "tour" : "exhibition";

        events.push({
          id: `khanenko-${i}`,
          title,
          description: dateText || null,
          category,
          starts_at: startsAt,
          ends_at: endsAt,
          location_name: VENUE.name,
          latitude: VENUE.lat,
          longitude: VENUE.lon,
          ticket_url: fullUrl,
          price_from: null,
          price_to: null,
          cover_url: img,
          category_translations: {
            exhibition: "Виставка",
            tour: "Екскурсія",
          },
        });
      } catch (e) {
        console.warn("[khanenko] parse error:", e.message);
      }
    });

    return events;
  } catch (e) {
    console.error("[khanenko] scrape failed:", e.message);
    return [];
  }
}

module.exports = { scrape, venue: VENUE };
