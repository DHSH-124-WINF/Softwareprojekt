package de.uni.stundenplan.modell;

import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.LinearExpr;
import de.uni.stundenplan.daten.Eingabedaten;
import de.uni.stundenplan.daten.Eingabedaten.Modul;
import de.uni.stundenplan.daten.Eingabedaten.Professor;
import de.uni.stundenplan.daten.Eingabedaten.Raum;
import de.uni.stundenplan.daten.Eingabedaten.Studiengruppe;
import de.uni.stundenplan.daten.Eingabedaten.Zeitslot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * <h2>Das CP-SAT-Modell: Variablen, Regeln und Ziel</h2>
 *
 * <p>Hier wird aus den Eingabedaten ein Modell, das der Solver versteht. Ein CP-SAT-Modell hat genau drei
 * Zutaten:</p>
 * <ol>
 *   <li><b>Variablen</b> – die offenen Entscheidungen. Hier: "Findet Modul m im Zeitslot z im Raum r statt?"</li>
 *   <li><b>Regeln</b> (Constraints) – was jede Lösung erfüllen <i>muss</i> (harte Regeln).</li>
 *   <li><b>Ziel</b> (optional) – was unter allen gültigen Lösungen möglichst gut sein <i>soll</i>.</li>
 * </ol>
 * <p>Der Solver kennt keine Module, Räume oder Professoren, nur ganze Zahlen. Die Übersetzung von "Raum zu klein"
 * in "diese Variable muss 0 sein" ist unsere Aufgabe, und genau die zeigt diese Klasse. Gerechnet wird hier noch
 * nichts: Es entsteht nur die Beschreibung des Problems. Gelöst wird erst in {@code StundenplanDemo}.</p>
 *
 * <h3>Die Variablen: eine Ja/Nein-Frage pro Möglichkeit</h3>
 * <pre>
 * findetStatt(Modul, Zeitslot, Raum)  ∈ {0, 1}        1 = findet dort statt, 0 = nicht
 * 6 Module x 15 Zeitslots x 2 Räume = 180 Variablen
 * </pre>
 *
 * <h3>Das Rezept für jede Regel</h3>
 * <ol>
 *   <li><b>Wofür gilt die Regel?</b> Zum Beispiel "für jeden Raum und jeden Zeitslot" – das werden die
 *       Schleifen.</li>
 *   <li><b>Welche Variablen gehören dazu?</b> Alle Zuordnungen mit genau diesem Raum und Zeitslot – die sucht die
 *       Hilfsmethode {@code variablen(bedingung)} heraus.</li>
 *   <li><b>Was muss für sie gelten?</b> "höchstens eine ist 1", "Summe = n" oder "muss 0 sein" – das ist der
 *       CP-SAT-Baustein ({@code addAtMostOne}, {@code addEquality}, {@code addLessOrEqual}).</li>
 * </ol>
 *
 * <h3>Die Regeln und ihre CP-SAT-Bausteine</h3>
 * <pre>
 * Nr | Regel                                   | Regelart     | CP-SAT-Baustein
 * ---+-----------------------------------------+--------------+---------------------------
 *  1 | Jedes Modul bekommt seine Termine       | genau n      | addEquality(Summe, n)
 *  2 | Professor nur zu eingereichten Zeiten   | Verbot       | addEquality(x, 0)
 *  3 | Raum groß genug für die Gruppe          | Verbot       | addEquality(x, 0)
 *  4 | Raum nicht doppelt belegt               | höchstens 1  | addAtMostOne(...)
 *  5 | Professor nicht doppelt belegt          | höchstens 1  | addAtMostOne(...)
 *  6 | Studiengruppe nicht doppelt belegt      | höchstens 1  | addAtMostOne(...)
 *  7 | Modul höchstens einmal pro Tag          | höchstens 1  | addAtMostOne(...)
 *  8 | Gruppe höchstens 2 Termine pro Tag      | Obergrenze   | addLessOrEqual(Summe, 2)
 *  Z | Möglichst wenige leere Plätze           | Ziel (weich) | minimize(gewichtete Summe)
 * </pre>
 */
public final class StundenplanModell {

    /**
     * Eine mögliche Zuordnung "Modul m findet im Zeitslot z im Raum r statt" zusammen mit ihrer Variable.
     *
     * @param modul       welches Modul
     * @param zeitslot    wann
     * @param raum        wo
     * @param findetStatt die Ja/Nein-Variable des Solvers: 1 = diese Zuordnung kommt in den Stundenplan
     */
    public record Zuordnung(Modul modul, Zeitslot zeitslot, Raum raum, BoolVar findetStatt) {
    }

