# BoxingPedia 🥊

A boxing encyclopedia website that shows the champion and top contenders in every weight class, from strawweight to heavyweight.

The fighter data lives in an SQL database. A small Java web server reads the database and sends the data to the website through a JSON API.

## Features

- **17 weight classes**, from Strawweight (105 lbs) to Heavyweight
- **Rankings by organization**: each division page shows a section for the WBA, WBC, IBF and WBO, each with its champion (gold card) and ranked contenders
- **Fighter stats**: record, KO percentage, belts, nationality, age, height, reach and stance
- **Automatic KO percentage**, calculated by the database from wins and KOs
- **Easy data entry**: fighters are kept in a CSV file and loaded into the database with one click
- **JSON API** that other programs can use

## Tech stack

| Part | Technology |
|---|---|
| Frontend | HTML, CSS, JavaScript |
| Backend | Java 17+ (built-in `HttpServer`) |
| Database | SQLite, through the [`sqlite-jdbc`](https://github.com/xerial/sqlite-jdbc) driver |
| Build tool | Maven |

## How it works

```
Website (HTML/JS)  ──►  Java API (WebServer.java)  ──►  SQLite database (boxing.db)
                                                              ▲
                                    boxers.csv + divisions.csv ┘ (loaded by DatabaseLoader.java)
```

1. Fighters and weight classes are written in CSV files in the `database` folder.
2. `DatabaseLoader.java` creates the tables from `schema.sql` and loads the CSV files into `boxing.db`.
3. `WebServer.java` serves the website and answers API requests with data from `boxing.db`.
4. Each division page uses JavaScript to ask the API for its fighters and builds a card for each one.

## Project structure

```
BoxingApp/
├── database/
│   ├── schema.sql          # Table definitions
│   ├── divisions.csv       # The 17 weight classes
│   ├── boxers.csv          # All fighters (edit this to add or update fighters)
│   └── boxing.db           # Generated database (not in Git)
├── src/main/
│   ├── java/com/boxingapp/
│   │   ├── WebServer.java       # Runs the website + API on port 8080
│   │   └── DatabaseLoader.java  # Rebuilds boxing.db from the CSV files
│   └── frontend/static/
│       ├── index.html           # Home page with all divisions
│       ├── divisions/           # Division pages (straw.html, ...)
│       ├── fighters/            # Fighter photos
│       └── pics/                # Division images
└── pom.xml                 # Maven settings (SQLite driver)
```

## Getting started

### What you need

- **Java 17 or newer** (JDK)
- **VS Code** with the **Extension Pack for Java**, or any Java IDE that supports Maven

### Run it

1. Clone the repository and open the `BoxingApp` folder in VS Code.
2. Wait for VS Code to finish importing the Maven project. The first time, it downloads the SQLite driver.
3. Open `src/main/java/com/boxingapp/WebServer.java` and click **Run**.
4. Open **http://localhost:8080** in your browser.

When the server starts, it rebuilds the database from the CSV files, so you don't need to run `DatabaseLoader` separately.

## Adding fighters

Fighters are kept in `database/boxers.csv`, one fighter per line:

```csv
name,nickname,organization,division,rank,titles,nationality,stance,age,height_cm,reach_cm,wins,losses,draws,kos,image_url
Oscar Collazo,El Pupilo,WBO,Strawweight,C,WBA/WBO,Puerto Rico,Southpaw,29,157,155,15,0,0,12,oscar-collazo.jpg
```

| Column | What to enter |
|---|---|
| `organization` | Whose ranking this is: `WBA`, `WBC`, `IBF`, `WBO`. Leave empty for an overall ranking. A boxer ranked by several organizations gets one line per organization |
| `division` | Must match a name in `divisions.csv` exactly, e.g. `Super Bantamweight` |
| `rank` | `C` champion, `SC` regular champion, `IC` interim champion, then `1`, `2`, `3`... for contenders, empty for unranked. Each organization has its own champion and ranks. Leave out a vacant position |
| `titles` | Belts held, separated by `/`, e.g. `WBA/WBO` (not commas) |
| `kos` | Number of wins by knockout. KO % is calculated automatically |
| `image_url` | File name of a photo in `src/main/frontend/static/fighters/` |

Lines without commas (like `STRAWWEIGHT` or `=====`) are skipped, so you can use them as headings to organize the file.

After editing, save the file and restart `WebServer`. If something in the file is wrong, like a misspelled division, two champions in one division or more KOs than wins, the loader tells you the file and line number and doesn't change the database.

## API

| Endpoint | Returns |
|---|---|
| `GET /api/divisions` | All weight classes, lightest first |
| `GET /api/boxers?division=Strawweight` | Fighters in a division, grouped by organization, champion first |
| `GET /api/boxer?id=1` | One fighter |

Example response from `/api/boxers?division=Strawweight`:

```json
[
  {
    "id": 1,
    "name": "Oscar Collazo",
    "nickname": "El Pupilo",
    "organization": "WBO",
    "division": "Strawweight",
    "rank": 0,
    "titles": "WBA/WBO",
    "wins": 15, "losses": 0, "draws": 0, "kos": 12,
    "ko_percentage": 80.0
  }
]
```

`rank` 0 means champion.

## Roadmap

- [x] Database and loader
- [x] Java API
- [x] Strawweight page connected to the database
- [ ] Fighters for the other 16 divisions
- [ ] Pages for every division
- [ ] Pound-for-pound page
- [ ] Fighter profile pages
- [ ] AI program that answers boxing questions using the API

## Data sources

Champions and rankings are based on [ESPN's boxing champions list](https://www.espn.com/boxing/story/_/id/12370125/boxing-champions-list) and the [Transnational Boxing Rankings Board (TBRB)](https://tbrb.org/mens-rankings). Fighter stats are entered by hand and may not be up to date.

## Author

Built by **Kendrick**.
