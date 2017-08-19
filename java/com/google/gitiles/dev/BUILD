java_library(
    name = "lib",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gitiles:servlet",
        "//lib:guava",
        "//lib:html-types",
        "//lib:servlet-api_3_0",
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/jgit",
        "//lib/jgit:jgit-servlet",
        "//lib/slf4j:slf4j-api",
        "//lib/slf4j:slf4j-simple",
        "//lib/soy",
    ],
)

java_binary(
    name = "dev",
    main_class = "com.google.gitiles.dev.Main",
    runtime_deps = [":lib"],
)
