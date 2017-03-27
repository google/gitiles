workspace(name = "gitiles")

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "a8a04c9ef2f131816c1e458613bee93b461ba652",
    # local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
    "GERRIT",
)

maven_jar(
    name = "commons_lang",
    artifact = "org.apache.commons:commons-lang3:3.1",
    sha1 = "905075e6c80f206bbe6cf1e809d2caa69f420c76",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.0",
    sha1 = "c4ba5371a29ac9b2ad6129b1d39ea38750043eff",
)

maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:21.0",
    sha1 = "3a3d111be1be1b745edfa7d91678a12d7ed38709",
)

maven_jar(
    name = "joda_time",
    artifact = "joda-time:joda-time:2.9.8",
    sha1 = "03986e1763e5df02ad7fc040ecb555193a8436bb",
)

maven_jar(
    name = "jsr305",
    artifact = "com.google.code.findbugs:jsr305:3.0.0",
    attach_source = False,
    sha1 = "5871fb60dc68d67da54a663c3fd636a10a532948",
)

maven_jar(
    name = "prettify",
    artifact = "prettify:java-prettify:1.2.1",
    repository = GERRIT,
    sha1 = "29ad8d072f9d0b83d1a2e9aa6ccb0905e6d543c6",
)

COMMONMARK_VERSION = "0.6.0"

# When upgrading commonmark it should also be updated in plugins/gitiles
maven_jar(
    name = "commonmark",
    artifact = "com.atlassian.commonmark:commonmark:" + COMMONMARK_VERSION,
    sha1 = "5df3f6fa3073966620685924aa22d08ece7213f2",
)

maven_jar(
    name = "cm_autolink",
    artifact = "com.atlassian.commonmark:commonmark-ext-autolink:" + COMMONMARK_VERSION,
    sha1 = "4d7e828a4651e2f590b4a059925991be58e62da6",
)

maven_jar(
    name = "autolink",
    artifact = "org.nibor.autolink:autolink:0.5.0",
    sha1 = "dab74ea929a5fb4c99189af18c9528faf8d5340b",
)

maven_jar(
    name = "gfm_strikethrough",
    artifact = "com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:" + COMMONMARK_VERSION,
    sha1 = "75a95aaec77810496de41239bcc773adfb13285f",
)

maven_jar(
    name = "gfm_tables",
    artifact = "com.atlassian.commonmark:commonmark-ext-gfm-tables:" + COMMONMARK_VERSION,
    sha1 = "ae1c701517e8116bc205b561b9b215a53df8abc7",
)

maven_jar(
    name = "servlet_api_2_5",
    artifact = "org.eclipse.jetty.orbit:javax.servlet:2.5.0.v201103041518",
    sha1 = "9c16011c06bc6fe5e9dba080fcb40ddb4b75dc85",
)

maven_jar(
    name = "servlet_api_3_0",
    artifact = "org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016",
    sha1 = "0aaaa85845fb5c59da00193f06b8e5278d8bf3f8",
)

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:0.32",
    sha1 = "e996fb4b41dad04365112786796c945f909cfdf7",
)

maven_jar(
    name = "soy",
    artifact = "com.google.template:soy:2017-02-01",
    sha1 = "8638940b207779fe3b75e55b6e65abbefb6af678",
)

maven_jar(
    name = "html_types",
    artifact = "com.google.common.html.types:types:1.0.4",
    sha1 = "2adf4c8bfccc0ff7346f9186ac5aa57d829ad065",
)

maven_jar(
    name = "protobuf",
    artifact = "com.google.protobuf:protobuf-java:3.0.0-beta-2",
    sha1 = "de80fe047052445869b96f6def6baca7182c95af",
)

maven_jar(
    name = "icu4j",
    artifact = "com.ibm.icu:icu4j:57.1",
    sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
)

JGIT_VERS = "4.6.0.201612231935-r.30-gd3148f300"

