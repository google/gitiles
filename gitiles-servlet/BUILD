load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)

DEPS = [
    "//blame-cache:blame-cache",
    "//lib:autolink",
    "//lib:commons-lang3",
    "//lib:gson",
    "//lib:guava",
    "//lib:joda-time",
    "//lib:jsr305",
    "//lib:commonmark",
    "//lib:cm-autolink",
    "//lib:gfm-tables",
    "//lib:gfm-strikethrough",
    "//lib:prettify",
    "//lib/jgit:jgit",
    "//lib/jgit:jgit-servlet",
    "//lib/slf4j:slf4j-api",
    "//lib/soy:soy",
]

DEPS_ALL = DEPS + [
    "//lib/jgit:jgit-archive",
    "//lib/guice:guice",
]

java_library(
    name = "servlet-api",
    neverlink = 1,
    exports = ["//lib:servlet-api_2_5"],
)

java_library(
    name = "servlet",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    visibility = ["//visibility:public"],
    deps = DEPS_ALL + [
        ":servlet-api",
    ],
)

genrule2(
    name = "webassets",
    srcs = glob(["src/main/resources/com/google/gitiles/static/**/*"]),
    outs = ["webassets.zip"],
    cmd = " && ".join([
        "o=$$PWD/$@",
        "tar cf - $(SRCS) | tar -C $$TMP/ --strip-components=2 -xf -",
        "cd $$TMP/main/resources/com/google/gitiles/",
        "mv static +static",
        "zip -qr $$o .",
    ]),
    visibility = ["//visibility:public"],
)

java_library(
    name = "testutil",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = ["src/test/java/**/*Test.java"],
    ) + glob(["**/ServletTest.java"]),
    deps = DEPS + [
        ":servlet",
        "//lib:servlet-api_2_5",
        "//lib:truth",
        "//lib/jgit:junit",
        "//lib/junit",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

junit_tests(
    name = "servlet_tests",
    srcs = glob(
        [
            "src/test/java/**/*Test.java",
        ],
        exclude = ["**/ServletTest.java"],
    ),
    visibility = ["//visibility:public"],
    runtime_deps = ["//lib/junit:hamcrest-core"],
    deps = DEPS + [
        ":servlet",
        ":testutil",
        "//lib:servlet-api_2_5",
        "//lib:truth",
        "//lib/jgit:junit",
        "//lib/junit",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:javadoc.bzl", "java_doc")

java_doc(
    name = "javadoc",
    libs = DEPS + [
        ":servlet",
    ],
    pkgs = ["com.google.gitiles"],
    title = "Gitiles API Documentation",
    visibility = ["//visibility:public"],
)
