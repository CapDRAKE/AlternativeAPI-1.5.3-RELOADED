# 1. Installation

## Prérequis

| Outil | Version | Pourquoi |
|---|---|---|
| **JDK 8 avec JavaFX** | 1.8.x | La lib et votre launcher sont compilés en Java 8 et utilisent JavaFX |
| Maven | 3.6 ou plus récent | Construire la lib et votre projet |
| Git | — | Récupérer les sources |

### Quel JDK 8 ?

JavaFX ne fait plus partie du JDK depuis Java 11, et la plupart des JDK 8 actuels ne
l'incluent pas non plus. Il vous faut une distribution **Java 8 qui embarque JavaFX**
(le fichier `jre/lib/ext/jfxrt.jar` doit exister dans le JDK) :

- Oracle JDK 8u202 (la version utilisée pour développer la lib ; les mises à jour
  suivantes d'Oracle sont sous une autre licence et les plus récentes n'incluent plus
  JavaFX) ;
- Azul Zulu 8, variante **« JDK FX »** ;
- BellSoft Liberica 8, variante **« Full JDK »**.

Pour vérifier :

```bash
ls "$JAVA_HOME/jre/lib/ext/jfxrt.jar"
```

> **Ce Java ne sert qu'au launcher.** Minecraft, lui, est lancé avec le Java que la
> version réclame (Java 21 pour 1.21, Java 17 pour 1.18…), que la lib télécharge
> automatiquement depuis les serveurs de Mojang. Voir
> [Mise à jour et lancement](04-mise-a-jour-et-lancement.md#le-java-du-jeu).

## Construire et installer la bibliothèque

La lib n'est pas publiée sur un dépôt Maven public : on la construit et on l'installe
dans le dépôt Maven local (`~/.m2`).

```bash
git clone https://github.com/CapDRAKE/AlternativeAPI-1.5.3-RELOADED.git
cd AlternativeAPI-1.5.3-RELOADED
```

### Adapter le chemin de JavaFX

Le `pom.xml` déclare JavaFX comme une dépendance `system` pointant vers un chemin
absolu :

```xml
<dependency>
    <groupId>com.oracle</groupId>
    <artifactId>javafx</artifactId>
    <version>2.2</version>
    <scope>system</scope>
    <systemPath>C:/Program Files/Java/jdk1.8.0_202/jre/lib/ext/jfxrt.jar</systemPath>
</dependency>
```

**Si votre JDK n'est pas à cet emplacement, remplacez ce chemin** par celui de votre
`jfxrt.jar`, sinon le build échoue avec :

```
[ERROR] Failed to execute goal on project alternative-api: Could not resolve dependencies ...
[ERROR] dependency: com.oracle:javafx:jar:2.2 (system)
[ERROR]   Could not find artifact com.oracle:javafx:jar:2.2 at specified path ...
```

### Installer

```bash
mvn clean install
```

Le jar `fr.trxyy.alternative:alternative-api:1.7.0` est alors disponible pour tous vos
projets Maven de la machine. Maven doit lui-même tourner avec le JDK 8 (vérifiez avec
`mvn -v`, ligne « Java version »).

## Ajouter la lib à votre launcher

Dans le `pom.xml` de votre launcher :

```xml
<properties>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
    <dependency>
        <groupId>fr.trxyy.alternative</groupId>
        <artifactId>alternative-api</artifactId>
        <version>1.7.0</version>
    </dependency>
</dependencies>
```

Cette dépendance apporte aussi, de façon transitive, les librairies utilisées par la lib
(Gson 2.8.6, Guava, Apache HttpClient, Commons IO, Commons Lang, org.json,
json-simple) : vous pouvez les utiliser directement dans votre code.

Il n'y a **rien à déclarer pour JavaFX** : avec un JDK 8 FX, il est déjà sur le
classpath du compilateur et de la JVM.

### Produire un jar exécutable

Pour distribuer votre launcher sous forme d'un seul fichier, ajoutez le plugin shade
(remplacez la classe principale par la vôtre) :

```xml
<build>
    <finalName>MonLauncher</finalName>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                        <filters>
                            <filter>
                                <artifact>*:*</artifact>
                                <excludes>
                                    <exclude>META-INF/*.SF</exclude>
                                    <exclude>META-INF/*.DSA</exclude>
                                    <exclude>META-INF/*.RSA</exclude>
                                </excludes>
                            </filter>
                        </filters>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.exemple.launcher.MonLauncher</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

```bash
mvn clean package
java -jar target/MonLauncher.jar
```

> **Utilisez `mvn clean package`, pas seulement `mvn package`.** Sans `clean`, le
> plugin shade peut refusionner l'ancien jar produit et embarquer une copie périmée
> d'AlternativeAPI : après avoir modifié et réinstallé la lib, votre correctif
> semblerait alors sans effet. À défaut de `clean`, supprimez `target/*.jar` avant de
> reconstruire.

## Distribuer le launcher à vos joueurs

Le jar produit a besoin, pour s'exécuter, d'un **Java 8 avec JavaFX**. La plupart des
joueurs n'en ont pas : en pratique, on distribue le launcher accompagné d'un JRE 8 FX
(par exemple Zulu 8 « JRE FX ») et d'un script ou exécutable qui l'utilise. Minecraft,
lui, n'a besoin de rien : son Java est téléchargé par la lib.

## Et ensuite ?

Passez au [tutoriel](02-tutoriel-launcher-basique.md) pour écrire votre premier launcher.
