package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "jgit-servlet",
    exports = ["@jgit-servlet//jar"],
)

java_library(
    name = "jgit",
    exports = ["@jgit-lib//jar"],
)

java_library(
    name = "jgit-archive",
    exports = [
        ":commons-compress",
        ":jgit-archive_library",
        ":tukaani-xz",
    ],
)

java_library(
    name = "tukaani-xz",
    exports = ["@tukaani-xz//jar"],
)

java_library(
    name = "commons-compress",
    exports = ["@commons-compress//jar"],
)

java_library(
    name = "jgit-archive_library",
    exports = ["@jgit-archive//jar"],
)

java_library(
    name = "junit",
    exports = ["@jgit-junit//jar"],
)