    /** Obergrenze für Regel 8: so viele Termine darf eine Studiengruppe höchstens an einem Tag haben. */
    public static final int MAX_TERMINE_PRO_TAG = 2;

    private final Eingabedaten daten;
    private final CpModel modell = new CpModel();
    private final List<Zuordnung> zuordnungen = new ArrayList<>();

    /**
     * Legt ein leeres Modell für die Eingabedaten an. Variablen, Regeln und Ziel kommen über die Methoden dazu.
     *
     * @param daten die Eingabedaten (Zeitslots, Räume, Professoren, Gruppen, Module)
     */
    public StundenplanModell(Eingabedaten daten) {
        this.daten = daten;
    }

    /**
     * Das eigentliche CP-SAT-Modell, das der Solver bekommt.
     *
     * @return das {@link CpModel}
     */
    public CpModel cpModell() {
        return modell;
    }

    /**
     * Alle möglichen Zuordnungen mit ihren Variablen. Nach dem Lösen liest man daraus den Stundenplan ab.
     *
     * @return die Zuordnungen (nur lesbar)
     */
    public List<Zuordnung> zuordnungen() {
        return Collections.unmodifiableList(zuordnungen);
    }

    // =================================================================================================
    // VARIABLEN
    // =================================================================================================

    /**
     * Legt für jede Kombination aus Modul, Zeitslot und Raum eine Ja/Nein-Variable an.
     *
     * @return die Anzahl der angelegten Variablen
     */
    public int legeVariablenAn() {
        // ---------------------------------------------------------------------------------------------
        // VARIABLEN | modell.newBoolVar(name) | Eine Ja/Nein-Frage pro Möglichkeit
        //   Fachlich:      Für jede Kombination aus Modul, Zeitslot und Raum fragen wir den Solver:
        //                  "Findet dieses Modul in diesem Zeitslot in diesem Raum statt – ja oder nein?"
        //                  Die Antworten sind der Stundenplan. Ein Modul mit 2 Terminen pro Woche hat am Ende
        //                  genau zwei "Ja" (dafür sorgt Regel 1).
        //   So geht's:     newBoolVar legt eine Variable mit dem Wertebereich {0, 1} an (BoolVar). Der Name ist
        //                  nur für Menschen (Fehlersuche, Export); der Solver rechnet mit einer internen Nummer.
        //                  Den Rückgabewert merken wir uns zusammen mit Modul, Zeitslot und Raum in einer
        //                  Zuordnung, damit die Regeln und später die Ausgabe wissen, wofür die Variable steht.
        //   Geeignet für:  Zuordnungsprobleme aller Art: "Wer oder was kommt wann und wohin?" Mit Ja/Nein-
        //                  Variablen lassen sich fast alle Stundenplan-Regeln als einfache Summen schreiben.
        //   Achtung:       Die Anzahl wächst multiplikativ: 6 Module x 15 Zeitslots x 2 Räume = 180. Eine echte
        //                  Uni mit 300 Modulen x 40 Zeitslots x 50 Räumen hätte 600.000 Variablen. Dann legt man
        //                  unmögliche Kombinationen (Raum zu klein, Professor nicht da) gar nicht erst an.
        // ---------------------------------------------------------------------------------------------
        for (Modul modul : daten.module()) {
            for (Zeitslot zeitslot : daten.zeitslots()) {
                for (Raum raum : daten.raeume()) {
                    BoolVar findetStatt = modell.newBoolVar(modul.name() + " | " + zeitslot + " | " + raum.name());
                    zuordnungen.add(new Zuordnung(modul, zeitslot, raum, findetStatt));
                }
            }
        }
        return zuordnungen.size();
    }

    // =================================================================================================
    // REGELN (harte Regeln: Jede Lösung MUSS sie erfüllen)
    // Jede Methode gibt zurück, wie viele Constraints sie angelegt hat.
    // =================================================================================================

