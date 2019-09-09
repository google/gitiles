load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "guice",
    exports = [
        ":aopalliance",
        ":guice-assistedinject",
        ":guice-library",
        ":javax-inject",
    ],
)

[java_library(
    name = n,
    runtime_deps = ["@%s//jar" % n],
) for n in [
    "guice-library",
    "guice-assistedinject",
    "javax-inject",
    "aopalliance",
]]
