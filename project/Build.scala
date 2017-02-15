import sbt._
import Keys._

object Build extends Build with Version {
  val specs2_core       = "org.specs2"      %%  "specs2-core"         % "3.8.8"
  val specs2_scalacheck = "org.specs2"      %%  "specs2-scalacheck"   % "3.8.8"
  val scalacheck        = "org.scalacheck"  %%  "scalacheck"          % "1.13.4"

  implicit class ProjectOps(self: Project) {
    def standard: Project = {
      self
          .settings(organization := "com.github.dmrolfs")
          .settings( scalaVersion := "2.12.1" )
          .settings(resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases")
          .settings(scalacOptions := Seq("-feature", "-deprecation", "-unchecked", "-Xlint", "-Yrangepos", "-encoding", "utf8"))
          .settings(scalacOptions in Test ++= Seq("-Yrangepos"))
    }

    def notPublished: Project = {
      self
          .settings(publish := {})
          .settings(publishArtifact := false)
    }

    def published: Project = {
      self.settings( publishSettings:_* )
          // .settings(publishTo := Some("Scalap Releases" at "s3://dl.john-ky.io/maven/releases"))
          // .settings(isSnapshot := true)
    }

    def libs(modules: ModuleID*) = self.settings(libraryDependencies ++= modules)

    def testLibs(modules: ModuleID*) = self.libs(modules.map(_ % "test"): _*)
  }

  lazy val `hashids-scala` = Project(id = "hashids-scala", base = file("hashids-scala"))
      .standard
      .published
      .testLibs(scalacheck, specs2_core, specs2_scalacheck)

  lazy val root = Project(id = "all", base = file("."))
      .notPublished
      .aggregate(`hashids-scala`)


  def publishSettings = {
    // if ( (version in ThisBuild).toString.endsWith("-SNAPSHOT") ) {
    if ( (buildVersion).toString.endsWith("-SNAPSHOT") ) {
      Seq(
        publishTo := Some("Artifactory Realm" at "http://oss.jfrog.org/artifactory/oss-snapshot-local"),
        publishMavenStyle := true,
        // Only setting the credentials file if it exists (#52)
        credentials := List(Path.userHome / ".bintray" / ".artifactory").filter(_.exists).map(Credentials(_))
      )
    } else {
      Seq(
        pomExtra := <scm>
          <url>https://github.com</url>
          <connection>https://github.com/dmrolfs/hashids-scala.git</connection>
        </scm>
        <developers>
          <developer>
            <id>newhoggy</id>
            <name>John Ky</name>
            <url>https://github.com/newhoggy</url>
          </developer>
<!--           <developer>
            <id>dmrolfs</id>
            <name>Damon Rolfs</name>
            <url>http://dmrolfs.github.io/</url>
          </developer>
 -->        </developers>,
        publishMavenStyle := true,
        resolvers += Resolver.url("omen bintray resolver", url("http://dl.bintray.com/omen/maven"))(Resolver.ivyStylePatterns),
        licenses := ("MIT", url("http://opensource.org/licenses/MIT")) :: Nil // this is required! otherwise Bintray will reject the code
      )
    }
  }
}
