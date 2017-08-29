java_library(
    name = "cache",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib/jgit",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:javadoc.bzl", "java_doc")

java_doc(
    name = "cache-javadoc",
    libs = [
        ":cache",
        "//lib:guava",
        "//lib/jgit:jgit",
    ],
    pkgs = ["com.google.gitiles.blame.cache"],
    title = "Blame Cache API Documentation",
    visibility = ["//visibility:public"],
)

load("//tools:stamper.bzl", "stamp")

stamp(
    name = "cache",
    workspace = "gitiles",
)
