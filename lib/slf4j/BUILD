package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "slf4j-api",
    exports = ["@slf4j-api//jar"],
)

java_library(
    name = "slf4j-simple",
    runtime_deps = [
        ":slf4j-api",
        "@slf4j-simple//jar",
    ],
)
