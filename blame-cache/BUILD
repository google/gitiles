SRCS = glob(["src/main/java/**/*.java"])

DEPS = [
    "//lib:guava",
    "//lib/jgit:jgit",
]

java_library(
    name = "lib",
    srcs = SRCS,
    visibility = ["//visibility:public"],
    deps = DEPS,
)

load("@com_googlesource_gerrit_bazlets//tools:javadoc.bzl", "java_doc")

java_doc(
    name = "javadoc",
    libs = [
        ":lib",
        "//lib:guava",
        "//lib/jgit:jgit",
    ],
    pkgs = ["com.google.gitiles.blame"],
    title = "Blame Cache API Documentation",
    visibility = ["//visibility:public"],
)
