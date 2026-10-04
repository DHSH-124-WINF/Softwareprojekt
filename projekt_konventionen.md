# Book of Standards: Projekt-Konventionen

## 1. Ticket-Management
* **Größe:** Arbeitsaufwand von maximal 1–2 Tagen. Größere Aufgaben zwingend in Sub-Tasks aufteilen.
* **Regel:** Keine Code-Änderung ohne zugehöriges Ticket mit klarer Definition of Done.

## 2. Branch-Konventionen
* **Format:** `<typ>/<ticket-id>-<kurze-beschreibung>`
* **Typen:** `feature`, `bugfix`, `hotfix`, `refactor`, `docs`
* **Beispiel:** `feature/PROJ-123-user-login`

## 3. Commit-Richtlinien
* **Umfang:** Atomic Commits (klein, in sich geschlossen, funktional). Keine Sammel-Commits.
* **Format:** `[TICKET-ID] <typ>: <beschreibung>` (Sprache: Englisch, Form: Imperativ)
* **Beispiel:** `[PROJ-123] feat: add login endpoint`

## 4. Merge Requests (MRs) & Reviews
* **Workflow:** Kein direkter Push auf Haupt-Branches (`main`/`develop`).
* **Voraussetzungen:** Mindestens 2 Approvals erforderlich.
* **Qualitätsschranken:** CI/CD-Pipelines und statische Code-Analyse (z. B. SonarQube) müssen fehlerfrei (grün) durchlaufen.
* **Umfang:** MRs klein halten (max. 300-500 Zeilen), um effektive Reviews zu ermöglichen.

## 5. Code Quality
* **Sprache:** Code, Variablen und Commits ausschließlich auf Englisch.
* **Prinzipien:** DRY (Don't Repeat Yourself) und KISS (Keep It Simple, Stupid).
* **Kommentare:** Der Code erklärt das "Was", Kommentare erklären ausschließlich das "Warum" (z. B. bei Workarounds).