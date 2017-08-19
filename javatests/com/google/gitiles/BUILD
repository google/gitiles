DEPS = [
    "//lib:gson",
    "//lib:guava",
    "//lib/jgit:jgit",
    "//lib/jgit:jgit-servlet",
    "//lib:joda-time",
    "//lib/soy:soy",
]

java_library(
    name = "testutil",
    srcs = glob(
        ["**/*.java"],
        exclude = ["**/*Test.java"],
    ) + glob(["**/ServletTest.java"]),
    deps = DEPS + [
        "//java/com/google/gitiles:servlet",
        "//lib:servlet-api_2_5",
        "//lib:truth",
        "//lib/jgit:junit",
        "//lib/junit",
    ],
)

load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

junit_tests(
    name = "servlet_tests",
    srcs = glob(
        [
            "**/*Test.java",
        ],
        exclude = ["**/ServletTest.java"],
    ),
    visibility = ["//visibility:public"],
    runtime_deps = ["//lib/junit:hamcrest-core"],
    deps = DEPS + [
        "//java/com/google/gitiles:servlet",
        ":testutil",
        "//lib:servlet-api_2_5",
        "//lib:truth",
        "//lib/jgit:junit",
        "//lib/junit",
    ],
)
