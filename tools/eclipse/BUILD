load("@com_googlesource_gerrit_bazlets//tools:classpath.bzl", "classpath_collector")
load("@com_googlesource_gerrit_bazlets//tools:py_binary_path.bzl", "py_binary_path")

DEPS = [
    "//java/com/google/gitiles:servlet",
    "//java/com/google/gitiles/dev:lib",
]

java_library(
    name = "classpath",
    runtime_deps = DEPS,
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = 1,
    deps = DEPS + [
        "//javatests/com/google/gitiles:servlet_tests",
    ],
)

# TODO(davido): This is a hack:
# https://github.com/bazelbuild/bazel/issues/2452
# We know the py_binary rule,  but for *reasons* we cannot
# use `buck run <rule>. Unfortunately, a query to retrieve
# the path was removed in Bazel. So we are forced to do this
# hack to access python script outside of the Bazel.
py_binary_path(
    name = "project.py",
    py_binary_label = "@com_googlesource_gerrit_bazlets//tools/eclipse:project",
)
