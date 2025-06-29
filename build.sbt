import sbt.project
import ModuleMdocPlugin.autoImport.mdocScalacOptions
import sbtcrossproject.{CrossProject, CrossType, Platform}

lazy val prjName = "erules"
lazy val prjPackageName = prjName.replaceAll("[^\\p{Alpha}\\d]+", ".")
lazy val prjDescription = "A rules engine evaluator"
lazy val prjOrg = "com.github.geirolz"
lazy val scala2 = "2.13.16"
lazy val scala3 = "3.6.4"
lazy val supportedScalaVersions = List(scala2, scala3)

//## global project to no publish ##
lazy val erules: Project = project
  .in(file("."))
  .settings(baseSettings)
  .settings(noPublishSettings)
  .settings(
    inThisBuild(
      List(
        homepage := Some(url(s"https://github.com/geirolz/$prjName")),
        licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
        developers := List(
          Developer(
            "DavidGeirola",
            "David Geirola",
            "david.geirola@gmail.com",
            url("https://github.com/geirolz")
          )
        )
      )
    )
  )
  .settings(
    crossScalaVersions := Nil
  )
  .aggregate(
    core.js,
    core.jvm,
    generic.js,
    generic.jvm,
    circe.js,
    circe.jvm,
    `cats-xml`.jvm,
    scalatest.js,
    scalatest.jvm
  )

lazy val core: CrossProject =
  buildModule(
    prjModuleName = "core",
    toPublish = true,
    folder = ".",
    platforms = JSPlatform,
    JVMPlatform
  ).settings(
    mdocOut := file("."),
    libraryDependencies ++= ProjectDependencies.Core.dedicated
  )

lazy val generic: CrossProject =
  buildModule(
    prjModuleName = "generic",
    toPublish = true,
    folder = "modules",
    platforms = JSPlatform,
    JVMPlatform
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Generic.dedicated
    )
    .settings(
      scalacOptions ++= macroSettings(scalaVersion.value)
    )

lazy val circe: CrossProject =
  buildModule(
    prjModuleName = "circe",
    toPublish = true,
    folder = "modules",
    platforms = JSPlatform,
    JVMPlatform
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Circe.dedicated.value
    )

lazy val `cats-xml`: CrossProject =
  buildModule(
    prjModuleName = "cats-xml",
    toPublish = true,
    folder = "modules",
    platforms = JVMPlatform
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.CatsXml.dedicated.value
    )

lazy val scalatest: CrossProject =
  buildModule(
    prjModuleName = "scalatest",
    toPublish = true,
    folder = "modules",
    platforms = JSPlatform,
    JVMPlatform
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Scalatest.dedicated.value
    )

//=============================== MODULES UTILS ===============================
def buildModule(
                 prjModuleName: String,
                 toPublish: Boolean,
                 folder: String,
                 platforms: Platform*
               ): CrossProject = {
  val keys = prjModuleName.split("-")
  val docName = keys.mkString(" ")
  val prjFile = file(s"$folder/$prjModuleName")
  val docNameStr = s"$prjName $docName"
  CrossProject(s"$prjName-$prjModuleName", prjFile)(platforms: _*)
    .crossType(CrossType.Pure)
    .settings(baseSettings)
    .settings(
      description := moduleName.value,
      moduleName := s"$prjName-$prjModuleName",
      name := s"$prjName $docName",
      publish / skip := !toPublish,
      mdocIn := prjFile / "docs",
      mdocOut := prjFile,
      mdocScalacOptions := Seq("-Xsource:3"),
      mdocVariables := Map(
        "ORG" -> prjOrg,
        "PRJ_NAME" -> prjName,
        "DOCS_TITLE" -> docNameStr.split(" ").map(_.capitalize).mkString(" "),
        "MODULE_NAME" -> moduleName.value,
        "VERSION" -> previousStableVersion.value.getOrElse("<version>")
      )
    )
    .enablePlugins(ModuleMdocPlugin)
}

//=============================== SETTINGS ===============================
lazy val noPublishSettings: Seq[Def.Setting[_]] = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publish / skip := true
)

lazy val baseSettings: Seq[Def.Setting[_]] = Seq(
  // project
  name := prjName,
  description := prjDescription,
  organization := prjOrg,
  // scala
  crossScalaVersions := supportedScalaVersions,
  scalaVersion := supportedScalaVersions.head,
  scalacOptions ++= scalacSettings(scalaVersion.value),
  versionScheme := Some("early-semver"),
  // dependencies
  resolvers ++= ProjectResolvers.all,
  libraryDependencies ++= ProjectDependencies.common.value ++ {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => ProjectDependencies.Plugins.compilerPluginsFor2_13
      case Some((3, _)) => ProjectDependencies.Plugins.compilerPluginsFor3
      case _ => Nil
    }
  },
  // fmt
  scalafmtOnCompile := true
)

def scalacSettings(scalaVersion: String): Seq[String] =
  Seq(
    //    "-Xlog-implicits",
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions" // Allow definition of implicit functions called views
  ) ++ {
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        Seq(
          "-Xkind-projector",
          "-explain-types", // Explain type errors in more detail.
          "-Xfatal-warnings" // Fail the compilation if there are any warnings.
        )
      case Some((2, 13)) =>
        Seq(
          "-explaintypes", // Explain type errors in more detail.
          "-unchecked", // Enable additional warnings where generated code depends on assumptions.
          "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
          "-Xfatal-warnings", // Fail the compilation if there are any warnings.
          "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
          "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
          "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
          "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
          "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
          "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
          "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
          "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
          "-Xlint:option-implicit", // Option.apply used implicit view.
          "-Xlint:package-object-classes", // Class or object defined in package object.
          "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
          "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
          "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
          "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
          "-Ywarn-dead-code", // Warn when dead code is identified.
          "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
          "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
          "-Ywarn-numeric-widen", // Warn when numerics are widened.
          "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
          "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
          "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
          "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
          "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
          "-Ywarn-unused:locals", // Warn if a local definition is unused.
          "-Ywarn-unused:explicits", // Warn if a explicit value parameter is unused.
          "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
          "-Ywarn-unused:privates", // Warn if a private member is unused.
          "-Xsource:3",
          "-P:kind-projector:underscore-placeholders"
        )
      case _ => Nil
    }
  }

def macroSettings(scalaVersion: String): Seq[String] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _)) =>
      Seq(
        "-Xcheck-macros" // Fail the compilation if there are any warnings.
      )
    case Some((2, 13)) =>
      Seq(
        "-language:experimental.macros", // Allow macro definition (besides implementation and application)
        "-Ywarn-macros:after" // Tells the compiler to make the unused checks after macro expansion
      )
    case _ => Nil
  }

//=============================== ALIASES ===============================
addCommandAlias("check", ";clean;test")
addCommandAlias(
  "gen-doc",
  List(
    core,
    generic,
    circe,
    `cats-xml`,
    scalatest
  ).map(prj => s"project ${prj.jvm.id}-docs; mdoc").mkString(";") + s";project $prjName;"
)
