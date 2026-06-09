load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

PLUGIN = "events-aws-kinesis"

AWS_SDK_VER = "2.16.19"
AWS_KINESIS_VER = "2.3.4"
JACKSON_VER = "2.10.4"
TESTCONTAINERS_VERSION = "1.15.3"
DOCKER_JAVA_VERS = "3.2.8"

EXT_DEPS = [
    "com.amazonaws:amazon-kinesis-producer:0.14.6",
    "com.amazonaws:aws-java-sdk-core:1.11.960",
    "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VER,
    "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VER,
    "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VER,
    "com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:" + JACKSON_VER,
    "commons-lang:commons-lang:2.6",
    "io.netty:netty-all:4.1.51.Final",
    "io.projectreactor:reactor-core:3.4.3",
    "io.reactivex.rxjava2:rxjava:2.1.14",
    "javax.xml.bind:jaxb-api:2.3.1",
    "org.reactivestreams:reactive-streams:1.0.2",
    "software.amazon.awssdk:auth:" + AWS_SDK_VER,
    "software.amazon.awssdk:aws-cbor-protocol:" + AWS_SDK_VER,
    "software.amazon.awssdk:aws-core:" + AWS_SDK_VER,
    "software.amazon.awssdk:aws-json-protocol:" + AWS_SDK_VER,
    "software.amazon.awssdk:aws-query-protocol:" + AWS_SDK_VER,
    "software.amazon.awssdk:cloudwatch:" + AWS_SDK_VER,
    "software.amazon.awssdk:dynamodb:" + AWS_SDK_VER,
    "software.amazon.awssdk:http-client-spi:" + AWS_SDK_VER,
    "software.amazon.awssdk:kinesis:" + AWS_SDK_VER,
    "software.amazon.awssdk:metrics-spi:" + AWS_SDK_VER,
    "software.amazon.awssdk:netty-nio-client:" + AWS_SDK_VER,
    "software.amazon.awssdk:profiles:" + AWS_SDK_VER,
    "software.amazon.awssdk:protocol-core:" + AWS_SDK_VER,
    "software.amazon.awssdk:regions:" + AWS_SDK_VER,
    "software.amazon.awssdk:sdk-core:" + AWS_SDK_VER,
    "software.amazon.awssdk:utils:" + AWS_SDK_VER,
    "software.amazon.glue:schema-registry-serde:1.0.0",
    "software.amazon.kinesis:amazon-kinesis-client:" + AWS_KINESIS_VER,
]

TEST_EXT_DEPS = EXT_DEPS + [
    "com.github.docker-java:docker-java-api:" + DOCKER_JAVA_VERS,
    "com.github.docker-java:docker-java-transport:" + DOCKER_JAVA_VERS,
    "net.java.dev.jna:jna:5.5.0",
    "org.rnorth.duct-tape:duct-tape:1.0.8",
    "org.rnorth.visible-assertions:visible-assertions:2.1.2",
    "org.testcontainers:localstack:" + TESTCONTAINERS_VERSION,
    "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
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