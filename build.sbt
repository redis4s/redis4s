name := "redis4s"
organization := "io.github.redis4s"
scalaVersion := "2.12.10"
crossScalaVersions := List("2.12.10", "2.13.1")

scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")

fork in run := true
Test / fork := true
parallelExecution in Test := false

libraryDependencies ++= {
  Seq(
    "org.log4s"         %% "log4s"                        % "1.8.2",
    "io.chrisdavenport" %% "log4cats-core"                % "1.0.1",
    "org.tpolecat"      %% "natchez-core"                 % "0.0.10",
    "co.fs2"            %% "fs2-io"                       % "2.0.0",
    "co.fs2"            %% "fs2-core"                     % "2.0.0",
    "org.typelevel"     %% "cats-free"                    % "2.0.0",
    "org.scodec"        %% "scodec-core"                  % "1.11.4",
    "io.chrisdavenport" %% "keypool"                      % "0.2.0",
    "com.codecommit"    %% "cats-effect-testing-minitest" % "0.3.0" % "test",
    "io.chrisdavenport" %% "log4cats-slf4j"               % "1.0.1" % "test",
    "io.monix"          %% "minitest"                     % "2.7.0" % "test",
    "ch.qos.logback"    % "logback-classic"               % "1.2.3" % "test"
  )
}

addCompilerPlugin("org.typelevel"    %% "kind-projector"     % "0.10.3" cross CrossVersion.binary)
addCompilerPlugin("com.olegpy"       %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("com.github.cb372" % "scala-typed-holes"   % "0.1.1" cross CrossVersion.full)

scalafmtOnCompile := true
cancelable in Global := true

// wartremoverErrors in (Compile, compile) ++= Warts.unsafe
// wartremoverErrors ++= Warts.all
wartremoverErrors := Nil

testFrameworks += new TestFramework("minitest.runner.Framework")

version ~= (_.replace('+', '-'))
dynver ~= (_.replace('+', '-'))
