# Softwareprojekt DHSH

Webanwendung zur Stundenplanung (ca. 40 Personen im Projektteam).

## Tech-Stack

| Bereich | Technologie | Version | Zweck |
|---|---|---|---|
| Backend | Java + Spring Boot | Java 25, Spring Boot 4.0.0 | REST-API und Geschäftslogik |
| Backend-Build | Maven | 3.9.x (getestet mit 3.9.16) | Multi-Modul-Build |
| Backend-Tests | JUnit/Spring Boot Test, ArchUnit | ArchUnit 1.4.1 | Tests, Prüfung der Modulgrenzen |
| Frontend | Vue + TypeScript | Vue 3.5.43 | Benutzeroberfläche im Browser |
| Frontend-Tooling | Node.js, Prettier | Node 22 (getestet mit 22.23.1), Prettier ^3.6.2 | Build, Formatierung |
| Datenbank | PostgreSQL + Flyway | noch festzulegen | Datenhaltung, versionierte Schema-Migrationen |
| Authentifizierung | Keycloak | noch festzulegen | Login, Benutzer und Rollen |
| Deployment | Docker + Pipeline | noch festzulegen | Automatisches Bauen und Ausrollen |
| Versionierung | GitLab / GitHub | – | Quellcode, Merge Requests, Boards |

**Datenbank:** Getrennte Dev- und Prod-Datenbank. Das Schema wird ausschließlich über nummerierte Flyway-Migrationen im Repo geändert (z. B. `V3__add_users_table.sql`), die beim Start automatisch laufen. Die Dev-Daten dürfen jederzeit zurückgesetzt werden, an Prod gibt es keine manuellen Änderungen.

**Umgebungen & Deployment:** Zwei Server, bereitgestellt von der DHSH-IT.
`Feature-Branch → Merge Request + Review → main → Pipeline (Docker-Build) → Test → Prod`
Mit der DHSH-IT zu klären: Verteilung der Dienste auf die Server, Pipeline-Zugänge, Ports und Domains.

**Branching:**
- `main` ist geschützt, kein direktes Pushen
- Merge Requests brauchen mindestens ein Review durch eine andere Person
- Einheitliche Namen: Branches z. B. `feature/login-seite`, `fix/rollen-bug`; Commits z. B. `feat: Login-Seite hinzugefügt`

**Offen:** Werden personenbezogene Daten gespeichert? Dann DSGVO frühzeitig mit der DHSH klären.

## Algorithmus
- Stundenplan = Zeitslot, Lehrveranstaltung, Lehrkraft, Raum, Gruppe
- Harte Constraints (z. B. keine Raum-Doppelbelegung) und weiche (z. B. wenig Raumwechsel)
- Geplantes Hilfsmittel: Google CP-SAT (Nutzung noch zu klären)

## UI/UX

**Grundsätze**
- Drag and Drop ist Pflicht
- Farbfeedback (grün = passt, gelb = vielleicht, rot = passt nicht) und örtlich logische Zuordnung
- Links Liste noch nicht zugewiesener Einträge, rechts große Übersicht (Wochenkalender bzw. Gebäudegrundriss)
- Design angelehnt an den Teams-Kalender, DHSH-Logo oben links, Navigationsleiste oben

**Views**
- **Login**
- **Startseite:** To-dos und Meldungen mit Konflikten
- **Nutzenden-Übersicht:** große Tabelle mit Name, Intern/Extern, Module, Rolle, Mail, Standort
- **Dozierenden-Modul-Zuordnung:** links Modulliste, rechts verfügbare Dozierende als Kacheln; Modul grün bei Zuordnung, rot wenn sie fehlt, gelb bei „vielleicht“. Ziel: Jedes Modul hat einen festen Dozierenden.
- **Kalender:** umschaltbar zwischen Tag, Woche, Monat, Semester und Jahr; filterbar nach Jahrgang, Studiengang und Dozierenden; Drag and Drop zwischen Liste und Kalender sowie innerhalb des Kalenders mit direktem Feedback zum Zeitpunkt
- **Raumplan:** links bereits zeitlich zugeordnete Module, rechts Übersicht je Etage, unten Timeline mit aktueller Zeit und Eingabefeld (evtl. nur für Restriktionen relevant, ggf. weglassen)
- **Übersichtsseite Restriktionen**
- **Optional:** Settings, Mailbox, Admin- und Userseite
