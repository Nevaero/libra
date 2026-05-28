# Libra — Persistence Processor : guide d'extension

> Comment ajouter un nouveau type complexe (VO) au persistence-processor.
>
> Le processor utilise désormais une annotation déclarative **`@PersistAs`** : ajouter un VO = **annoter le record / sealed interface**. Pas de fichier handler à écrire, pas de modification du processor (`TypeAnalyzer`, `CodeGenerator`, `ComponentMapping`).

---

## Quand annoter avec `@PersistAs` ?

Tu dois annoter un type quand il s'agit d'un **VO complexe** (record ou sealed interface) qui apparaît comme composant d'une `@PersistenceEntity` et qui doit être **flatten** en plusieurs colonnes (ou réduit à une FK).

Exemples typiques :
- `Money(long minorUnits, Asset asset)` → 3 colonnes
- `Address(String street, String city, String country)` → 3 colonnes
- `GeoPoint(double lat, double lon)` → 2 colonnes
- `Asset` (sealed `Currency | Security`) → 2 colonnes (type + code)
- `Currency` (réutilisé comme FK ailleurs) → 1 colonne `<name>Code`

Tu **n'as pas besoin** de `@PersistAs` si :
- Le type est déjà simple (primitif / wrapper / String / UUID / Instant / LocalDate / BigDecimal / enum) — supporté nativement par `SimpleMapping`
- Le type est `Optional<X>` où X est simple — `OptionalMapping`
- Le type est `List<X>` où X est annoté `@PersistenceEntity` — `ListMapping`

---

## Procédure (1 étape)

**Annote ton VO** avec `@PersistAs(strategy = ...)`. Build. C'est tout.

```java
@PersistAs(strategy = PersistAs.Strategy.SPREAD)
public record Money(long minorUnits, Asset asset) { }
```

À la compilation, `PersistAsProcessor` :
1. Lit l'annotation, en déduit les metadata (colonnes, expressions, besoin de resolver) ;
2. Publie un `SyntheticHandler` dans `InProcessHandlerRegistry` pour que `TypeAnalyzer` le voie **dans le même round** ;
3. Génère un fichier `<Type>Handler.java` annoté `@AutoService(ComplexTypeHandler.class)` à côté du VO — découvert ensuite via ServiceLoader pour les builds suivants.

Aucune ligne du processor n'a besoin d'être touchée.

---

## Les trois stratégies

| Strategy | Cible | Colonnes produites | Exemple |
|---|---|---|---|
| `SPREAD` | record | 1 colonne par composant (récursif si nested est aussi `@PersistAs`) | `Money` → 3 colonnes |
| `REFERENCE` | record déjà persisté ailleurs (entity table) | 1 colonne FK contenant l'idField | `Currency` → 1 colonne `<name>Code` |
| `VARIANT` | sealed interface (polymorphic discriminator) | 2 colonnes : type discriminant + id | `Asset` → `<name>Type` + `<name>Code` |

### Paramètres de l'annotation

| Param | Stratégie | Rôle | Default |
|---|---|---|---|
| `idField` | `REFERENCE` | Nom du record component qui sert d'id | `"id"` |
| `typeSuffix` | `VARIANT` | Suffixe de la colonne discriminator (e.g. `"Type"` → `assetType`) | `"Type"` |
| `idSuffix` | `VARIANT` | Suffixe de la colonne id (e.g. `"Code"` pour Asset, `"Id"` pour Instrument) | `"Id"` |
| `idType` | `VARIANT` | FQN Java de la colonne id (`"java.lang.String"`, `"java.util.UUID"`, ...) | `"java.lang.String"` |

---

## Exemples concrets (état actuel du domaine)

### `SPREAD` — record décomposé en plusieurs colonnes

```java
// io.libra.core.entities.Money
@PersistAs(strategy = PersistAs.Strategy.SPREAD)
public record Money(long minorUnits, Asset asset) { }
```

Le composant `Money` d'une `@PersistenceEntity` produit 3 colonnes :
- `<name>MinorUnits` (long)
- `<name>AssetType` (String) — discriminator hérité d'`Asset` (VARIANT)
- `<name>AssetCode` (String) — id hérité d'`Asset` (VARIANT)

