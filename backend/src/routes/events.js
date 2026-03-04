const express = require("express");
const router = express.Router();
const { getEvents, getCacheInfo } = require("../scrapers");

router.get("/", async (req, res) => {
  const city = (req.query.city || "kyiv").toLowerCase();
  const lang = (req.query.lang || "uk").toLowerCase();

  if (city !== "kyiv") {
    return res.json([]);
  }

  try {
    const events = await getEvents();
    res.json(events);
  } catch (err) {
    console.error("[events] Error:", err.message);
    res.status(500).json({ error: "Failed to fetch events" });
  }
});

router.get("/status", (req, res) => {
  res.json(getCacheInfo());
});

module.exports = router;