    /**
     * Regel 1: Jedes Modul findet genau so oft statt, wie es Termine pro Woche hat.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelTermineProWoche() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 1 | addEquality(LinearExpr.sum(...), n) | Jedes Modul bekommt seine Termine pro Woche
        //   Fachlich:      Programmierung 1 findet zweimal pro Woche statt, Rechnernetze einmal.
        //   So geht's:     LinearExpr.sum(...) addiert alle Ja/Nein-Variablen eines Moduls über alle Zeitslots
        //                  und Räume. Weil jede Variable 0 oder 1 ist, zählt die Summe, wie oft das Modul
        //                  stattfindet. addEquality verlangt: Summe = termineProWoche.
        //   Geeignet für:  Mengen-Vorgaben: genau n (addEquality), mindestens n (addGreaterOrEqual), höchstens n
        //                  (addLessOrEqual, siehe Regel 8). Für "genau 1" gibt es die Kurzform addExactlyOne(...).
        //   Achtung:       Ohne diese Regel wäre "alles 0" (gar kein Unterricht) die beste Lösung: keine
        //                  Konflikte, keine leeren Plätze. Der Solver tut nur, was die Regeln verlangen.
        //                  Nichts ist "selbstverständlich".
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Modul modul : daten.module()) {
            BoolVar[] termineDesModuls = variablen(z -> z.modul().equals(modul));
            modell.addEquality(LinearExpr.sum(termineDesModuls), modul.termineProWoche());
            anzahl++;
        }
        return anzahl;
    }

    /**
     * Regel 2: Ein Modul findet nur zu Zeiten statt, die sein Professor eingereicht hat.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelNurZuEingereichtenZeiten() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 2 | addEquality(x, 0) | Professoren lehren nur zu ihren eingereichten Zeiten
        //   Fachlich:      Prof. Müller hat Mo ganztags sowie Di und Mi vormittags eingereicht. Zu allen anderen
        //                  Zeiten dürfen seine Module nicht stattfinden.
        //   So geht's:     Jede Zuordnung, deren Zeitslot der Professor des Moduls NICHT eingereicht hat, wird
        //                  mit addEquality(x, 0) auf 0 festgenagelt: "Diese Möglichkeit ist verboten."
        //   Geeignet für:  Feste Verbote, die schon in den Daten stehen: Sperrzeiten, Feiertage, Räume ohne
        //                  die nötige Ausstattung.
        //   Achtung:       Im echten Projekt legt man solche Variablen meist gar nicht erst an (Vorfilter in
        //                  legeVariablenAn) – das spart Speicher. Am Ergebnis ändert das nichts: Der Solver
        //                  erkennt festgenagelte Variablen vor der Suche (Presolve) und entfernt sie. Hier steht
        //                  das Verbot bewusst als eigene Regel da, damit man es sieht.
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Zuordnung zuordnung : zuordnungen) {
            Professor professor = zuordnung.modul().professor();
            if (!professor.kannUm(zuordnung.zeitslot())) {
                modell.addEquality(zuordnung.findetStatt(), 0);
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Regel 3: Der Raum muss groß genug für die Studiengruppe sein.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelRaumGrossGenug() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 3 | addEquality(x, 0) | Der Raum muss groß genug sein
        //   Fachlich:      INF hat 90 Studierende, der Seminarraum nur 40 Plätze: INF darf nie in den
        //                  Seminarraum. WINF (35) passt in beide Räume.
        //   So geht's:     Wie Regel 2: Jede Zuordnung mit zu kleinem Raum wird auf 0 festgenagelt.
        //   Geeignet für:  Alle Ja/Nein-Prüfungen, die man schon vor dem Lösen beantworten kann
        //                  (Kapazität, Ausstattung wie Beamer oder Labor, Barrierefreiheit).
        //   Achtung:       Das Ziel "wenige leere Plätze" würde einen zu kleinen Raum sogar belohnen:
        //                  40 - 90 = -50 leere Plätze. Erst diese Regel verhindert das. Zum Ausprobieren in
        //                  StundenplanDemo auskommentieren – dann sitzt INF im Seminarraum.
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Zuordnung zuordnung : zuordnungen) {
            if (zuordnung.raum().plaetze() < zuordnung.modul().gruppe().groesse()) {
                modell.addEquality(zuordnung.findetStatt(), 0);
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Regel 4: In einem Raum findet zur selben Zeit höchstens eine Veranstaltung statt.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelRaumNichtDoppeltBelegt() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 4 | addAtMostOne(...) | Ein Raum ist nicht doppelt belegt
        //   Fachlich:      Im Hörsaal kann um Mo 08-10 nur eine Veranstaltung stattfinden.
        //   So geht's:     Für jedes Paar (Raum, Zeitslot) sammeln wir alle Variablen, die genau diesen Raum zu
        //                  genau dieser Zeit belegen würden (über alle Module hinweg), und verlangen mit
        //                  addAtMostOne: Höchstens eine davon ist 1.
        //   Geeignet für:  Jede Ressource, die nur eins gleichzeitig kann: Räume, Personen, Gruppen, Geräte.
        //                  addAtMostOne bedeutet dasselbe wie "Summe <= 1", ist aber kürzer und für den Solver
        //                  die natürliche Form für Ja/Nein-Variablen.
        //   Achtung:       "Höchstens" erlaubt auch 0: Der Raum darf leer bleiben. "Genau eins" wäre
        //                  addExactlyOne. Java: Die Methode erwartet Literal[] oder Iterable<Literal>. Ein
        //                  BoolVar[] passt, eine List<BoolVar> nicht – deshalb liefert variablen(...) ein Array.
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Raum raum : daten.raeume()) {
            for (Zeitslot zeitslot : daten.zeitslots()) {
                modell.addAtMostOne(variablen(z -> z.raum().equals(raum) && z.zeitslot().equals(zeitslot)));
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Regel 5: Ein Professor unterrichtet zur selben Zeit höchstens eine Veranstaltung.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelProfessorNichtDoppeltBelegt() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 5 | addAtMostOne(...) | Ein Professor ist nicht doppelt belegt
        //   Fachlich:      Prof. Müller unterrichtet Programmierung 1 und Rechnernetze (INF) und außerdem
        //                  Datenbanken (WINF). Er kann nicht gleichzeitig in zwei Räumen stehen.
        //   So geht's:     Derselbe Baustein wie Regel 4, nur anders gruppiert: je Professor und Zeitslot alle
        //                  Variablen seiner Module, davon höchstens eine 1.
        //   Achtung:       Diese Regel verbindet die beiden Studiengruppen: Einen Zeitslot, in dem Prof. Müller
        //                  für INF unterrichtet, hat er für WINF nicht mehr. (Über die gemeinsamen Räume
        //                  verbindet sie auch Regel 4.)
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Professor professor : daten.professoren()) {
            for (Zeitslot zeitslot : daten.zeitslots()) {
                modell.addAtMostOne(variablen(
                        z -> z.modul().professor().equals(professor) && z.zeitslot().equals(zeitslot)));
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Regel 6: Eine Studiengruppe hat zur selben Zeit höchstens eine Veranstaltung.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelGruppeNichtDoppeltBelegt() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 6 | addAtMostOne(...) | Eine Studiengruppe ist nicht doppelt belegt
        //   Fachlich:      Die WINF-Studierenden können nicht gleichzeitig in Datenbanken und in Statistik sitzen.
        //                  Ohne diese Regel ginge das Mi 10-12: Da haben beide Professoren Zeit, und es gibt
        //                  zwei Räume.
        //   So geht's:     Je Studiengruppe und Zeitslot alle Variablen ihrer Module, davon höchstens eine 1.
        //   Achtung:       Bei INF greift zusätzlich schon Regel 3 mit Regel 4: INF passt nur in den Hörsaal,
        //                  und der ist nie doppelt belegt. Dass sich Regeln überschneiden, ist kein Fehler. Diese
        //                  Regel sagt trotzdem klar, was gemeint ist, und gilt auch noch, wenn ein zweiter
        //                  großer Raum dazukommt.
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Studiengruppe gruppe : daten.gruppen()) {
            for (Zeitslot zeitslot : daten.zeitslots()) {
                modell.addAtMostOne(variablen(
                        z -> z.modul().gruppe().equals(gruppe) && z.zeitslot().equals(zeitslot)));
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Regel 7: Ein Modul findet höchstens einmal pro Tag statt.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelModulHoechstensEinmalProTag() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 7 | addAtMostOne(...) | Ein Modul höchstens einmal pro Tag
        //   Fachlich:      Die zwei Termine von Programmierung 1 sollen an verschiedenen Tagen liegen, nicht
        //                  beide am Montag.
        //   So geht's:     Je Modul und Tag alle Variablen dieses Moduls an diesem Tag (alle Uhrzeiten, alle
        //                  Räume), davon höchstens eine 1.
        //   Achtung:       Regeln wirken zusammen: Mathematik 1 braucht zwei Tage, an denen Prof. Schmidt Zeit
        //                  hat. Reicht er nur einen Tag ein, gibt es keinen gültigen Plan (Status INFEASIBLE).
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Modul modul : daten.module()) {
            for (String tag : daten.tage()) {
                modell.addAtMostOne(variablen(z -> z.modul().equals(modul) && z.zeitslot().tag().equals(tag)));
                anzahl++;
            }
        }
        return anzahl;
    }

    /**
     * Regel 8: Eine Studiengruppe hat höchstens {@value #MAX_TERMINE_PRO_TAG} Termine pro Tag.
     *
     * @return die Anzahl der angelegten Constraints
     */
    public int regelHoechstensZweiTermineProTag() {
        // ---------------------------------------------------------------------------------------------
        // REGEL 8 | addLessOrEqual(LinearExpr.sum(...), 2) | Höchstens zwei Termine pro Tag und Gruppe
        //   Fachlich:      Keine Gruppe soll mehr als zwei Veranstaltungen an einem Tag haben. So verteilen sich
        //                  die Termine besser über die Woche.
        //   So geht's:     Die Summe zählt die Termine der Gruppe an diesem Tag; addLessOrEqual verlangt
        //                  Summe <= 2.
        //   Geeignet für:  Obergrenzen und Kapazitäten (Lehrdeputat, maximale Stunden pro Tag), mit
        //                  addGreaterOrEqual auch Untergrenzen.
        //   Achtung:       addAtMostOne (Regeln 4 bis 7) ist der Sonderfall "Summe <= 1". Für jede andere Grenze
        //                  nimmt man die lineare Regel.
        // ---------------------------------------------------------------------------------------------
        int anzahl = 0;
        for (Studiengruppe gruppe : daten.gruppen()) {
            for (String tag : daten.tage()) {
                BoolVar[] termineAmTag = variablen(
                        z -> z.modul().gruppe().equals(gruppe) && z.zeitslot().tag().equals(tag));
                modell.addLessOrEqual(LinearExpr.sum(termineAmTag), MAX_TERMINE_PRO_TAG);
                anzahl++;
            }
        }
        return anzahl;
    }

