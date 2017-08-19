# TODO(davido): Move sources that depend on the rest of gitiles
# out of the blame directory and move this rule into its own BUILD
# file in blame directory.
BLAME_CACHE_SRCS = ["blame/" + n for n in [
    "BlameCache.java",
    "BlameCacheImpl.java",
    "Region.java",
]]

java_library(
    name = "blame-cache",
    srcs = BLAME_CACHE_SRCS,
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib/jgit",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:javadoc.bzl", "java_doc")

java_doc(
    name = "blame-cache-javadoc",
    libs = [
        ":blame-cache",
        "//lib:guava",
        "//lib/jgit:jgit",
    ],
    pkgs = ["com.google.gitiles.blame"],
    title = "Blame Cache API Documentation",
    visibility = ["//visibility:public"],
)

DEPS = [
    ":blame-cache",
    "//lib:autolink",
    "//lib:commons-lang3",
    "//lib:gson",
    "//lib:guava",
    "//lib:html-types",
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
    srcs = glob(
        ["**/*.java"],
        exclude = BLAME_CACHE_SRCS,
    ),
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
