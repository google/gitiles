DEPS = [
    "//lib:autolink",
    "//lib:commons-lang3",
    "//lib:commons-text",
    "//lib:gson",
    "//lib:guava",
    "//lib:html-types",
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
    "//java/com/google/gitiles/blame/cache",
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
    srcs = glob(["**/*.java"]),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gitiles"],
    visibility = ["//visibility:public"],
    deps = DEPS_ALL + [
        ":servlet-api",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:javadoc.bzl", "java_doc")

java_doc(
    name = "servlet-javadoc",
    libs = DEPS + [
        ":servlet",
    ],
    pkgs = ["com.google.gitiles"],
    title = "Gitiles API Documentation",
    visibility = ["//visibility:public"],
)

load("//tools:stamper.bzl", "stamp")

stamp(
    name = "servlet",
    workspace = "gitiles",
)
