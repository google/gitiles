package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "jgit-servlet",
    exports = ["@jgit_servlet//jar"],
)

java_library(
    name = "jgit",
    exports = ["@jgit_lib//jar"],
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
    exports = ["@tukaani_xz//jar"],
)

java_library(
    name = "commons-compress",
    exports = ["@commons_compress//jar"],
)

java_library(
    name = "jgit-archive_library",
    exports = ["@jgit_archive//jar"],
)

java_library(
    name = "junit",
    exports = ["@jgit_junit//jar"],
)
