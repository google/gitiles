load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "servlet",
    exports = [
        ":security",
        "//lib:servlet-api_3_0",  # Different from the rest of gitiles-server.
        "@servlet//jar",
    ],
)

java_library(
    name = "security",
    exports = [
        ":server",
        "@security//jar",
    ],
)

java_library(
    name = "server",
    exports = [
        ":continuation",
        ":http",
        "@server//jar",
    ],
)

java_library(
    name = "continuation",
    exports = ["@continuation//jar"],
)

java_library(
    name = "http",
    exports = [
        ":io",
        "@http//jar",
    ],
)

java_library(
    name = "io",
    exports = [
        ":util",
        "@io//jar",
    ],
)

java_library(
    name = "util",
    exports = ["@util//jar"],
)
