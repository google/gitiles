SRCS = glob(["src/main/java/**/*.java"])

DEPS = [
    "//lib:guava",
    "//lib/jgit:jgit",
]

java_library(
    name = "blame-cache",
    srcs = SRCS,
    visibility = ["//visibility:public"],
    deps = DEPS,
)

load("@com_googlesource_gerrit_bazlets//tools:javadoc.bzl", "java_doc")

java_doc(
    name = "javadoc",
    libs = [
        ":blame-cache",
        "//lib:guava",
        "//lib/jgit:jgit",
    ],
    pkgs = ["com.google.gitiles.blame"],
    title = "Blame Cache API Documentation",
    visibility = ["//visibility:public"],
)
