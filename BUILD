load("@com_googlesource_gerrit_bazlets//tools:pkg_war.bzl", "pkg_war")

pkg_war(
    name = "gitiles",
    context = ["//gitiles-servlet:webassets"],
    libs = [
        "//gitiles-servlet:servlet",
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/slf4j:slf4j-simple",
    ],
    web_xml = "//gitiles-war:web_xml",
)