La cascade se fait automatiquement : `Money.asset` est lui-même `@PersistAs(VARIANT)`, donc `SPREAD` délègue récursivement au registry.

### `REFERENCE` — VO qui est en fait une FK

```java
// io.libra.core.entities.Currency
@PersistenceEntity(table = "currencies", idField = "code")
@PersistAs(strategy = PersistAs.Strategy.REFERENCE, idField = "code")
public record Currency(String code, String name, int decimals) implements Asset { }
```

Quand `Currency` apparaît comme composant ailleurs, 1 seule colonne `<name>Code` est produite, et le mapper appelle `resolver.resolveCurrency(row.<name>Code())` à la reconstruction.

### `VARIANT` — sealed avec discriminator

```java
// io.libra.core.entities.Asset
@PersistAs(strategy = PersistAs.Strategy.VARIANT,
           typeSuffix = "Type", idSuffix = "Code", idType = "java.lang.String")
public sealed interface Asset permits Currency, Security { ... }

// io.libra.core.entities.Instrument — même pattern, id en UUID
@PersistAs(strategy = PersistAs.Strategy.VARIANT,
           typeSuffix = "Type", idSuffix = "Id", idType = "java.util.UUID")
public sealed interface Instrument permits Security, CurrencyPair { ... }
```

Le mapper généré appelle `resolver.resolveAsset(row.<name>Type(), row.<name>Code())` côté toDomain, et `resolver.assetTypeOf(domain.<name>())` / `resolver.assetCodeOf(domain.<name>())` côté toRow.

---

## Dual annotation : `@PersistAs` ET `@PersistenceEntity` sur la même classe

Un VO peut être **à la fois une entité persistée et une référence FK utilisée ailleurs**. Exemple : `Currency` a sa propre table `currencies` (donc `@PersistenceEntity`) et est aussi référencé comme FK depuis d'autres entités (donc `@PersistAs(REFERENCE)`).

```java
@PersistenceEntity(table = "currencies", idField = "code")
@PersistAs(strategy = PersistAs.Strategy.REFERENCE, idField = "code")
public record Currency(String code, String name, int decimals) implements Asset { }
```

Les deux processors coopèrent :
- `PersistenceEntityProcessor` génère `CurrencyRow` + `CurrencyMapper` (table dédiée) ;
- `PersistAsProcessor` génère `CurrencyHandler` (FK 1 colonne réutilisable partout) ;
- `TypeAnalyzer` voit le handler via `InProcessHandlerRegistry` dans le même round et l'utilise quand il rencontre un composant `Currency` dans une autre `@PersistenceEntity`.

---

## Côté `PersistenceResolver`

Si tu introduis un VO `VARIANT` ou `REFERENCE`, tu dois exposer dans `PersistenceResolver` :

- `REFERENCE` → `resolve<Simple>(idType id)` (e.g. `Currency resolveCurrency(String code)`)
- `VARIANT` → `resolve<Simple>(String type, <idType> id)` (e.g. `Asset resolveAsset(String type, String code)`)  
  ET les inverses : `<simpleLower>TypeOf(<Simple> v)` + `<simpleLower><CapIdSuffix>Of(<Simple> v)`

Implémente-les dans `PersistenceResolverImpl` :

```java
// /root/dev/libra/src/main/java/io/libra/persistence/PersistenceResolverImpl.java
@Override
public Currency resolveCurrency(String code) {
    return currencyRepository.findById(code)
            .map(currencyMapper::toDomain)
            .orElseThrow(() -> new NoSuchElementException("Currency not found: " + code));
}

@Override
public String assetTypeOf(Asset a) {
    return switch (a) {
        case Currency c -> "CURRENCY";
        case Security s -> "SECURITY";
    };
}

@Override
public String assetCodeOf(Asset a) { return a.code(); }
```

---

## Cas avancé (rare) : handler manuel

Si une des 3 stratégies ne couvre pas ton cas — par exemple un VO qui a besoin d'une transformation custom non-standard (chiffrement de colonne, hash, encoding non trivial) — tu peux toujours écrire un `ComplexTypeHandler` manuel :

