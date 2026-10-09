package de.dhsh.stundenplanung;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Objects;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Prüft die Modulgrenzen: Ein Modul ist das erste Package unterhalb von {@value #BASIS_PACKAGE} (z. B.
 * {@code datenerfassung}). Neue Module werden ohne Anpassung dieses Tests erfasst.
 */
@AnalyzeClasses( packages = ModulgrenzenTest.BASIS_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class )
class ModulgrenzenTest {

	static final String BASIS_PACKAGE = "de.dhsh.stundenplanung";

	@ArchTest
	static final ArchRule internalNurImEigenenModul = classes( )
			.that( ).resideInAPackage( BASIS_PACKAGE + ".*.internal.." )
			.should( nurAusDemEigenenModulVerwendetWerden( ) )
			// Erlaubt leere Module, solange noch keine Klassen existieren
			.allowEmptyShould( true );

	@ArchTest
	static final ArchRule apiVerweistNichtAufInternal = noClasses( )
			.that( ).resideInAPackage( BASIS_PACKAGE + ".*.api.." )
			.should( ).dependOnClassesThat( ).resideInAPackage( BASIS_PACKAGE + ".*.internal.." )
			.because( "die api eines Moduls keine internen Typen nach außen geben darf" )
			.allowEmptyShould( true );

	private static ArchCondition<JavaClass> nurAusDemEigenenModulVerwendetWerden( ) {
		return new ArchCondition<>( "nur aus dem eigenen Modul verwendet werden" ) {
			@Override
			public void check( JavaClass klasse, ConditionEvents events ) {
				String modul = modulVon( klasse );
				for ( Dependency abhaengigkeit : klasse.getDirectDependenciesToSelf( ) ) {
					if ( !Objects.equals( modul, modulVon( abhaengigkeit.getOriginClass( ) ) ) ) {
						events.add( SimpleConditionEvent.violated( abhaengigkeit, abhaengigkeit.getDescription( ) ) );
					}
				}
			}
		};
	}

	/** Liefert den Modulnamen, bzw. {@code null} für Klassen direkt im Basis-Package oder außerhalb. */
	private static String modulVon( JavaClass klasse ) {
		String praefix = BASIS_PACKAGE + ".";
		String packageName = klasse.getPackageName( );
		if ( !packageName.startsWith( praefix ) ) {
			return null;
		}
		String rest = packageName.substring( praefix.length( ) );
		int punkt = rest.indexOf( '.' );
		return punkt < 0 ? rest : rest.substring( 0, punkt );
	}
}
