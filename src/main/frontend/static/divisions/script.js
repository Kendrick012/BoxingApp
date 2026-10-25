document.addEventListener("DOMContentLoaded", () => {
    console.log("JS is working ✅");

    const btn = document.getElementById("testBtn");
    btn.addEventListener("click", () => {
        alert("Button clicked! 🥊");
    });
    const btn2 = document.getElementById("testsBtn");
    btn2.addEventListener("click", () => {
        alert("Button clicked! 🥊");
    });
    const btn3 = document.getElementById("testBtns");
    btn3.addEventListener("click", () => {
        alert("Button clicked! 🥊");
    });

    loadBoxers();
});

/* ============================
   Fighter cards from the database
   ============================ */

// Where the API lives. If this page is opened from WebServer (port 8080), use the same server.
// If it's opened from somewhere else, like VS Code's Live Server (port 5500), ask
// WebServer on port 8080 for the data. WebServer has to be running either way.
const API_BASE = window.location.port === "8080" ? "" : "http://localhost:8080";

// Fighter photos live in src/main/frontend/static/fighters/.
// In boxers.csv, image_url can just be the file name (e.g. oscar-collazo.jpg).
// A full web address (https://...) or a path with a "/" in it is used exactly as written.
const PHOTO_FOLDER = "../fighters/";

function photoUrl(imageUrl) {
    return imageUrl.includes("/") ? imageUrl : PHOTO_FOLDER + imageUrl;
}

// Asks the API for every boxer in this page's division, then draws a card for each one.
// The server already sorts them: champion first, then #1, #2, ... then unranked.
async function loadBoxers() {
    const container = document.getElementById("boxers");
    if (!container) return;

    const division = container.dataset.division;

    try {
        const response = await fetch(`${API_BASE}/api/boxers?division=${encodeURIComponent(division)}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || `Server answered ${response.status}`);
        }

        container.innerHTML = "";
        if (data.length === 0) {
            showMessage(container, `No ${division} fighters yet. Add some to boxers.csv and restart the server.`);
            return;
        }
        // If boxers have an organization (WBA, WBC, IBF, WBO ...), show one section per organization.
        // The server already sorts them by organization, then rank.
        const hasOrganizations = data.some(boxer => boxer.organization);
        let currentGroup;
        data.forEach(boxer => {
            const group = boxer.organization || "Overall";
            if (hasOrganizations && group !== currentGroup) {
                const heading = document.createElement("h3");
                heading.className = "org-heading";
                heading.textContent = group;
                container.appendChild(heading);
                currentGroup = group;
            }
            container.appendChild(makeCard(boxer));
        });
    } catch (err) {
        console.error(err);
        showMessage(container,
            "Couldn't load fighters. Make sure WebServer.java is running in VS Code (it serves the data on port 8080).");
    }
}

// Builds one fighter card. Uses textContent (not innerHTML) so text from the
// database is always shown as plain text and can't break the page.
function makeCard(boxer) {
    const card = document.createElement("div");
    card.className = "card fighter-card";
    if (boxer.rank === 0) card.classList.add("champion");

    // Photo, or the fighter's initials if there's no photo yet
    if (boxer.image_url) {
        const img = document.createElement("img");
        img.src = photoUrl(boxer.image_url);
        img.alt = boxer.name;
        // If the photo file can't be found, show the initials instead of a broken image
        img.onerror = () => {
            console.warn(`Photo not found for ${boxer.name}: ${img.src}`);
            img.replaceWith(makePlaceholder(boxer.name));
        };
        card.appendChild(img);
    } else {
        card.appendChild(makePlaceholder(boxer.name));
    }

    // Rank badge: "Champion", "Regular Champion", "Interim Champion", "#1", "#2", ...
    // (the database stores C as 0, SC as 0.5 and IC as 0.7)
    if (boxer.rank !== null) {
        const badge = document.createElement("span");
        badge.className = "rank-badge";
        const champions = { 0: "Champion", 0.5: "Regular Champion", 0.7: "Interim Champion" };
        badge.textContent = champions[boxer.rank] || `#${boxer.rank}`;
        if (boxer.rank === 0.5 || boxer.rank === 0.7) card.classList.add("secondary-champion");
        card.appendChild(badge);
    }

    const name = document.createElement("h1");
    name.textContent = boxer.name;
    card.appendChild(name);

    if (boxer.nickname) {
        const nickname = document.createElement("div");
        nickname.className = "nickname";
        nickname.textContent = `"${boxer.nickname}"`;
        card.appendChild(nickname);
    }

    addStat(card, "Belts", boxer.titles);
    addStat(card, "Record", `${boxer.wins}-${boxer.losses}-${boxer.draws}`);
    addStat(card, "KO %", boxer.ko_percentage !== null ? `${boxer.ko_percentage}% (${boxer.kos} KOs)` : null);
    addStat(card, "Nationality", boxer.nationality);
    addStat(card, "Age", boxer.age);
    addStat(card, "Height", boxer.height_cm ? `${boxer.height_cm} cm` : null);
    addStat(card, "Reach", boxer.reach_cm ? `${boxer.reach_cm} cm` : null);
    addStat(card, "Stance", boxer.stance);

    return card;
}

// Adds one "LABEL ...... value" row. Skips it if there's no value.
function addStat(card, label, value) {
    if (value === null || value === undefined || value === "") return;

    const row = document.createElement("p");
    const labelSpan = document.createElement("span");
    labelSpan.className = "stat-label";
    labelSpan.textContent = label;
    const valueSpan = document.createElement("span");
    valueSpan.className = "stat-value";
    valueSpan.textContent = value;

    row.append(labelSpan, valueSpan);
    card.appendChild(row);
}

function makePlaceholder(name) {
    const placeholder = document.createElement("div");
    placeholder.className = "photo-placeholder";
    placeholder.textContent = initials(name);
    return placeholder;
}

function initials(name) {
    return name.split(" ").map(part => part[0]).join("").slice(0, 2).toUpperCase();
}

function showMessage(container, text) {
    container.innerHTML = "";
    const message = document.createElement("p");
    message.className = "status-message";
    message.textContent = text;
    container.appendChild(message);
}
