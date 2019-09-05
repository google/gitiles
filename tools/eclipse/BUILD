load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:classpath.bzl", "classpath_collector")

DEPS = [
    "//java/com/google/gitiles:servlet",
    "//java/com/google/gitiles/dev:lib",
]

java_library(
    name = "classpath",
    runtime_deps = DEPS,
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = 1,
    deps = DEPS + [
        "//javatests/com/google/gitiles:servlet_tests",
    ],
)
