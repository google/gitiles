load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

[java_library(
    name = n,
    exports = ["@%s//jar" % n],
) for n in [
    "autolink",
    "commonmark",
    "commons-lang3",
    "commons-text",
    "cm-autolink",
    "gfm-strikethrough",
    "gfm-tables",
    "html-types",
    "jsr305",
    "servlet-api_2_5",
    "servlet-api_3_0",
    "gson",
    "guava",
    "prettify",
]]
