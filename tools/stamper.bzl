# TODO(davido): Consider to move this general bazlets to Bazlets repository
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")

def stamp(workspace, name):
    # TODO(davido): Remove manual merge of manifest file when this feature
    # request is implemented: https://github.com/bazelbuild/bazel/issues/2009
    genrule2(
        name = "%s-stamped" % name,
        stamp = 1,
        srcs = [":%s" % name],
        cmd = " && ".join([
            "GEN_VERSION=$$(cat bazel-out/stable-status.txt | grep -w STABLE_BUILD_%s_LABEL | cut -d ' ' -f 2)" % workspace.upper(),
            "cd $$TMP",
            "unzip -q $$ROOT/$<",
            "echo \"Implementation-Version: $$GEN_VERSION\n$$(cat META-INF/MANIFEST.MF)\" > META-INF/MANIFEST.MF",
            "zip -qr $$ROOT/$@ .",
        ]),
        outs = ["%s-stamped.jar" % name],
        visibility = ["//visibility:public"],
    )