    // =================================================================================================
    // ZIEL (weich: Der Solver sucht unter allen gültigen Plänen den besten)
    // =================================================================================================

    /** Legt das Ziel fest: möglichst wenige leere Plätze über die ganze Woche. */
    public void zielWenigeLeerePlaetze() {
        // ---------------------------------------------------------------------------------------------
        // ZIEL | minimize(LinearExpr.weightedSum(variablen, gewichte)) | Möglichst wenige leere Plätze
        //   Fachlich:      WINF (35 Studierende) passt in beide Räume. Gültig wäre beides, aber im Hörsaal
        //                  blieben 85 Plätze leer, im Seminarraum nur 5. Das Ziel sagt dem Solver, welcher der
        //                  vielen gültigen Pläne der BESTE ist.
        //   So geht's:     Jede Variable bekommt ein Gewicht: die leeren Plätze, falls diese Zuordnung genommen
        //                  wird (Plätze des Raums minus Größe der Gruppe). Die gewichtete Summe zählt also die
        //                  leeren Plätze der ganzen Woche, und minimize sucht den kleinsten Wert.
        //   Hart/weich:    Die Regeln 1 bis 8 MÜSSEN gelten, das Ziel ist nur ein Wunsch. Ein Modell hat
        //                  höchstens EIN Ziel (minimize oder maximize). Mehrere Wünsche addiert man gewichtet
        //                  zu einer Summe.
        //   Geeignet für:  Kosten, Abstände, Vorlieben ("Prof. Müller lieber vormittags").
        //   Achtung:       Ohne Ziel nimmt der Solver den ersten gültigen Plan, den er findet, und meldet trotzdem
        //                  OPTIMAL. Ob WINF dann im Seminarraum sitzt, ist Zufall: Es hängt z. B. von der
        //                  Reihenfolge der Räume ab. Bei zu kleinen Räumen wird das Gewicht negativ; das stört
        //                  nicht, weil Regel 3 diese Zuordnungen verbietet.
        // ---------------------------------------------------------------------------------------------
        BoolVar[] variablen = new BoolVar[zuordnungen.size()];
        long[] leerePlaetze = new long[zuordnungen.size()];
        for (int i = 0; i < zuordnungen.size(); i++) {
            Zuordnung zuordnung = zuordnungen.get(i);
            variablen[i] = zuordnung.findetStatt();
            leerePlaetze[i] = zuordnung.raum().plaetze() - zuordnung.modul().gruppe().groesse();
        }
        modell.minimize(LinearExpr.weightedSum(variablen, leerePlaetze));
    }

    // =================================================================================================
    // Hilfsmethode
    // =================================================================================================

    /**
     * Sucht die Variablen aller Zuordnungen heraus, die die Bedingung erfüllen, zum Beispiel
     * {@code variablen(z -> z.raum().equals(hoersaal) && z.zeitslot().equals(montagFrueh))}.
     *
     * @param bedingung welche Zuordnungen dazugehören
     * @return die passenden Variablen als Array, so wie {@code addAtMostOne} und {@code LinearExpr.sum} es erwarten
     */
    private BoolVar[] variablen(Predicate<Zuordnung> bedingung) {
        return zuordnungen.stream()
                .filter(bedingung)
                .map(Zuordnung::findetStatt)
                .toArray(BoolVar[]::new);
    }
}
