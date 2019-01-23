load("@com_googlesource_gerrit_bazlets//tools/maven:package.bzl", "maven_package")
load("//:version.bzl", "GITILES_VERSION")

maven_package(
    src = {
        "blame-cache": "//java/com/google/gitiles/blame/cache:libcache-src.jar",
        "gitiles-servlet": "//java/com/google/gitiles:libservlet-src.jar",
    },
    doc = {
        "blame-cache": "//java/com/google/gitiles/blame/cache:cache-javadoc",
        "gitiles-servlet": "//java/com/google/gitiles:servlet-javadoc",
    },
    group = "com.google.gitiles",
    jar = {
        "blame-cache": "//java/com/google/gitiles/blame/cache:cache-stamped",
        "gitiles-servlet": "//java/com/google/gitiles:servlet-stamped",
    },
    repository = "gerrit-maven-repository",
    url = "gs://gerrit-maven",
    version = GITILES_VERSION,
)
