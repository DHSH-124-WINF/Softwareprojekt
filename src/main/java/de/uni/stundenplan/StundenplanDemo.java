package de.uni.stundenplan;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import de.uni.stundenplan.ausgabe.StundenplanAusgabe;
import de.uni.stundenplan.daten.Eingabedaten;
import de.uni.stundenplan.modell.StundenplanModell;
import de.uni.stundenplan.modell.StundenplanModell.Zuordnung;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <h2>Mini-Stundenplan mit CP-SAT: von der Eingabe bis zum fertigen Plan</h2>
 *
 * <p>Das Hauptprogramm. Es zeigt in sieben Schritten den ganzen Weg, den jedes CP-SAT-Programm geht. Jeder Schritt
 * hat unten im Quelltext einen Erklärblock und schreibt eine Überschrift {@code === Schritt n: ... ===} auf die
 * Konsole.</p>
 * <ol>
 *   <li><b>OR-Tools laden</b> – die native Solver-Bibliothek; sie rechnet lokal in diesem Prozess.</li>
 *   <li><b>Eingabedaten</b> – Gruppen, Räume, Module und die eingereichten Zeiten ({@code Eingabedaten}).</li>
 *   <li><b>Variablen anlegen</b> – eine Ja/Nein-Frage pro Möglichkeit ({@code StundenplanModell}).</li>
 *   <li><b>Regeln hinzufügen</b> – acht harte Regeln, die jeder Plan erfüllen muss.</li>
 *   <li><b>Ziel festlegen</b> – möglichst wenige leere Plätze (optional).</li>
 *   <li><b>Lösen</b> – der {@code CpSolver} sucht den besten gültigen Plan.</li>
 *   <li><b>Ergebnis auswerten</b> – Status prüfen, Variablen auslesen, Stundenplan ausgeben
 *       ({@code StundenplanAusgabe}).</li>
 * </ol>
 *
 * <h3>Starten</h3>
 * <p>In IntelliJ die Run-Konfiguration "Stundenplan-Demo" starten, oder mit Maven:
 * {@code mvnw.cmd compile exec:java}.</p>
 *
 * <h3>Zum Ausprobieren</h3>
 * <ul>
 *   <li><b>Unlösbar machen:</b> In {@code Eingabedaten.beispiel()} reicht Prof. Schmidt nur die drei
 *       Donnerstag-Zeiten ein. Seine fünf Termine passen nicht in drei Zeitslots: Status INFEASIBLE, kein Plan.</li>
 *   <li><b>Gruppe vergrößern:</b> WINF auf 50 Studierende setzen. WINF passt nicht mehr in den Seminarraum
 *       (40 Plätze) und muss in den Hörsaal: 430 statt 170 leere Plätze. Regel 4 sorgt dafür, dass INF und WINF
 *       den Hörsaal nicht gleichzeitig belegen.</li>
 *   <li><b>Eine Regel weglassen:</b> In Schritt 4 Regel 3 (Raum groß genug) auskommentieren. INF landet im zu
 *       kleinen Seminarraum, denn 40 - 90 = -50 "leere Plätze" sind für das Ziel sogar besser (Zielwert -230).
 *       Der Solver nutzt jede Lücke in den Regeln aus. Ohne Regel 1 plant er gar nichts (0 leere Plätze), ohne
 *       Regel 5 steht ein Professor zur selben Zeit in zwei Räumen.</li>
 *   <li><b>Ziel weglassen:</b> Schritt 5 auskommentieren. Der Solver nimmt den ersten gültigen Plan und meldet
 *       trotzdem OPTIMAL. Dass WINF dabei im Seminarraum sitzt, ist Zufall: Stehen die Räume in den Eingabedaten
 *       andersherum (Seminarraum zuerst), sind es 330 statt 170 leere Plätze.</li>
 *   <li><b>Ziel umdrehen:</b> In {@code StundenplanModell.zielWenigeLeerePlaetze()} {@code maximize} statt
 *       {@code minimize} aufrufen. Jetzt sitzt WINF absichtlich im Hörsaal: 490 leere Plätze.</li>
 *   <li><b>Daten erweitern:</b> ein Modul, einen Raum oder eine dritte Studiengruppe ergänzen. Die Regeln passen
 *       sich von selbst an, weil sie über die Daten laufen.</li>
 * </ul>
 */
