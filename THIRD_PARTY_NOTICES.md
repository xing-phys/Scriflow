# Third-party notices

Scriflow's own source, documentation, and original artwork use the
[MIT License](LICENSE). The third-party files below retain their upstream
licenses and notices.

## Gradle Wrapper

The repository includes the Gradle Wrapper for its development build tooling:

- `gradle/wrapper/gradle-wrapper.jar`
- `gradlew`
- `gradlew.bat`

Gradle's build-tool source is licensed under **Apache License 2.0**. The wrapper
JAR includes that license at `META-INF/LICENSE`; an exact, unmodified copy is
provided as [licenses/gradle-wrapper-Apache-2.0.txt](licenses/gradle-wrapper-Apache-2.0.txt).
The launcher scripts retain their original Apache license headers and notices:
`gradlew` identifies copyright 2015–2021, the original authors; `gradlew.bat`
identifies copyright 2015, the original author or authors.
See [Gradle's license information](https://docs.gradle.org/current/userguide/licenses.html).

The checked-in JAR's SHA-256 is
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`,
matching the wrapper checksum published for the configured Gradle **8.11.1**
version. See [Gradle's checksum reference](https://gradle.org/release-checksums/).

Inspection of this JAR found Gradle wrapper, command-line parsing, and supporting
`org.gradle` classes, plus `META-INF/MANIFEST.MF` and `META-INF/LICENSE`. It has
no `NOTICE` entry or other library namespace. It is a small bootstrap tool,
rather than the full Gradle distribution; the wrapper and launch scripts are
not packaged into the Scriflow APK.

For redistribution, Apache License 2.0 section 4 requires a license copy,
preservation of applicable source notices, notices identifying modified files,
and preservation of applicable attribution from a supplied `NOTICE` file when
its conditions apply. Retain the embedded license, accompanying license copy,
and launcher headers when distributing these files. The complete terms are in
the license copy and [Apache's official license](https://www.apache.org/licenses/LICENSE-2.0).

## PDF import and export runtime

Scriflow includes [PDFBox Android 2.0.27.0](https://github.com/TomRoush/PdfBox-Android/tree/v2.0.27.0),
the Android port of Apache PDFBox, under Apache License 2.0. Its unmodified
[LICENSE](licenses/pdfbox-android-LICENSE.txt) and
[NOTICE](licenses/pdfbox-android-NOTICE.txt) are retained and packaged in the APK
assets. The license file also contains the upstream notices for included code
and bundled font resources. The app uses Android's native PDF renderer for
display and PDFBox for reading navigation metadata and appending vector ink to
the original PDF object graph.

PDFBox's runtime dependencies, Bouncy Castle **1.72** (`bcprov-jdk15to18`,
`bcpkix-jdk15to18`, and `bcutil-jdk15to18`), use the permissive
[Bouncy Castle license](licenses/bouncycastle-LICENSE.html), an MIT-style license.
The exact upstream license is retained and included in APK assets. See the
[upstream source license](https://github.com/bcgit/bc-java/blob/r1rv72/LICENSE.html).

Gradle resolves these app dependencies from Maven Central. The alternate
builder uses [scripts/bootstrap-pdfbox.sh](scripts/bootstrap-pdfbox.sh), which
pins every runtime archive by SHA-256 before compilation and packaging. The
archives are downloaded to ignored `.tools/pdfbox/`; they are packaged in the
APK but are not checked into this repository. Gradle's dependency versions and
the alternate builder's versions must remain synchronized.

## Downloaded development tools

The full Gradle distribution, Android SDK/build tools, JDK, plugin dependencies,
and their caches are downloaded separately into ignored local directories.
Those build tools are not checked into this repository or included in the app
by the build scripts. Their accompanying licenses and notices remain with those tools.

In particular, the full Gradle 8.11.1 distribution carries a `NOTICE` describing
included Apache Software Foundation software and other components including
Groovy, SLF4J, JUnit, JCIFS, and HttpClient. Those components are absent from the
checked-in wrapper JAR. Do not describe the wrapper's license inventory as the
inventory for a redistributed full toolchain; preserve and review the toolchain's
own license/notice files if distributing it separately.
