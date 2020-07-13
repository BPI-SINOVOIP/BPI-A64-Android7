# trigger recipe module

Trigger recipe allows you to trigger new builds and pass arbitrary properties.

## Examples
Basic:

    api.trigger({
        'builder_name': 'HelloWorld',
        'properties': {
            'my_prop': 123,
        },
    })

This triggers a new build on HelloWorld builder with "my_prop" build property
set to 123.

You can trigger multiple builds in one steps:

    api.trigger(
        {'builder_name': 'Release'},
        {'builder_name': 'Debug'},
    )

You can trigger a build on a different buildbot master:

    api.trigger({
        'builder_name': 'Release',
        'bucket': 'master.tryserver.chromium.linux', # specify master name here.
    })

This uses [buildbucket](../../../master/buildbucket) underneath and must be
configured.

Specify different Buildbot changes:

    api.trigger({
        'builder_name': 'Release',
        'buildbot_changes': [{
            'author': 'someone@chromium.org',
            'branch': 'master',
            'files': ['a.txt.'],
            'comments': 'Refactoring',
            'revision': 'deadbeef',
            'revlink':
              'http://chromium.googlesource.com/chromium/src/+/deadbeef',
            'when_timestamp': 1416859562,
        }]
    })