maven_jar(
    name = "jgit",
    artifact = "org.eclipse.jgit:org.eclipse.jgit:" + JGIT_VERS,
    repository = GERRIT,
    sha1 = "a2b5970b853f8fee64589fc1103c0ceb7677ba63",
)

maven_jar(
    name = "jgit_servlet",
    artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + JGIT_VERS,
    repository = GERRIT,
    sha1 = "d3aa54bd610db9a5c246aa8fef13989982c98628",
)

maven_jar(
    name = "jgit_junit",
    artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + JGIT_VERS,
    repository = GERRIT,
    sha1 = "6c2b2f192c95d25a2e1576aee5d1169dd8bd2266",
)

maven_jar(
    name = "jgit_archive_library",
    artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + JGIT_VERS,
    repository = GERRIT,
    sha1 = "a728cf277396f1227c5a8dffcf5dee0188fc0821",
)

maven_jar(
    name = "ewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "commons_compress",
    artifact = "org.apache.commons:commons-compress:1.13",
    sha1 = "15c5e9584200122924e50203ae210b57616b75ee",
)

maven_jar(
    name = "tukaani_xz",
    artifact = "org.tukaani:xz:1.4",
    attach_source = False,
    sha1 = "18a9a2ce6abf32ea1b5fd31dae5210ad93f4e5e3",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.11",
    sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
)

maven_jar(
    name = "hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

SL_VERS = "1.7.7"

maven_jar(
    name = "slf4j_api",
    artifact = "org.slf4j:slf4j-api:" + SL_VERS,
    sha1 = "2b8019b6249bb05d81d3a3094e468753e2b21311",
)

maven_jar(
    name = "slf4j_simple",
    artifact = "org.slf4j:slf4j-simple:" + SL_VERS,
    sha1 = "8095d0b9f7e0a9cd79a663c740e0f8fb31d0e2c8",
)

GUICE_VERSION = "4.1.0"

maven_jar(
    name = "multibindings",
    artifact = "com.google.inject.extensions:guice-multibindings:" + GUICE_VERSION,
    sha1 = "3b27257997ac51b0f8d19676f1ea170427e86d51",
)

maven_jar(
    name = "guice_library",
    artifact = "com.google.inject:guice:" + GUICE_VERSION,
    sha1 = "eeb69005da379a10071aa4948c48d89250febb07",
)

maven_jar(
    name = "guice_assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERSION,
    sha1 = "af799dd7e23e6fe8c988da12314582072b07edcb",
)

maven_jar(
    name = "aopalliance",
    artifact = "aopalliance:aopalliance:1.0",
    sha1 = "0235ba8b489512805ac13a8f9ea77a1ca5ebe3e8",
)

maven_jar(
    name = "javax_inject",
    artifact = "javax.inject:javax.inject:1",
    sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
)

JETTY_VERSION = "9.4.3.v20170317"

maven_jar(
    name = "servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERSION,
    sha1 = "3c564240ae2c3ec3a66c009884f5801f53be69c5",
)

maven_jar(
    name = "security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERSION,
    sha1 = "d7de81b385a054e65bada7282878a6178b25217e",
)

maven_jar(
    name = "server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERSION,
    sha1 = "216e823b42e86e78098e22c99aa49d557c61ca8e",
)

maven_jar(
    name = "continuation",
    artifact = "org.eclipse.jetty:jetty-continuation:" + JETTY_VERSION,
    sha1 = "bb1f8a3afaae0e44efd50cd0306ab9558bace99a",
)

maven_jar(
    name = "http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERSION,
    sha1 = "8f73fbfaabb3a3b2d5b3a2ddad15c19c7f00a876",
)

maven_jar(
    name = "io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERSION,
    sha1 = "0c2b20cfd32d74e74c030c0b77fa64d01434f2e3",
)

maven_jar(
    name = "util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERSION,
    sha1 = "1d3299563053e2290338e22db693a8d442e7fb25",
)
