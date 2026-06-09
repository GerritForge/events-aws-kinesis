load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

PLUGIN = "events-aws-kinesis"

EXT_DEPS = [
    "com.amazonaws:amazon-kinesis-producer",
    "com.amazonaws:aws-java-sdk-core",
    "software.amazon.awssdk:auth",
    "software.amazon.awssdk:cloudwatch",
    "software.amazon.awssdk:dynamodb",
    "software.amazon.awssdk:kinesis",
    "software.amazon.awssdk:regions",
    "software.amazon.kinesis:amazon-kinesis-client",
]

TEST_EXT_DEPS = EXT_DEPS + [
    "org.testcontainers:localstack",
    "org.testcontainers:testcontainers",
    "software.amazon.awssdk:url-connection-client",
]

gerrit_plugin(
    srcs = glob(["src/main/java/**/*.java"]),
    ext_deps = EXT_DEPS,
    manifest_entries = [
        "Gerrit-PluginName: events-aws-kinesis",
        "Gerrit-InitStep: com.gerritforge.gerrit.plugins.kinesis.InitConfig",
        "Gerrit-Module: com.gerritforge.gerrit.plugins.kinesis.Module",
        "Implementation-Title: Gerrit events listener to send events to AWS Kinesis broker",
        "Implementation-URL: https://github.com/GerritForge/events-aws-kinesis",
    ],
    plugin = PLUGIN,
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":events-broker-neverlink",
    ],
)

gerrit_plugin_tests(
    timeout = "long",
    srcs = glob(["src/test/java/**/*.java"]),
    ext_deps = TEST_EXT_DEPS,
    plugin = PLUGIN,
    deps = ["//plugins/events-broker"],
)

java_library(
    name = "events-broker-neverlink",
    neverlink = 1,
    exports = ["//plugins/events-broker"],
)
