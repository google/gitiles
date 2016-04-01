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
