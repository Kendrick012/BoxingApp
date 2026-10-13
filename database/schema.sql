-- Boxing App database tables (SQLite)
--
-- Don't add data here. Put weight classes in divisions.csv and boxers in boxers.csv,
-- then run DatabaseLoader.java. It runs this file first, so every run starts fresh.

-- Delete old tables (boxers first, because it points to divisions)
DROP TABLE IF EXISTS boxers;
DROP TABLE IF EXISTS divisions;

-- Weight classes, e.g. Strawweight / 105
CREATE TABLE divisions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL UNIQUE,
    weight_limit_lbs REAL
);

-- One row per boxer
CREATE TABLE boxers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    nickname    TEXT,
    division_id INTEGER REFERENCES divisions(id),
    rank        INTEGER,  -- 0 = champion, 1 = #1 contender, 2 = #2 ... (empty = unranked)
    titles      TEXT,     -- belts held, e.g. WBC/IBF (empty = none)
    nationality TEXT,
    stance      TEXT,
    age         INTEGER,
    height_cm   INTEGER,
    reach_cm    INTEGER,
    wins        INTEGER DEFAULT 0,
    losses      INTEGER DEFAULT 0,
    draws       INTEGER DEFAULT 0,
    kos         INTEGER DEFAULT 0,  -- wins by knockout
    -- KO percentage is calculated automatically from kos and wins (don't put it in the CSV)
    ko_percentage REAL GENERATED ALWAYS AS (
        CASE WHEN wins > 0 THEN ROUND(kos * 100.0 / wins, 1) END
    ) VIRTUAL,
    image_url   TEXT,
    UNIQUE (division_id, rank)  -- two boxers in one division can't share a rank
);
