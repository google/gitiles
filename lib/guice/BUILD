package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "guice",
    exports = [
        ":aopalliance",
        ":guice-assistedinject",
        ":guice_library",
        ":javax-inject",
    ],
)

[java_library(
    name = n,
    runtime_deps = ["@%s//jar" % n.replace("-", "_")],
) for n in [
    "guice_library",
    "guice-assistedinject",
    "javax-inject",
    "aopalliance",
]]
