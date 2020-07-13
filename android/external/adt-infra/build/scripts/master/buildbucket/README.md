buildbucket module can be used to connect a Buildbot master to buildbucket.

## Configuration

Configuring a buildbot master is trivial:

    # master_site_config.py

    class MyMaster(Master3):
        # ...
        service_account_file = 'service-account-<name>.json'
        buildbucket_bucket = '<your-bucket-name>'


Chromium masters use "service-account-chromium.json" and
"service-account-chromium-tryserver.json" service accounts. For other projects,
write to [infra-dev@chromium.org](mailto:infra-dev@chromium.org).

`buildbucket_bucket` is a name of the bucket on buildbucket. Ping
nodir@chromium.org to get one set up.

## How it works in the nutshell
Every ten seconds the Buildbot master [peeks][api_peek] builds in the
specified bucket until it reaches lease count limit. For each valid peeked
build the master tries to lease it. If leased successfully, master schedules a
build.

During build lifetime master reports build status back to buildbucket. When the
build starts, master calls [start][api_start] API, and when build finishes, it
calls [succeed][api_succeed] or [fail][api_fail] APIs. For SKIPPED and RETRY
builds the master does not notify buidbucket, so the lease expires soon and the
build will be rescheduled.

Every minute buildbot [sends heartbeats][api_heartbeat] for currently held
leases. If the master discovers that a build lease expired, it stops the build.

### Build parameters
Buildbot-buildbucket integration supports the following build parameters:

* builder_name (str): required name of a builder to trigger. If builder is not
  found, the build is skipped.
* properties (dict): arbitrary build properties. Property 'buildbucket' is
  reserved.
* changes (list of dict): list of changes to be associated with the build, used
  to create Buildbot changes for builds.
  Each change is a dict with keys:
    * id (str): a unique identity of the change.
      If id and revision are specified, buildbot master will search for an
      existing buildbot change prior creating a new one. Also see implementation
      details below.
    * revision (str): change revision, such as commit sha or svn revision.
    * author (dict): author of the change. REQUIRED.
        * email (str): change author's email. REQUIRED.
    * create_ts (int): change creation timestamp, in microseconds since Epoch.
    * files (list of dict): list of changed files.
      Each file is a dict with keys:
        * path (str): file path relative to the repository root.
    * message (str): change description.
    * branch (str): change branch.
    * url (str): url to human-viewable change page.
    * project (str): name of project this change refers to.

## Limitations

Current implementation has the following limitations:

* Only one buildbucket instance configuration per buildbot master is supported.
* Build request merging is not supported. If build requests are merged,
  only one buildbucket build will be updated.
  Bug: http://crbug.com/451259

## Implications of using buildbucket.

* Scheduling code does not have to be hosted on buildbot.
* [trigger](../../slave/recipe_modules/trigger) recipe module can trigger
  builds on different masters.
* Multiple masters can be setup to poll the same buckets(s). This allows
  parallel build processing.

## Implementation details

* Buildbucket-specific information, such as build id and lease key, is stored in
  "buildbucket" property of Buidlbot entities.
* When change id and revision are specified, buildbot master executes a database
  query to find all changes matching a revision, assuming revision is uniquish,
  and then searches in memory for change by id.
* Current leases are stored in memory. On startup, buildbot master loads them
  from the database, which can be long if there is a lot of pending build
  requests (will not happen if only buildbucket is used).

[api_peek]: https://cr-buildbucket.appspot.com/_ah/api/explorer/#p/buildbucket/v1/buildbucket.peek
[api_start]: https://cr-buildbucket.appspot.com/_ah/api/explorer/#p/buildbucket/v1/buildbucket.start
[api_heartbeat]: https://cr-buildbucket.appspot.com/_ah/api/explorer/#p/buildbucket/v1/buildbucket.heartbeat_batch
[api_succeed]: https://cr-buildbucket.appspot.com/_ah/api/explorer/#p/buildbucket/v1/buildbucket.succeed
[api_fail]: https://cr-buildbucket.appspot.com/_ah/api/explorer/#p/buildbucket/v1/buildbucket.fail
[cr-buildbucket-dev-9c9efb83ec4b.json]: http://storage.googleapis.com/cr-buildbucket-dev/cr-buildbucket-dev-9c9efb83ec4b.json
[buildbucket-service-account-bug]: https://go/buildbucket-service-account-bug
