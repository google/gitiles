load("//:version.bzl", "GITILES_VERSION")
load("@com_googlesource_gerrit_bazlets//tools/maven:package.bzl", "maven_package")

maven_package(
    version = GITILES_VERSION,
    group = "com.google.gitiles",
    repository = "gerrit-maven-repository",
    url = "gs://gerrit-maven",
    jar = {
        "blame-cache": "//blame-cache:lib",
        "gitiles-servlet": "//gitiles-servlet:servlet",
    },
    src = {
        "blame-cache": "//blame-cache:liblib-src.jar",
        "gitiles-servlet": "//gitiles-servlet:libservlet-src.jar",
    },
    doc = {
        "blame-cache": "//blame-cache:javadoc",
        "gitiles-servlet": "//gitiles-servlet:javadoc",
    },
)
