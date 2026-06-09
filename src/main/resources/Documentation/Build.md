# Build

The events-aws-kinesis plugin can be build as a regular 'in-tree' plugin. That means
that is required to clone a Gerrit source tree first and then to have the plugin
source directory into the `/plugins` path. The plugin depends on [events-broker](https://gerrit.googlesource.com/modules/events-broker)
which is linked directly from source with the same 'in-tree' plugin structure.

Additionally, the `plugins/external_plugin_deps.MODULE.bazel` file needs to include
the plugin's module fragment to wire its Maven dependencies into the in-tree build.

```shell script
git clone --recursive https://gerrit.googlesource.com/gerrit
cd gerrit
git clone "https://github.com/GerritForge/events-aws-kinesis" plugins/events-aws-kinesis
git clone "https://gerrit.googlesource.com/modules/events-broker" plugins/events-broker
ln -sf plugins/events-aws-kinesis/external_plugin_deps.MODULE.bazel plugins/.
bazelisk build plugins/events-aws-kinesis
```

The output is created in

```
bazel-bin/plugins/events-aws-kinesis/events-aws-kinesis.jar
```