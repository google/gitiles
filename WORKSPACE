workspace(name = "gitiles")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "bazel_skylib",
    sha256 = "bbccf674aa441c266df9894182d80de104cabd19be98be002f6d478aaa31574d",
    strip_prefix = "bazel-skylib-2169ae1c374aab4a09aa90e65efe1a3aad4e279b",
    urls = ["https://github.com/bazelbuild/bazel-skylib/archive/2169ae1c374aab4a09aa90e65efe1a3aad4e279b.tar.gz"],
)

load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "0.19.0")

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "bd5e7bafb2bd72a4f84e06f07490b41d2921a65b",
    # local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "MAVEN_CENTRAL",
    "maven_jar",
)

maven_jar(
    name = "commons-lang3",
    artifact = "org.apache.commons:commons-lang3:3.6",
    sha1 = "9d28a6b23650e8a7e9063c04588ace6cf7012c17",
)

maven_jar(
    name = "commons-text",
    artifact = "org.apache.commons:commons-text:1.2",
    sha1 = "74acdec7237f576c4803fff0c1008ab8a3808b2b",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.5",
    sha1 = "f645ed69d595b24d4cf8b3fbb64cc505bede8829",
)

maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:27.1-jre",
    sha1 = "e47b59c893079b87743cdcfb6f17ca95c08c592c",
)

maven_jar(
    name = "guava-failureaccess",
    artifact = "com.google.guava:failureaccess:1.0.1",
    sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
)

maven_jar(
    name = "jsr305",
    artifact = "com.google.code.findbugs:jsr305:3.0.0",
    attach_source = False,
    sha1 = "5871fb60dc68d67da54a663c3fd636a10a532948",
)

# When upgrading prettify it should also be updated in plugins/gitiles
maven_jar(
    name = "prettify",
    artifact = "com.github.twalcari:java-prettify:1.2.2",
    sha1 = "b8ba1c1eb8b2e45cfd465d01218c6060e887572e",
)

COMMONMARK_VERSION = "0.10.0"

# When upgrading commonmark it should also be updated in plugins/gitiles
maven_jar(
    name = "commonmark",
    artifact = "com.atlassian.commonmark:commonmark:" + COMMONMARK_VERSION,
    sha1 = "119cb7bedc3570d9ecb64ec69ab7686b5c20559b",
)

maven_jar(
    name = "cm-autolink",
    artifact = "com.atlassian.commonmark:commonmark-ext-autolink:" + COMMONMARK_VERSION,
    sha1 = "a6056a5efbd68f57d420bc51bbc54b28a5d3c56b",
)

maven_jar(
    name = "autolink",
    artifact = "org.nibor.autolink:autolink:0.7.0",
    sha1 = "649f9f13422cf50c926febe6035662ae25dc89b2",
)

maven_jar(
    name = "gfm-strikethrough",
    artifact = "com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:" + COMMONMARK_VERSION,
    sha1 = "40837da951b421b545edddac57012e15fcc9e63c",
)

maven_jar(
    name = "gfm-tables",
    artifact = "com.atlassian.commonmark:commonmark-ext-gfm-tables:" + COMMONMARK_VERSION,
    sha1 = "c075db2a3301100cf70c7dced8ecf86b494458a2",
)

maven_jar(
    name = "servlet-api_2_5",
    artifact = "org.eclipse.jetty.orbit:javax.servlet:2.5.0.v201103041518",
    sha1 = "9c16011c06bc6fe5e9dba080fcb40ddb4b75dc85",
)

maven_jar(
    name = "servlet-api_3_0",
    artifact = "org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016",
    sha1 = "0aaaa85845fb5c59da00193f06b8e5278d8bf3f8",
)

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:0.43",
    sha1 = "0cb9105957368f68d5fd771045bfa27d4a534836",
)

# Indirect dependency of truth
maven_jar(
    name = "diffutils",
    artifact = "com.googlecode.java-diff-utils:diffutils:1.3.0",
    sha1 = "7e060dd5b19431e6d198e91ff670644372f60fbd",
)

maven_jar(
    name = "soy",
    artifact = "com.google.template:soy:2019-03-11",
    sha1 = "119ac4b3eb0e2c638526ca99374013965c727097",
)

maven_jar(
    name = "html-types",
    artifact = "com.google.common.html.types:types:1.0.4",
    sha1 = "2adf4c8bfccc0ff7346f9186ac5aa57d829ad065",
)

