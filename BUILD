load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

PLUGIN = "events-aws-kinesis"

AWS_SDK_VER = "2.16.19"

EXT_DEPS = [
    "com.amazonaws:amazon-kinesis-producer:0.14.6",
    "com.amazonaws:aws-java-sdk-core:1.11.960",
    "software.amazon.awssdk:auth:" + AWS_SDK_VER,
    "software.amazon.awssdk:cloudwatch:" + AWS_SDK_VER,
    "software.amazon.awssdk:dynamodb:" + AWS_SDK_VER,
    "software.amazon.awssdk:kinesis:" + AWS_SDK_VER,
    "software.amazon.awssdk:regions:" + AWS_SDK_VER,
    "software.amazon.kinesis:amazon-kinesis-client:2.3.4",
]

TEST_EXT_DEPS = EXT_DEPS + [
    "org.testcontainers:localstack:1.15.3",
    "org.testcontainers:testcontainers:1.15.3",
    "software.amazon.awssdk:url-connection-client:" + AWS_SDK_VER,
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
    name = "events_aws_kinesis_tests",
    timeout = "long",
    srcs = glob(["src/test/java/**/*.java"]),
    ext_deps = TEST_EXT_DEPS,
    plugin = PLUGIN,
    tags = ["events-aws-kinesis"],
    deps = ["//plugins/events-broker"],
)

java_library(
    name = "events-broker-neverlink",
    neverlink = 1,
    exports = ["//plugins/events-broker"],
)
