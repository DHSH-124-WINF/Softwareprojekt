package de.uni.stundenplan.ausgabe;

import de.uni.stundenplan.daten.Eingabedaten;
import de.uni.stundenplan.daten.Eingabedaten.Modul;
import de.uni.stundenplan.daten.Eingabedaten.Professor;
import de.uni.stundenplan.daten.Eingabedaten.Raum;
import de.uni.stundenplan.daten.Eingabedaten.Studiengruppe;
import de.uni.stundenplan.daten.Eingabedaten.Zeitslot;
import de.uni.stundenplan.modell.StundenplanModell.Zuordnung;
import java.util.List;
import java.util.Optional;

/**
 * <h2>Konsolenausgabe: Eingabedaten und fertiger Stundenplan</h2>
 *
 * <p>Reine Darstellung, kein Solver: Diese Klasse bekommt fertige Daten – die Eingabe oder die Liste der Termine,
 * die der Solver ausgewählt hat – und schreibt sie als Text-Tabellen auf die Konsole. Wie man die Termine aus
 * dem Solver herausliest, steht in {@code StundenplanDemo} (Schritt 7).</p>
 *
 * <p>Die Spaltenbreiten werden aus den Daten berechnet. Wer in den Eingabedaten Module, Räume oder Gruppen
 * ergänzt, bekommt also automatisch passende Tabellen.</p>
 */
public final class StundenplanAusgabe {

    private StundenplanAusgabe() {
    }

    // =================================================================================================
    // Eingabedaten
    // =================================================================================================

    /**
     * Schreibt die Eingabedaten als Tabellen: Gruppen, Räume, Module und die eingereichten Zeiten.
     *
     * @param daten die Eingabedaten
     */
    public static void druckeEingabe(Eingabedaten daten) {
        System.out.println("Studiengruppen:");
        int breiteKuerzel = maxLaenge(daten.gruppen().stream().map(Studiengruppe::kuerzel).toList());
        int breiteName = maxLaenge(daten.gruppen().stream().map(Studiengruppe::name).toList());
        for (Studiengruppe gruppe : daten.gruppen()) {
            System.out.printf("  %-" + breiteKuerzel + "s  %-" + breiteName + "s  %3d Studierende%n",
                    gruppe.kuerzel(), gruppe.name(), gruppe.groesse());
        }

        System.out.println();
        System.out.println("Räume:");
        int breiteRaum = maxLaenge(daten.raeume().stream().map(Raum::name).toList());
        for (Raum raum : daten.raeume()) {
            System.out.printf("  %-" + breiteRaum + "s  %3d Plätze%n", raum.name(), raum.plaetze());
        }

        System.out.println();
        System.out.println("Module (was, für welche Gruppe, von wem, wie oft pro Woche):");
        int breiteModul = maxLaenge(daten.module().stream().map(Modul::name).toList());
        int breiteProfessor = maxLaenge(daten.professoren().stream().map(Professor::name).toList());
        for (Modul modul : daten.module()) {
            System.out.printf("  %-" + breiteModul + "s  %-" + breiteKuerzel + "s  %-" + breiteProfessor
                            + "s  %dx pro Woche%n",
                    modul.name(), modul.gruppe().kuerzel(), modul.professor().name(), modul.termineProWoche());
        }

        System.out.println();
        System.out.println("Eingereichte Zeiten der Professoren (X = kann, . = kann nicht):");
        List<String> uhrzeiten = uhrzeiten(daten);
        int breiteUhrzeit = maxLaenge(uhrzeiten);
        StringBuilder kopf = new StringBuilder("  " + " ".repeat(breiteProfessor + 2 + breiteUhrzeit));
        for (String tag : daten.tage()) {
            kopf.append("  ").append(tag);
        }
        System.out.println(kopf);
        for (Professor professor : daten.professoren()) {
            boolean ersteZeile = true;
            for (String uhrzeit : uhrzeiten) {
                StringBuilder zeile = new StringBuilder(String.format("  %-" + breiteProfessor + "s  %-"
                        + breiteUhrzeit + "s", ersteZeile ? professor.name() : "", uhrzeit));
                for (String tag : daten.tage()) {
                    String zeichen = professor.kannUm(new Zeitslot(tag, uhrzeit)) ? "X" : ".";
                    zeile.append("  ").append(String.format("%-" + tag.length() + "s", zeichen));
                }
                System.out.println(zeile.toString().stripTrailing());
                ersteZeile = false;
            }
        }
    }

    // =================================================================================================
    // Der Stundenplan
    // =================================================================================================