maven_jar(
    name = "protobuf",
    artifact = "com.google.protobuf:protobuf-java:3.6.1",
    sha1 = "0d06d46ecfd92ec6d0f3b423b4cd81cb38d8b924",
)

maven_jar(
    name = "icu4j",
    artifact = "com.ibm.icu:icu4j:57.1",
    sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
)

JGIT_VERS = "5.2.0.201812061821-r"

JGIT_REPO = MAVEN_CENTRAL

maven_jar(
    name = "jgit-lib",
    artifact = "org.eclipse.jgit:org.eclipse.jgit:" + JGIT_VERS,
    repository = JGIT_REPO,
    sha1 = "250269f30458084777a480895e390d2a42143da3",
)

maven_jar(
    name = "jgit-servlet",
    artifact = "org.eclipse.jgit:org.eclipse.jgit.http.server:" + JGIT_VERS,
    repository = JGIT_REPO,
    sha1 = "5d7fbe1c8528d881e2987c75e512df2cfa408d73",
)

maven_jar(
    name = "jgit-junit",
    artifact = "org.eclipse.jgit:org.eclipse.jgit.junit:" + JGIT_VERS,
    repository = JGIT_REPO,
    sha1 = "723b9e6c54f8b3012dd7d4fe42b616b8d10ee230",
)

maven_jar(
    name = "jgit-archive",
    artifact = "org.eclipse.jgit:org.eclipse.jgit.archive:" + JGIT_VERS,
    repository = JGIT_REPO,
    sha1 = "6e49b0516b46ca90d394256d40c6069cdd8f2957",
)

maven_jar(
    name = "ewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

# When upgrading commons_compress, upgrade tukaani_xz to the
# corresponding version
maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.15",
    sha1 = "b686cd04abaef1ea7bc5e143c080563668eec17e",
)

# Transitive dependency of commons_compress. Should only be
# upgraded at the same time as commons_compress.
maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.6",
    attach_source = False,
    sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
    name = "hamcrest-core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

SL_VERS = "1.7.7"

maven_jar(
    name = "slf4j-api",
    artifact = "org.slf4j:slf4j-api:" + SL_VERS,
    sha1 = "2b8019b6249bb05d81d3a3094e468753e2b21311",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:" + SL_VERS,
    sha1 = "8095d0b9f7e0a9cd79a663c740e0f8fb31d0e2c8",
)

GUICE_VERSION = "4.2.1"

maven_jar(
    name = "guice-library",
    artifact = "com.google.inject:guice:" + GUICE_VERSION,
    sha1 = "f77dfd89318fe3ff293bafceaa75fbf66e4e4b10",
)

maven_jar(
    name = "guice-assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:" + GUICE_VERSION,
    sha1 = "d327e4aee7c96f08cd657c17da231a1f4a8999ac",
)

maven_jar(
    name = "aopalliance",
    artifact = "aopalliance:aopalliance:1.0",
    sha1 = "0235ba8b489512805ac13a8f9ea77a1ca5ebe3e8",
)

maven_jar(
    name = "javax-inject",
    artifact = "javax.inject:javax.inject:1",
    sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
)

JETTY_VERSION = "9.4.12.v20180830"

maven_jar(
    name = "servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERSION,
    sha1 = "4c1149328eda9fa39a274262042420f66d9ffd5f",
)

maven_jar(
    name = "security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERSION,
    sha1 = "299e0602a9c0b753ba232cc1c1dda72ddd9addcf",
)

maven_jar(
    name = "server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERSION,
    sha1 = "b0f25df0d32a445fd07d5f16fff1411c16b888fa",
)

maven_jar(
    name = "continuation",
    artifact = "org.eclipse.jetty:jetty-continuation:" + JETTY_VERSION,
    sha1 = "5f6d6e06f95088a3a7118b9065bc49ce7c014b75",
)

maven_jar(
    name = "http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERSION,
    sha1 = "1341796dde4e16df69bca83f3e87688ba2e7d703",
)

maven_jar(
    name = "io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERSION,
    sha1 = "e93f5adaa35a9a6a85ba130f589c5305c6ecc9e3",
)

maven_jar(
    name = "util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERSION,
    sha1 = "cb4ccec9bd1fe4b10a04a0fb25d7053c1050188a",
)
