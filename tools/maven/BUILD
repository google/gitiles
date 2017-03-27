load("//:version.bzl", "GITILES_VERSION")
load("@com_googlesource_gerrit_bazlets//tools/maven:package.bzl", "maven_package")

maven_package(
    src = {
        "blame-cache": "//blame-cache:liblib-src.jar",
        "gitiles-servlet": "//gitiles-servlet:libservlet-src.jar",
    },
    doc = {
        "blame-cache": "//blame-cache:javadoc",
        "gitiles-servlet": "//gitiles-servlet:javadoc",
    },
    group = "com.google.gitiles",
    jar = {
        "blame-cache": "//blame-cache:lib",
        "gitiles-servlet": "//gitiles-servlet:servlet",
    },
    repository = "gerrit-maven-repository",
    url = "gs://gerrit-maven",
    version = GITILES_VERSION,
)