    /**
     * Schreibt den Stundenplan aller Studiengruppen als eine große Tabelle: je Tag eine Zeile pro Gruppe, je Uhrzeit
     * eine Spalte. Jede belegte Zelle zeigt das Modul und darunter Professor und Raum.
     *
     * @param daten   die Eingabedaten (für Tage, Uhrzeiten und Gruppen)
     * @param termine die Zuordnungen, die der Solver ausgewählt hat (Variable = 1)
     */
    public static void druckeStundenplan(Eingabedaten daten, List<Zuordnung> termine) {
        List<String> uhrzeiten = uhrzeiten(daten);
        int breiteTag = Math.max("Tag".length(), maxLaenge(daten.tage()));
        int breiteGruppe = Math.max("Gruppe".length(),
                maxLaenge(daten.gruppen().stream().map(Studiengruppe::kuerzel).toList()));
        int breiteZelle = maxLaenge(uhrzeiten);
        for (Zuordnung termin : termine) {
            breiteZelle = Math.max(breiteZelle, Math.max(zeile1(termin).length(), zeile2(termin).length()));
        }

        String zellenTrenner = ("+" + "-".repeat(breiteZelle + 2)).repeat(uhrzeiten.size()) + "+";
        String trenner = "+" + "-".repeat(breiteTag + 2) + "+" + "-".repeat(breiteGruppe + 2) + zellenTrenner;
        String trennerInnen = "|" + " ".repeat(breiteTag + 2) + "+" + "-".repeat(breiteGruppe + 2) + zellenTrenner;

        System.out.println(trenner);
        System.out.println(zeile(breiteTag, breiteGruppe, breiteZelle, "Tag", "Gruppe", uhrzeiten));
        System.out.println(trenner);
        for (String tag : daten.tage()) {
            for (int g = 0; g < daten.gruppen().size(); g++) {
                Studiengruppe gruppe = daten.gruppen().get(g);
                List<Optional<Zuordnung>> zellen = uhrzeiten.stream()
                        .map(uhrzeit -> finde(termine, gruppe, new Zeitslot(tag, uhrzeit)))
                        .toList();
                System.out.println(zeile(breiteTag, breiteGruppe, breiteZelle, g == 0 ? tag : "", gruppe.kuerzel(),
                        zellen.stream().map(z -> z.map(StundenplanAusgabe::zeile1).orElse("")).toList()));
                System.out.println(zeile(breiteTag, breiteGruppe, breiteZelle, "", "",
                        zellen.stream().map(z -> z.map(StundenplanAusgabe::zeile2).orElse("")).toList()));
                if (g < daten.gruppen().size() - 1) {
                    System.out.println(trennerInnen);
                }
            }
            System.out.println(trenner);
        }
    }

    /**
     * Rechnet die leeren Plätze des Stundenplans nach: je Gruppe und Raum, wie oft und wie viele Plätze frei
     * bleiben. Die Summe muss dem Zielwert des Solvers entsprechen.
     *
     * @param daten   die Eingabedaten (für die Reihenfolge von Gruppen und Räumen)
     * @param termine die Zuordnungen, die der Solver ausgewählt hat
     * @return die Summe der leeren Plätze
     */
    public static long druckeLeerePlaetze(Eingabedaten daten, List<Zuordnung> termine) {
        System.out.println("Leere Plätze (Plätze des Raums minus Größe der Gruppe):");
        int breiteKuerzel = maxLaenge(daten.gruppen().stream().map(Studiengruppe::kuerzel).toList());
        int breiteRaum = maxLaenge(daten.raeume().stream().map(Raum::name).toList());
        long summe = 0;
        for (Studiengruppe gruppe : daten.gruppen()) {
            for (Raum raum : daten.raeume()) {
                long anzahl = termine.stream()
                        .filter(t -> t.modul().gruppe().equals(gruppe) && t.raum().equals(raum))
                        .count();
                if (anzahl > 0) {
                    long leer = raum.plaetze() - gruppe.groesse();
                    System.out.printf("  %-" + breiteKuerzel + "s  im %-" + breiteRaum
                                    + "s  %d Termine x %3d leere Plätze = %4d%n",
                            gruppe.kuerzel(), raum.name(), anzahl, leer, anzahl * leer);
                    summe += anzahl * leer;
                }
            }
        }
        System.out.println("  Zusammen: " + summe + " leere Plätze");
        return summe;
    }

    // =================================================================================================
    // Hilfsmethoden
    // =================================================================================================

    /** Die Uhrzeiten des Rasters in ihrer Reihenfolge, z. B. [08-10, 10-12, 14-16]. */
    private static List<String> uhrzeiten(Eingabedaten daten) {
        return daten.zeitslots().stream().map(Zeitslot::uhrzeit).distinct().toList();
    }

    /** Der Termin einer Gruppe in einem Zeitslot – oder leer, wenn die Gruppe dann frei hat. */
    private static Optional<Zuordnung> finde(List<Zuordnung> termine, Studiengruppe gruppe, Zeitslot zeitslot) {
        return termine.stream()
                .filter(t -> t.modul().gruppe().equals(gruppe) && t.zeitslot().equals(zeitslot))
                .findFirst();
    }

    /** Erste Zeile einer Zelle: das Modul. */
    private static String zeile1(Zuordnung termin) {
        return termin.modul().name();
    }

    /** Zweite Zeile einer Zelle: Professor (ohne Titel) und Raum, z. B. "Müller, Hörsaal". */
    private static String zeile2(Zuordnung termin) {
        return termin.modul().professor().name().replace("Prof. ", "") + ", " + termin.raum().name();
    }

    /** Eine Tabellenzeile: | Tag | Gruppe | Zelle | Zelle | ... | */
    private static String zeile(int breiteTag, int breiteGruppe, int breiteZelle, String tag, String gruppe,
                                List<String> zellen) {
        StringBuilder text = new StringBuilder();
        text.append("| ").append(links(tag, breiteTag)).append(" | ").append(links(gruppe, breiteGruppe)).append(" |");
        for (String zelle : zellen) {
            text.append(' ').append(links(zelle, breiteZelle)).append(" |");
        }
        return text.toString();
    }

    private static String links(String text, int breite) {
        return text + " ".repeat(breite - text.length());
    }

    private static int maxLaenge(List<String> texte) {
        return texte.stream().mapToInt(String::length).max().orElse(0);
    }
}
