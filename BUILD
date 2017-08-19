load("@com_googlesource_gerrit_bazlets//tools:pkg_war.bzl", "pkg_war")

pkg_war(
    name = "gitiles",
    context = ["//resources/com/google/gitiles:webassets"],
    libs = [
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/slf4j:slf4j-simple",
        "//java/com/google/gitiles:servlet",
    ],
    web_xml = "//resources:web_xml",
)