public final class StundenplanDemo {

    private StundenplanDemo() {
    }

    /**
     * Startet die Demo.
     *
     * @param args wird nicht verwendet
     */
    public static void main(String[] args) {

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 1 | Loader.loadNativeLibraries() | OR-Tools laden
        //   Fachlich:      OR-Tools ist in C++ geschrieben. "ortools-java" ist nur eine dünne Java-Hülle; die
        //                  eigentliche Rechenarbeit macht eine native Bibliothek.
        //   So geht's:     Maven lädt dazu das passende Plattform-JAR (z. B. ortools-win32-x86-64). Der Aufruf
        //                  entpackt die Bibliothek daraus in einen Temp-Ordner und lädt sie in diesen Prozess.
        //   Lokal:         Alles läuft LOKAL in dieser JVM: keine Cloud, kein Netzwerk, kein Lizenzschlüssel.
        //                  Die Daten verlassen den Rechner nie.
        //   Achtung:       Ohne diesen Aufruf scheitert schon die erste Variable mit einem UnsatisfiedLinkError.
        //                  Also gleich am Anfang aufrufen (mehrfaches Aufrufen schadet nicht).
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 1: OR-Tools laden");
        Loader.loadNativeLibraries();
        System.out.println("Native Solver-Bibliothek geladen. CP-SAT rechnet lokal in diesem Prozess:");
        System.out.println("keine Cloud, kein Netzwerk, kein Lizenzschlüssel.");

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 2 | Eingabedaten.beispiel() | Die Eingabedaten
        //   Fachlich:      Das, was die Uni vor der Planung weiß: Studiengruppen mit ihrer Größe, Räume mit ihren
        //                  Plätzen, Module (wer unterrichtet was für wen, wie oft) und die Zeiten, die die
        //                  Professoren eingereicht haben.
        //   So geht's:     Ganz normale Java-Objekte (Records). Der Solver sieht sie nie direkt: Erst Schritt 3
        //                  und 4 übersetzen sie in Variablen und Regeln.
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 2: Eingabedaten");
        Eingabedaten daten = Eingabedaten.beispiel();
        StundenplanAusgabe.druckeEingabe(daten);

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 3 | modell.newBoolVar(...) | Die Variablen anlegen
        //   Fachlich:      Die offenen Entscheidungen: Für jede Kombination aus Modul, Zeitslot und Raum eine
        //                  Ja/Nein-Variable "findet das hier statt?". Details in StundenplanModell.legeVariablenAn.
        //   Achtung:       Hier wird noch NICHTS gerechnet. Das Modell ist nur eine Beschreibung des Problems.
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 3: Variablen anlegen");
        StundenplanModell modell = new StundenplanModell(daten);
        int anzahlVariablen = modell.legeVariablenAn();
        System.out.println(anzahlVariablen + " Ja/Nein-Variablen = " + daten.module().size() + " Module x "
                + daten.zeitslots().size() + " Zeitslots x " + daten.raeume().size() + " Räume.");
        System.out.println("Beispiel: \"" + modell.zuordnungen().get(0).findetStatt().getName()
                + "\" = 1 heißt: findet genau dann und dort statt.");

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 4 | addEquality, addAtMostOne, addLessOrEqual | Die Regeln hinzufügen
        //   Fachlich:      Was JEDER Plan erfüllen muss (harte Regeln). Jede Regel ist eine eigene Methode in
        //                  StundenplanModell, mit Erklärung. Eine fachliche Regel wird dabei meist zu vielen
        //                  Constraints, z. B. "Raum nicht doppelt belegt" zu einem je Raum und Zeitslot.
        //   Achtung:       Was nicht als Regel dasteht, gilt nicht. Zum Ausprobieren einzelne Zeilen
        //                  auskommentieren und schauen, was der Solver dann daraus macht.
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 4: Regeln hinzufügen");
        regel(1, "Jedes Modul bekommt seine Termine", "addEquality(Summe, n)",
                modell.regelTermineProWoche());
        regel(2, "Professor nur zu eingereichten Zeiten", "addEquality(x, 0)",
                modell.regelNurZuEingereichtenZeiten());
        regel(3, "Raum groß genug für die Gruppe", "addEquality(x, 0)",
                modell.regelRaumGrossGenug());
        regel(4, "Raum nicht doppelt belegt", "addAtMostOne",
                modell.regelRaumNichtDoppeltBelegt());
        regel(5, "Professor nicht doppelt belegt", "addAtMostOne",
                modell.regelProfessorNichtDoppeltBelegt());
        regel(6, "Studiengruppe nicht doppelt belegt", "addAtMostOne",
                modell.regelGruppeNichtDoppeltBelegt());
        regel(7, "Modul höchstens einmal pro Tag", "addAtMostOne",
                modell.regelModulHoechstensEinmalProTag());
        regel(8, "Gruppe höchstens 2 Termine pro Tag", "addLessOrEqual(Summe, 2)",
                modell.regelHoechstensZweiTermineProTag());
        System.out.println("Zusammen: " + modell.cpModell().model().getConstraintsCount() + " Constraints.");

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 5 | modell.minimize(...) | Das Ziel festlegen (optional)
        //   Fachlich:      Unter allen gültigen Plänen den besten finden: den mit den wenigsten leeren Plätzen.
        //                  Details in StundenplanModell.zielWenigeLeerePlaetze.
        //   Achtung:       Ohne Ziel reicht dem Solver der erste gültige Plan.
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 5: Ziel festlegen");
        modell.zielWenigeLeerePlaetze();
        System.out.println("Ziel: möglichst wenige leere Plätze (minimize).");
        System.out.println("Die Regeln MÜSSEN gelten, das Ziel ist ein Wunsch.");

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 6 | CpSolver.solve(modell) | Lösen
        //   Fachlich:      Jetzt wird gerechnet: Der Solver sucht einen Plan, der ALLE Regeln erfüllt, und
        //                  unter diesen den mit den wenigsten leeren Plätzen.
        //   So geht's:     Die Parameter steuern den Solver:
        //                  - setMaxTimeInSeconds(10): Zeitlimit. Danach liefert er den besten bis dahin
        //                    gefundenen Plan (Status FEASIBLE) oder UNKNOWN, falls er noch keinen hat.
        //                  - setNumWorkers(1): ein einziger Such-Thread. Dann läuft die Suche bei jedem Start
        //                    gleich ab, und alle sehen denselben Plan. Standard sind alle Prozessorkerne: Dann
        //                    laufen mehrere Suchstrategien parallel und tauschen Ergebnisse aus. Das ist bei
        //                    großen Modellen viel schneller, kann aber bei mehreren gleich guten Plänen von
        //                    Lauf zu Lauf einen anderen liefern.
        //                  solve() blockiert, bis der Solver fertig ist, und liefert den Status zurück.
        //   Achtung:       Ein unlösbares Problem ist KEINE Exception, sondern ein Ergebnis (Status INFEASIBLE).
        //                  Deshalb immer zuerst den Status prüfen, dann erst Werte lesen.
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 6: Lösen");
        CpSolver solver = new CpSolver();
        solver.getParameters()
                .setMaxTimeInSeconds(10.0)
                .setNumWorkers(1);
        CpSolverStatus status = solver.solve(modell.cpModell());
        System.out.println("Status: " + status + " (" + bedeutung(status) + ")");
        System.out.printf(Locale.GERMANY, "Rechenzeit: %.3f s%n", solver.wallTime());

        // ---------------------------------------------------------------------------------------------
        // SCHRITT 7 | solver.booleanValue(x) | Das Ergebnis auswerten
        //   Fachlich:      Aus 180 Ja/Nein-Antworten wird der Stundenplan.
        //   So geht's:     Nur bei OPTIMAL oder FEASIBLE gibt es eine Lösung. Dann fragt man für jede Zuordnung:
        //                  Ist ihre Variable 1? booleanValue(x) liefert true oder false (für Zahlen-Variablen gibt
        //                  es value(x)). Die Zuordnungen mit true sind die Termine des Plans.
        //                  objectiveValue() ist der Zielwert (hier: leere Plätze), bestObjectiveBound() die beste
        //                  bewiesene Schranke. Sind beide gleich, ist der Plan nachweislich optimal.
        //   Achtung:       Die Werte gehören zur letzten Antwort von solve(). Ohne Lösung gibt es keine Werte.
        // ---------------------------------------------------------------------------------------------
        ueberschrift("Schritt 7: Ergebnis auswerten");
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            if (status == CpSolverStatus.INFEASIBLE) {
                System.out.println("Kein Stundenplan möglich. Typische Ursachen: zu wenige eingereichte Zeiten,");
                System.out.println("zu kleine Räume oder zu viele Termine. Tipp: Regeln einzeln auskommentieren,");
                System.out.println("bis wieder ein Plan herauskommt. Dann weiß man, welche Regel im Weg ist.");
            } else if (status == CpSolverStatus.MODEL_INVALID) {
                System.out.println("Fehler im Modell: " + modell.cpModell().validate());
            } else {
                System.out.println("Kein Plan innerhalb des Zeitlimits. Mehr Zeit geben: setMaxTimeInSeconds.");
            }
            return;
        }
        List<Zuordnung> stundenplan = new ArrayList<>();
        for (Zuordnung zuordnung : modell.zuordnungen()) {
            if (solver.booleanValue(zuordnung.findetStatt())) {
                stundenplan.add(zuordnung);
            }
        }
        System.out.println(stundenplan.size() + " von " + anzahlVariablen + " Variablen sind 1. Das ist der Plan:");
        System.out.println();
        StundenplanAusgabe.druckeStundenplan(daten, stundenplan);
        System.out.println();
        StundenplanAusgabe.druckeLeerePlaetze(daten, stundenplan);
        System.out.println();
        if (!modell.cpModell().model().hasObjective()) {
            System.out.println("Kein Ziel gesetzt: Der Solver hat den ersten gültigen Plan genommen.");
            return;
        }
        System.out.println("Zielwert laut Solver: " + Math.round(solver.objectiveValue())
                + ", beste Schranke: " + Math.round(solver.bestObjectiveBound())
                + (status == CpSolverStatus.OPTIMAL ? " -> gleich, also nachweislich der beste Plan."
                        : " -> das Optimum liegt irgendwo dazwischen."));
    }

    /** Eine Zeile der Regel-Übersicht in Schritt 4. */
    private static void regel(int nummer, String text, String baustein, int anzahlConstraints) {
        System.out.printf("Regel %d  %-37s  %-24s  %3d Constraints%n", nummer, text, baustein, anzahlConstraints);
    }

    /** Was der Status bedeutet, in einem Satz. */
    private static String bedeutung(CpSolverStatus status) {
        return switch (status) {
            case OPTIMAL -> "bester Plan gefunden und bewiesen, dass es keinen besseren gibt";
            case FEASIBLE -> "gültiger Plan gefunden, aber nicht bewiesen, dass er der beste ist";
            case INFEASIBLE -> "bewiesen: Kein Plan erfüllt alle Regeln";
            case MODEL_INVALID -> "das Modell ist fehlerhaft, siehe modell.validate()";
            case UNKNOWN -> "Zeitlimit erreicht, bevor ein Plan oder ein Gegenbeweis gefunden wurde";
            default -> "unbekannter Status";
        };
    }

    /** Eine leere Zeile und eine Überschrift im Stil "=== Schritt 1: ... ===". */
    private static void ueberschrift(String text) {
        System.out.println();
        System.out.println("=== " + text + " ===");
    }
}