```java
@AutoService(ComplexTypeHandler.class)
public class FooHandler implements ComplexTypeHandler {
    @Override public String targetFqn() { return "io.libra.core.entities.Foo"; }
    @Override public List<ColumnSpec> columns(String name) { /* ... */ }
    @Override public String toDomainExpr(String name, String rowVar, String resolverVar) { /* ... */ }
    @Override public List<String> toRowArgs(String name, String domainVar, String resolverVar) { /* ... */ }
    @Override public boolean needsResolver() { return false; }
}
```

`@AutoService(ComplexTypeHandler.class)` génère le `META-INF/services/io.libra.persistence.processor.ComplexTypeHandler`. `TypeAnalyzer` le découvre via ServiceLoader.

**Cas exotique** : à n'utiliser que si `SPREAD` / `REFERENCE` / `VARIANT` est insuffisant. Pour 99% des VO domaine, `@PersistAs` suffit.

---

## Anti-patterns à éviter

| Anti-pattern | Pourquoi c'est mauvais | Bonne pratique |
|---|---|---|
| Modifier `TypeAnalyzer` ou `CodeGenerator` pour hard-coder un FQN | Casse l'OCP du processor. Le registry est auto-découvert. | Annoter le VO avec `@PersistAs`. |
| Oublier d'annoter le VO | Le processor lèvera `UnsupportedTypeException` ("type X not supported") à la compilation de toute `@PersistenceEntity` qui le référence. | Ajouter `@PersistAs(strategy = ...)` sur le record / sealed. |
| Annoter `SPREAD` sur une sealed interface | `SPREAD` exige des record components ; le processor lève une erreur de compilation. | Utiliser `VARIANT` pour les sealed. |
| Annoter `REFERENCE` avec un `idField` qui n'existe pas | Le processor lève "idField=... not found among record components". | Vérifier le nom exact du composant du record. |
| Écrire un handler manuel quand une stratégie suffit | Code dupliqué, divergent à maintenir. | Préférer `@PersistAs` ; handler manuel uniquement pour cas vraiment exotiques. |

---

## Vérification / tests

Après build, vérifie :

```bash
./gradlew :persistence-processor:build
./gradlew compileJava
```

1. Le handler est généré dans `build/generated/sources/annotationProcessor/java/main/<package>/<Type>Handler.java`.
2. Le Row généré pour toute `@PersistenceEntity` utilisant le VO comporte les bonnes colonnes (e.g. `MoneyRow` exhibe `amountMinorUnits`, `amountAssetType`, `amountAssetCode`).
3. Le Mapper généré injecte `PersistenceResolver` si la stratégie l'exige (`REFERENCE`, `VARIANT`, ou `SPREAD` cascadant).

### Test compile-testing

Ajoute un test dans `/root/dev/libra/persistence-processor/src/test/java/io/libra/persistence/processor/PersistAsProcessorTest.java` :

```java
@Test
void generatesHandlerForSpreadVo() {
    JavaFileObject source = JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "import io.libra.persistence.PersistAs;",
            "import io.libra.persistence.PersistAs.Strategy;",
            "",
            "@PersistAs(strategy = Strategy.SPREAD)",
            "public record Foo(long bar, String baz) { }"
    );

    Compilation result = compileWithStubs(source);

    assertThat(result).succeededWithoutWarnings();
    assertThat(result).generatedSourceFile("test.FooHandler")
            .contentsAsUtf8String()
            .contains("targetFqn")
            .contains("\"test.Foo\"");
}
```

Pour tester l'**intégration** d'un VO dans une `@PersistenceEntity`, voir `PersistenceEntityProcessorTest`.

---

## TL;DR

**Pour ajouter un nouveau VO : 1 annotation `@PersistAs` sur le record / sealed. Build. C'est fini.**

| Cas | Annotation |
|---|---|
| Record décomposé en N colonnes | `@PersistAs(strategy = SPREAD)` |
| Record déjà en table, réutilisé en FK | `@PersistAs(strategy = REFERENCE, idField = "code")` |
| Sealed interface polymorphique | `@PersistAs(strategy = VARIANT, typeSuffix = "Type", idSuffix = "Code", idType = "java.lang.String")` |

Le processor s'occupe du reste : génération du handler, publication dans le registry, intégration au pipeline `TypeAnalyzer` → `CodeGenerator` → Row + Mapper.
