load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "truth",
    visibility = ["//visibility:public"],
    exports = ["@truth//jar"],
    runtime_deps = [
        ":diffutils",
        "//lib:guava",
        "//lib/junit",
    ],
)

java_library(
    name = "diffutils",
    visibility = ["//visibility:private"],
    exports = ["@diffutils//jar"],
)
