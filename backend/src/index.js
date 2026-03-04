const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const compression = require("compression");
const eventsRouter = require("./routes/events");

const app = express();
const PORT = parseInt(process.env.PORT, 10) || 3000;

app.use(helmet());
app.use(cors());
app.use(compression());
app.use(express.json());

app.get("/", (_req, res) => {
  res.json({
    service: "putivnyk-api",
    version: "2.0.0",
    status: "ok",
    description: "Live events from Kyiv theaters, museums & venues",
    sources: [
      "opera.com.ua",
      "molodyytheatre.com",
      "ft.org.ua",
      "artarsenal.in.ua",
      "khanenko.museum",
      "vdng.ua",
    ],
  });
});

app.get("/health", (_req, res) => res.json({ status: "ok" }));

app.use("/events", eventsRouter);

app.use((_req, res) => {
  res.status(404).json({ error: "Not found" });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`putivnyk-api listening on port ${PORT}`);
});
