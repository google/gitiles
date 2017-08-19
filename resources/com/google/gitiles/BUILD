load(
    "@com_googlesource_gerrit_bazlets//tools:genrule2.bzl",
    "genrule2",
)

filegroup(
    name = "gitiles",
    srcs = glob(
        ["**/*"],
        exclude = ["BUILD"],
    ),
    visibility = ["//visibility:public"],
)

genrule2(
    name = "webassets",
    srcs = [":gitiles"],
    outs = ["webassets.zip"],
    cmd = " && ".join([
        "o=$$PWD/$@",
        "tar cf - $(SRCS) | tar -C $$TMP/ --strip-components=1 -xf -",
        "cd $$TMP/com/google/gitiles/",
        "mv static +static",
        "zip -qr $$o .",
    ]),
    visibility = ["//visibility:public"],
)
