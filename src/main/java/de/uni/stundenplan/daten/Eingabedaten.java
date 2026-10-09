package de.uni.stundenplan.daten;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <h2>Die Eingabedaten: alles, was vor der Planung feststeht</h2>
 *
 * <p>Die Demo bildet nach, wie an der Uni heute ein Stundenplan entsteht:</p>
 * <ol>
 *   <li>Die Professorinnen und Professoren <b>reichen ihre Zeiten ein</b>, zu denen sie lehren können.</li>
 *   <li>Es steht fest, <b>wer welches Modul anbietet</b>, für welche Studiengruppe und wie oft pro Woche.</li>
 *   <li>Es steht fest, welche <b>Studiengruppen</b> es gibt und <b>wie groß</b> sie sind.</li>
 *   <li>Es gibt <b>große und kleine Räume</b>.</li>
 * </ol>
 * <p>Gesucht ist für jeden Termin eines Moduls ein Zeitslot und ein Raum. Das erledigt der Solver im
 * {@code StundenplanModell}. Diese Klasse enthält bewusst <b>keine</b> Solver-Logik, nur die Fakten: Im echten
 * Projekt kämen sie aus einer Datenbank oder einem Formular, an den Regeln würde das nichts ändern.</p>
 *
 * <h3>Das Beispiel auf einen Blick</h3>
 * <pre>
 * Zeitraster   Mo-Fr mit je 3 Zeitslots (08-10, 10-12, 14-16) = 15 Zeitslots pro Woche
 * Gruppen      INF   Informatik              90 Studierende (groß)
 *              WINF  Wirtschaftsinformatik   35 Studierende (klein)
 * Räume        Hörsaal       120 Plätze (groß)
 *              Seminarraum    40 Plätze (klein)
 * Module       INF   Programmierung 1 (Müller, 2x), Rechnernetze (Müller, 1x), Mathematik 1 (Schmidt, 2x)
 *              WINF  Datenbanken (Müller, 1x), Wirtschaftsmathematik (Schmidt, 2x), Statistik (Schmidt, 1x)
 * Zeiten       Prof. Müller    Mo ganztags, Di und Mi vormittags
 *              Prof. Schmidt   Mi ab 10 Uhr, Do ganztags, Fr vormittags
 * </pre>
 * <p>Beide Professoren unterrichten in <b>beiden</b> Studiengruppen. Deshalb kommt es auf die Regel "Professor
 * nicht doppelt belegt" wirklich an: Prof. Müller kann nicht gleichzeitig vor INF und vor WINF stehen.</p>
 *
 * <h3>Warum Records?</h3>
 * <p>Ein {@code record} ist eine unveränderliche Datenklasse: Konstruktor, Getter ({@code raum.plaetze()}),
 * {@code equals} und {@code hashCode} erzeugt Java automatisch. Zwei Records mit gleichen Werten sind gleich –
 * deshalb findet {@code eingereichteZeiten.contains(new Zeitslot("Mo", "08-10"))} den Slot, obwohl es ein neues
 * Objekt ist.</p>
 *
 * @param tage        die Wochentage, an denen geplant wird
 * @param zeitslots   alle Zeitslots der Woche (jeder Tag mit jeder Uhrzeit)
 * @param raeume      die Räume mit ihren Sitzplätzen
 * @param professoren die Lehrenden mit ihren eingereichten Zeiten
 * @param gruppen     die Studiengruppen mit ihrer Größe
 * @param module      die Module: was, für welche Gruppe, von wem, wie oft pro Woche
 */
public record Eingabedaten(List<String> tage, List<Zeitslot> zeitslots, List<Raum> raeume,
                           List<Professor> professoren, List<Studiengruppe> gruppen, List<Modul> module) {

    /**
     * Ein Zeitslot im Wochenraster, z. B. Montag 08-10 Uhr.
     *
     * @param tag     Wochentag, z. B. "Mo"
     * @param uhrzeit Uhrzeit, z. B. "08-10"
     */
    public record Zeitslot(String tag, String uhrzeit) {
        @Override
        public String toString() {
            return tag + " " + uhrzeit;
        }
    }

    /**
     * Ein Raum.
     *
     * @param name    Anzeigename
     * @param plaetze Sitzplätze (Kapazität)
     */
    public record Raum(String name, int plaetze) {
    }

    /**
     * Eine Professorin oder ein Professor mit den eingereichten Zeiten.
     *
     * @param name               Anzeigename, z. B. "Prof. Müller"
     * @param eingereichteZeiten die Zeitslots, zu denen gelehrt werden kann; alle anderen sind tabu
     */
    public record Professor(String name, Set<Zeitslot> eingereichteZeiten) {

        /** Hat die Person diesen Zeitslot eingereicht? */
        public boolean kannUm(Zeitslot zeitslot) {
            return eingereichteZeiten.contains(zeitslot);
        }
    }

    /**
     * Eine Studiengruppe, z. B. das erste Semester Informatik.
     *
     * @param kuerzel kurzer Name für die Ausgabe, z. B. "INF"
     * @param name    voller Name
     * @param groesse Anzahl der Studierenden
     */
    public record Studiengruppe(String kuerzel, String name, int groesse) {
    }

    /**
     * Ein Modul (eine Lehrveranstaltung): was, für wen, von wem, wie oft pro Woche.
     *
     * @param name            Name des Moduls
     * @param gruppe          die Studiengruppe, die das Modul hört
     * @param professor       wer es unterrichtet (fest vorgegeben, nicht vom Solver gewählt)
     * @param termineProWoche wie viele Termine (Zeitslots) es pro Woche braucht
     */
    public record Modul(String name, Studiengruppe gruppe, Professor professor, int termineProWoche) {
    }

    /** Prüft beim Anlegen, dass jede eingereichte Zeit im Wochenraster vorkommt (Schutz vor Tippfehlern). */
    public Eingabedaten {
        for (Professor professor : professoren) {
            for (Zeitslot zeit : professor.eingereichteZeiten()) {
                if (!zeitslots.contains(zeit)) {
                    throw new IllegalArgumentException(professor.name() + ": Zeitslot '" + zeit
                            + "' gibt es im Wochenraster nicht.");
                }
            }
        }
    }

    /**
     * Die Beispieldaten der Demo. Zum Ausprobieren einfach hier etwas ändern und die Demo neu starten.
     *
     * @return die Eingabedaten für den Mini-Stundenplan
     */
    public static Eingabedaten beispiel() {

        // Das Zeitraster: 5 Tage x 3 Zeitslots = 15 Zeitslots pro Woche.
        List<String> tage = List.of("Mo", "Di", "Mi", "Do", "Fr");
        List<String> uhrzeiten = List.of("08-10", "10-12", "14-16");
        List<Zeitslot> zeitslots = new ArrayList<>();
        for (String tag : tage) {
            for (String uhrzeit : uhrzeiten) {
                zeitslots.add(new Zeitslot(tag, uhrzeit));
            }
        }

        // Räume: ein großer und ein kleiner.
        Raum hoersaal = new Raum("Hörsaal", 120);
        Raum seminarraum = new Raum("Seminarraum", 40);

        // Studiengruppen: eine große und eine kleine.
        Studiengruppe informatik = new Studiengruppe("INF", "Informatik", 90);
        Studiengruppe wirtschaftsinformatik = new Studiengruppe("WINF", "Wirtschaftsinformatik", 35);

        // Die Professoren und die Zeiten, die sie eingereicht haben.
        Professor mueller = new Professor("Prof. Müller", zeiten(
                "Mo 08-10", "Mo 10-12", "Mo 14-16",
                "Di 08-10", "Di 10-12",
                "Mi 08-10", "Mi 10-12"));
        Professor schmidt = new Professor("Prof. Schmidt", zeiten(
                "Mi 10-12", "Mi 14-16",
                "Do 08-10", "Do 10-12", "Do 14-16",
                "Fr 08-10", "Fr 10-12"));

        // Die Module: wer bietet was für welche Gruppe an, und wie oft pro Woche?
        List<Modul> module = List.of(
                new Modul("Programmierung 1", informatik, mueller, 2),
                new Modul("Rechnernetze", informatik, mueller, 1),
                new Modul("Mathematik 1", informatik, schmidt, 2),
                new Modul("Datenbanken", wirtschaftsinformatik, mueller, 1),
                new Modul("Wirtschaftsmathematik", wirtschaftsinformatik, schmidt, 2),
                new Modul("Statistik", wirtschaftsinformatik, schmidt, 1));

        return new Eingabedaten(tage, List.copyOf(zeitslots), List.of(hoersaal, seminarraum),
                List.of(mueller, schmidt), List.of(informatik, wirtschaftsinformatik), module);
    }

    /** Wandelt eingereichte Zeiten wie "Mo 08-10" in Zeitslots um. */
    private static Set<Zeitslot> zeiten(String... texte) {
        List<Zeitslot> ergebnis = new ArrayList<>();
        for (String text : texte) {
            String[] teile = text.split(" ");
            ergebnis.add(new Zeitslot(teile[0], teile[1]));
        }
        return Set.copyOf(ergebnis);
    }
}
