# This file is part of Buildbot.  Buildbot is free software: you can
# redistribute it and/or modify it under the terms of the GNU General Public
# License as published by the Free Software Foundation, version 2.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
# details.
#
# You should have received a copy of the GNU General Public License along with
# this program; if not, write to the Free Software Foundation, Inc., 51
# Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Portions Copyright Buildbot Team Members
# Original Copyright (c) 2010 The Chromium Authors.

"""Simple JSON exporter."""

import collections
import datetime
import fnmatch
import os
import re

from twisted.internet import defer
from twisted.python import log as twlog
from twisted.web import html, resource, server

from buildbot.changes import changes
from buildbot.status.builder import SUCCESS, WARNINGS, FAILURE
from buildbot.status.web.base import HtmlResource
from buildbot.util import json, now


def get_timeblock():
    def dt_to_ts(date):
        return (date - datetime.datetime.utcfromtimestamp(0)).total_seconds()

    utcnow = datetime.datetime.utcnow()

    return {
            'local': datetime.datetime.now().isoformat(),
            'utc': utcnow.isoformat(),
            'utc_ts': dt_to_ts(utcnow),
    }


SERVER_STARTED = get_timeblock()


_IS_INT = re.compile('^[-+]?\d+$')


FLAGS = """\
  - as_text
    - By default, application/json is used. Setting as_text=1 change the type
      to text/plain and implicitly sets compact=0 and filter=1. Mainly useful to
      look at the result in a web browser.
  - compact
    - By default, the json data is compact and defaults to 1. For easier to read
      indented output, set compact=0.
  - select
    - By default, most children data is listed. You can do a random selection
      of data by using select=<sub-url> multiple times to coagulate data.
      "select=" includes the actual url otherwise it is skipped.
  - filter
    - Filters out null, false, and empty string, list and dict. This reduce the
      amount of useless data sent.
  - callback
    - Enable uses of JSONP as described in
      http://en.wikipedia.org/wiki/JSONP. Note that
      Access-Control-Allow-Origin:* is set in the HTTP response header so you
      can use this in compatible browsers.
"""

EXAMPLES = """\
  - /json
    - Root node, that *doesn't* mean all the data. Many things (like logs) must
      be explicitly queried for performance reasons.
  - /json/builders/
    - All builders.
  - /json/builders/<A_BUILDER>
    - A specific builder as compact text.
  - /json/builders/<A_BUILDER>/builds
    - All *cached* builds.
  - /json/builders/<A_BUILDER>/builds/_all
    - All builds. Warning, reads all previous build data.
  - /json/builders/<A_BUILDER>/builds/<A_BUILD>
    - Where <A_BUILD> is either positive, a build number, or negative, a past
      build.
  - /json/builders/<A_BUILDER>/builds/-1/source_stamp/changes
    - Build changes
  - /json/builders/<A_BUILDER>/builds?select=-1&select=-2
    - Two last builds on '<A_BUILDER>' builder.
  - /json/builders/<A_BUILDER>/builds?select=-1/source_stamp/changes&select=-2/source_stamp/changes
    - Changes of the two last builds on '<A_BUILDER>' builder.
  - /json/builders/<A_BUILDER>/slaves
    - Slaves associated to this builder.
  - /json/builders/<A_BUILDER>?select=&select=slaves
    - Builder information plus details information about its slaves. Neat eh?
  - /json/slaves/<A_SLAVE>
    - A specific slave.
  - /json/buildstate
    - The current build state.
  - /json?select=slaves/<A_SLAVE>/&select=project&select=builders/<A_BUILDER>/builds/<A_BUILD>
    - A selection of random unrelated stuff as an random example. :)
"""


def RequestArg(request, arg, default):
    return request.args.get(arg, [default])[0]


def RequestArgToBool(request, arg, default):
    value = RequestArg(request, arg, default)
    if value in (False, True):
        return value
    value = value.lower()
    if value in ('1', 'true'):
        return True
    if value in ('0', 'false'):
        return False
    # Ignore value.
    return default


def FilterOut(data):
    """Returns a copy with None, False, "", [], () and {} removed.
    Warning: converts tuple to list."""
    if isinstance(data, (list, tuple)):
        # Recurse in every items and filter them out.
        items = map(FilterOut, data)
        if not filter(lambda x: not x in ('', False, None, [], {}, ()), items):
            return None
        return items
    elif isinstance(data, dict):
        return dict(filter(lambda x: not x[1] in ('', False, None, [], {}, ()),
                           [(k, FilterOut(v)) for (k, v) in data.iteritems()]))
    else:
        return data


class JsonResource(resource.Resource):
    """Base class for json data."""

    contentType = "application/json"
    cache_seconds = 60
    help = None
    pageTitle = None
    level = 0

    def __init__(self, status):
        """Adds transparent lazy-child initialization."""
        resource.Resource.__init__(self)
        # buildbot.status.builder.Status
        self.status = status

    def getChildWithDefault(self, path, request):
        """Adds transparent support for url ending with /"""
        if path == "" and len(request.postpath) == 0:
            return self
        if (path == "help" or path == "help/") and self.help:
            pageTitle = ''
            if self.pageTitle:
                pageTitle = self.pageTitle + ' help'
            res = HelpResource(self.help, pageTitle=pageTitle, parent_node=self)
            res.level = self.level + 1
            return res
        # Equivalent to resource.Resource.getChildWithDefault()
        if self.children.has_key(path):
            return self.children[path]
        return self.getChild(path, request)

    def putChild(self, name, res):
        """Adds the resource's level for help links generation."""

        def RecurseFix(res, level):
            res.level = level + 1
            for c in res.children.itervalues():
                RecurseFix(c, res.level)

        RecurseFix(res, self.level)
        resource.Resource.putChild(self, name, res)

    def render_GET(self, request):
        """Renders a HTTP GET at the http request level."""
        userAgent = request.requestHeaders.getRawHeaders(
            'user-agent', ['unknown'])[0]
        twlog.msg('Received request for %s from %s, id: %s' %
                  (request.uri, userAgent, id(request)))
        d = defer.maybeDeferred(lambda : self.content(request))
        def handle(data):
            if isinstance(data, unicode):
                data = data.encode("utf-8")
            request.setHeader("Access-Control-Allow-Origin", "*")
            if RequestArgToBool(request, 'as_text', False):
                request.setHeader("content-type", 'text/plain')
            else:
                request.setHeader("content-type", self.contentType)
                request.setHeader("content-disposition",
                                "attachment; filename=\"%s.json\"" % request.path)
            # Make sure we get fresh pages.
            if self.cache_seconds:
                now = datetime.datetime.utcnow()
                expires = now + datetime.timedelta(seconds=self.cache_seconds)
                request.setHeader("Expires",
                                expires.strftime("%a, %d %b %Y %H:%M:%S GMT"))
                request.setHeader("Pragma", "no-cache")
            return data
        d.addCallback(handle)
        def ok(data):
            twlog.msg('Finished processing request with id: %s' % id(request))
            request.write(data)
            request.finish()
        def fail(f):
            request.processingFailed(f)
            return None # processingFailed will log this for us
        d.addCallbacks(ok, fail)
        return server.NOT_DONE_YET

    @defer.deferredGenerator
    def content(self, request):
        """Renders the json dictionaries."""
        # Supported flags.
        select = request.args.get('select')
        as_text = RequestArgToBool(request, 'as_text', False)
        filter_out = RequestArgToBool(request, 'filter', as_text)
        compact = RequestArgToBool(request, 'compact', not as_text)
        callback = request.args.get('callback')

        # Implement filtering at global level and every child.
        if select is not None:
            del request.args['select']
            # Do not render self.asDict()!
            data = {}
            # Remove superfluous /
            select = [s.strip('/') for s in select]
            select.sort(cmp=lambda x,y: cmp(x.count('/'), y.count('/')),
                        reverse=True)
            for item in select:
                # Start back at root.
                node = data
                # Implementation similar to twisted.web.resource.getChildForRequest
                # but with a hacked up request.
                child = self
                prepath = request.prepath[:]
                postpath = request.postpath[:]
                request.postpath = filter(None, item.split('/'))
                while request.postpath and not child.isLeaf:
                    pathElement = request.postpath.pop(0)
                    node[pathElement] = {}
                    node = node[pathElement]
                    request.prepath.append(pathElement)
                    child = child.getChildWithDefault(pathElement, request)

                # some asDict methods return a Deferred, so handle that
                # properly
                if hasattr(child, 'asDict'):
                    wfd = defer.waitForDeferred(
                            defer.maybeDeferred(lambda :
                                child.asDict(request)))
                    yield wfd
                    child_dict = wfd.getResult()
                else:
                    child_dict = {
                        'error' : 'Not available',
                    }
                node.update(child_dict)

                request.prepath = prepath
                request.postpath = postpath
        else:
            wfd = defer.waitForDeferred(
                    defer.maybeDeferred(lambda :
                        self.asDict(request)))
            yield wfd
            data = wfd.getResult()

        if filter_out:
            data = FilterOut(data)
        if compact:
            data = json.dumps(data, sort_keys=True, separators=(',',':'))
        else:
            data = json.dumps(data, sort_keys=True, indent=2)
        if callback:
            # Only accept things that look like identifiers for now
            callback = callback[0]
            if re.match(r'^[_a-zA-Z$][_a-zA-Z$0-9.]*$', callback):
                data = '%s(%s);' % (callback, data)
        yield data

    @defer.deferredGenerator
    def asDict(self, request):
        """Generates the json dictionary.

        By default, renders every childs."""
        data = {}
        for name in self.children:
            child = self.getChildWithDefault(name, request)
            if isinstance(child, JsonResource):
                wfd = defer.waitForDeferred(
                        defer.maybeDeferred(lambda :
                            child.asDict(request)))
                yield wfd
                data[name] = wfd.getResult()
            # else silently pass over non-json resources.
        yield data


def ToHtml(text):
    """Convert a string in a wiki-style format into HTML."""
    indent = 0
    in_item = False
    output = []
    for line in text.splitlines(False):
        match = re.match(r'^( +)\- (.*)$', line)
        if match:
            if indent < len(match.group(1)):
                output.append('<ul>')
                indent = len(match.group(1))
            elif indent > len(match.group(1)):
                while indent > len(match.group(1)):
                    output.append('</ul>')
                    indent -= 2
            if in_item:
                # Close previous item
                output.append('</li>')
            output.append('<li>')
            in_item = True
            line = match.group(2)
        elif indent:
            if line.startswith((' ' * indent) + '  '):
                # List continuation
                line = line.strip()
            else:
                # List is done
                if in_item:
                    output.append('</li>')
                    in_item = False
                while indent > 0:
                    output.append('</ul>')
                    indent -= 2

        if line.startswith('/'):
            if not '?' in line:
                line_full = line + '?as_text=1'
            else:
                line_full = line + '&as_text=1'
            output.append('<a href="' + html.escape(line_full) + '">' +
                html.escape(line) + '</a>')
        else:
            output.append(html.escape(line).replace('  ', '&nbsp;&nbsp;'))
        if not in_item:
            output.append('<br>')

    if in_item:
        output.append('</li>')
    while indent > 0:
        output.append('</ul>')
        indent -= 2
    return '\n'.join(output)


class HelpResource(HtmlResource):
    def __init__(self, text, pageTitle, parent_node):
        HtmlResource.__init__(self)
        self.text = text
        self.pageTitle = pageTitle
        self.flags = getattr(parent_node, 'FLAGS', None) or FLAGS
        self.parent_level = parent_node.level
        self.parent_children = parent_node.children.keys()

    def content(self, request, cxt):
        cxt['level'] = self.parent_level
        cxt['text'] = ToHtml(self.text)
        cxt['children'] = [ n for n in self.parent_children if n != 'help' ]
        cxt['flags'] = ToHtml(self.flags)
        cxt['examples'] = ToHtml(EXAMPLES).replace(
                'href="/json',
                'href="%sjson' % (self.level * '../'))

        template = request.site.buildbot_service.templates.get_template("jsonhelp.html")
        return template.render(**cxt)

class BuilderPendingBuildsJsonResource(JsonResource):
    help = """Describe pending builds for a builder.
"""
    pageTitle = 'Builder'

    def __init__(self, status, builder_status):
        JsonResource.__init__(self, status)
        self.builder_status = builder_status

    def asDict(self, request):
        # buildbot.status.builder.BuilderStatus
        d = self.builder_status.getPendingBuildRequestStatuses()
        def to_dict(statuses):
            return defer.gatherResults(
                [ b.asDict_async() for b in statuses ])
        d.addCallback(to_dict)
        return d


class BuilderJsonResource(JsonResource):
    help = """Describe a single builder.
"""
    pageTitle = 'Builder'

    def __init__(self, status, builder_status):
        JsonResource.__init__(self, status)
        self.builder_status = builder_status
        self.putChild('builds', BuildsJsonResource(status, builder_status))
        self.putChild('slaves', BuilderSlavesJsonResources(status,
                                                           builder_status))
        self.putChild(
                'pendingBuilds',
                BuilderPendingBuildsJsonResource(status, builder_status))

    def asDict(self, request):
        # buildbot.status.builder.BuilderStatus
        return self.builder_status.asDict_async()


class BuildersJsonResource(JsonResource):
    help = """List of all the builders defined on a master.
"""
    pageTitle = 'Builders'

    def __init__(self, status):
        JsonResource.__init__(self, status)
        for builder_name in self.status.getBuilderNames():
            self.putChild(builder_name,
                          BuilderJsonResource(status,
                                              status.getBuilder(builder_name)))

    @defer.deferredGenerator
    def asDict(self, request):
        """Generates the json dictionary.

        By default, renders every builder. If 'trybots=1' is passed in
        the request, then it renders only the builders that were marked as
        trybots.
        """
        wfd = defer.waitForDeferred(
                defer.maybeDeferred(JsonResource.asDict, self, request))
        yield wfd
        data = wfd.getResult()
        trybots = request.args.get('trybots')
        if trybots:
          data = {name: builder for name, builder in data.iteritems()
                  if name in self.status.master.trybots}
        yield data


class BuilderSlavesJsonResources(JsonResource):
    help = """Describe the slaves attached to a single builder.
"""
    pageTitle = 'BuilderSlaves'

    def __init__(self, status, builder_status):
        JsonResource.__init__(self, status)
        self.builder_status = builder_status
        for slave_name in self.builder_status.slavenames:
            self.putChild(slave_name,
                          SlaveJsonResource(status,
                                            self.status.getSlave(slave_name)))


class BuildJsonResource(JsonResource):
    help = """Describe a single build.
"""
    pageTitle = 'Build'

    def __init__(self, status, build_status):
        JsonResource.__init__(self, status)
        self.build_status = build_status
        self.putChild('source_stamp',
                      SourceStampJsonResource(status,
                                              build_status.getSourceStamp()))
        self.putChild('steps', BuildStepsJsonResource(status, build_status))

    def asDict(self, request):
        return self.build_status.asDict()


class AllBuildsJsonResource(JsonResource):
    help = """All the builds that were run on a builder.
"""
    pageTitle = 'AllBuilds'

    def __init__(self, status, builder_status):
        JsonResource.__init__(self, status)
        self.builder_status = builder_status

    def getChild(self, path, request):
        # Dynamic childs.
        if isinstance(path, int) or _IS_INT.match(path):
            build_status = self.builder_status.getBuild(int(path))
            if build_status:
                # Don't cache BuildJsonResource; that would defeat the cache-ing
                # mechanism in place for BuildStatus objects (in BuilderStatus).
                return BuildJsonResource(self.status, build_status)
        return JsonResource.getChild(self, path, request)

    def asDict(self, request):
        results = {}
        # If max is too big, it'll trash the cache...
        max = int(RequestArg(request, 'max',
                             self.builder_status.buildCacheSize/2))
        for i in range(0, max):
            child = self.getChildWithDefault(-i, request)
            if not isinstance(child, BuildJsonResource):
                continue
            results[child.build_status.getNumber()] = child.asDict(request)
        return results


class BuildsJsonResource(AllBuildsJsonResource):
    help = """Builds that were run on a builder.
"""
    pageTitle = 'Builds'

    def __init__(self, status, builder_status):
        AllBuildsJsonResource.__init__(self, status, builder_status)
        self.putChild('_all', AllBuildsJsonResource(status, builder_status))

    def getChild(self, path, request):
        # Transparently redirects to _all if path is not ''.
        return self.children['_all'].getChildWithDefault(path, request)

    def asDict(self, request):
        # This would load all the pickles and is way too heavy, especially that
        # it would trash the cache:
        # self.children['builds'].asDict(request)
        # TODO(maruel) This list should also need to be cached but how?
        builds = dict([
            (int(file), None)
            for file in os.listdir(self.builder_status.basedir)
            if _IS_INT.match(file)
        ])
        return builds


class BuildStepJsonResource(JsonResource):
    help = """A single build step.
"""
    pageTitle = 'BuildStep'

    def __init__(self, status, build_step_status):
        # buildbot.status.buildstep.BuildStepStatus
        JsonResource.__init__(self, status)
        self.build_step_status = build_step_status
        # TODO self.putChild('logs', LogsJsonResource())

    def asDict(self, request):
        return self.build_step_status.asDict()


class BuildStepsJsonResource(JsonResource):
    help = """A list of build steps that occurred during a build.
"""
    pageTitle = 'BuildSteps'

    def __init__(self, status, build_status):
        JsonResource.__init__(self, status)
        self.build_status = build_status
        # The build steps are constantly changing until the build is done so
        # keep a reference to build_status instead

    def getChild(self, path, request):
        # Dynamic childs.
        build_step_status = None
        if isinstance(path, int) or _IS_INT.match(path):
            build_step_status = self.build_status.getSteps()[int(path)]
        else:
            steps_dict = dict([(step.getName(), step)
                               for step in self.build_status.getSteps()])
            build_step_status = steps_dict.get(path)
        if build_step_status:
            # Create it on-demand.
            child = BuildStepJsonResource(self.status, build_step_status)
            # Cache it.
            index = self.build_status.getSteps().index(build_step_status)
            self.putChild(str(index), child)
            self.putChild(build_step_status.getName(), child)
            return child
        return JsonResource.getChild(self, path, request)

    def asDict(self, request):
        # Only use the number and not the names!
        results = {}
        index = 0
        for step in self.build_status.getSteps():
            results[index] = step.asDict()
            index += 1
        return results


class ChangeJsonResource(JsonResource):
    help = """Describe a single change that originates from a change source.
"""
    pageTitle = 'Change'

    def __init__(self, status, change):
        # buildbot.changes.changes.Change
        JsonResource.__init__(self, status)
        self.change = change

    def asDict(self, request):
        return self.change.asDict()


class ChangesJsonResource(JsonResource):
    help = """List of changes.
"""
    pageTitle = 'Changes'

    def __init__(self, status, changes):
        JsonResource.__init__(self, status)
        for c in changes or []:
            # c.number can be None or clash another change if the change was
            # generated inside buildbot or if using multiple pollers.
            if c.number is not None and str(c.number) not in self.children:
                self.putChild(str(c.number), ChangeJsonResource(status, c))
            else:
                # Temporary hack since it creates information exposure.
                self.putChild(str(id(c)), ChangeJsonResource(status, c))

    def getChild(self, path, request):
        # Dynamic childs.
        if isinstance(path, int) or _IS_INT.match(path):
            change = self.status.master.getChange(int(path))
            number = str(change.number)
            child = ChangeJsonResource(self.status, change)
            self.putChild(number, child)
            return child
        return JsonResource.getChild(self, path, request)

    @defer.deferredGenerator
    def asDict(self, request):
        """Don't throw an exception when there is no child."""
        max = int(RequestArg(request, 'max', 100))
        d = self.status.master.db.changes.getRecentChanges(max)
        def reify(chdicts):
            return defer.gatherResults(
                [changes.Change.fromChdict(self.status.master, chdict)
                 for chdict in chdicts])
        d.addCallback(reify)
        wfd = defer.waitForDeferred(d)
        yield wfd
        chobjs = wfd.getResult()
        yield dict([(change.number, change.asDict()) for change in chobjs])


class ChangeSourcesJsonResource(JsonResource):
    help = """Describe a change source.
"""
    pageTitle = 'ChangeSources'

    def asDict(self, request):
        result = {}
        n = 0
        for c in self.status.getChangeSources():
            # buildbot.changes.changes.ChangeMaster
            change = {}
            change['description'] = c.describe()
            result[n] = change
            n += 1
        return result


class ProjectJsonResource(JsonResource):
    help = """Project-wide settings.
"""
    pageTitle = 'Project'

    def asDict(self, request):
        return self.status.asDict()


class SlaveJsonResource(JsonResource):
    help = """Describe a slave.
"""
    pageTitle = 'Slave'

    def __init__(self, status, slave_status):
        JsonResource.__init__(self, status)
        self.slave_status = slave_status
        self.name = self.slave_status.getName()
        self.builders = None

    def getBuilders(self):
        if self.builders is None:
            # Figure out all the builders to which it's attached
            self.builders = []
            for builderName in self.status.getBuilderNames():
                if self.name in self.status.getBuilder(builderName).slavenames:
                    self.builders.append(builderName)
        return self.builders

    def getSlaveBuildMap(self, buildcache, buildercache):
        for builderName in self.getBuilders():
            if builderName not in buildercache:
                buildercache.add(builderName)
                builder_status = self.status.getBuilder(builderName)

                buildnums = range(-1, -(builder_status.buildCacheSize - 1), -1)
                builds = builder_status.getBuilds(buildnums)

                for build_status in builds:
                    if not build_status or not build_status.isFinished():
                        # If not finished, it will appear in runningBuilds.
                        continue
                    slave = buildcache[build_status.getSlavename()]
                    slave.setdefault(builderName, []).append(
                            build_status.getNumber())
        return buildcache[self.name]

    def asDict(self, request):
        if not hasattr(request, 'custom_data'):
            request.custom_data = {}
        if 'buildcache' not in request.custom_data:
            # buildcache is used to cache build information across multiple
            # invocations of SlaveJsonResource. It should be set to an empty
            # collections.defaultdict(dict).
            request.custom_data['buildcache'] = collections.defaultdict(dict)

            # Tracks which builders have been stored in the buildcache.
            request.custom_data['buildercache'] = set()

        results = self.slave_status.asDict()
        # Enhance it by adding more information.
        results['builders'] = self.getSlaveBuildMap(
                request.custom_data['buildcache'],
                request.custom_data['buildercache'])
        return results


class SlavesJsonResource(JsonResource):
    help = """List the registered slaves.
"""
    pageTitle = 'Slaves'

    def __init__(self, status):
        JsonResource.__init__(self, status)
        for slave_name in status.getSlaveNames():
            self.putChild(slave_name,
                          SlaveJsonResource(status,
                                            status.getSlave(slave_name)))


class SourceStampJsonResource(JsonResource):
    help = """Describe the sources for a SourceStamp.
"""
    pageTitle = 'SourceStamp'

    def __init__(self, status, source_stamp):
        # buildbot.sourcestamp.SourceStamp
        JsonResource.__init__(self, status)
        self.source_stamp = source_stamp
        self.putChild('changes',
                      ChangesJsonResource(status, source_stamp.changes))
        # TODO(maruel): Should redirect to the patch's url instead.
        #if source_stamp.patch:
        #  self.putChild('patch', StaticHTML(source_stamp.path))

    def asDict(self, request):
        return self.source_stamp.asDict()

class MetricsJsonResource(JsonResource):
    help = """Master metrics.
"""
    title = "Metrics"

    def asDict(self, request):
        metrics = self.status.getMetrics()
        if metrics:
            return metrics.asDict()
        else:
            # Metrics are disabled
            return None


class BuildStateJsonResource(JsonResource):
    help = ('Holistic build state JSON endpoint.\n\n'
            'This endpoint is a fast (unless otherwise noted) and '
            'comprehensive source for full BuildBot state queries. Any '
            'augmentations to this endpoint MUST keep this in mind.')

    pageTitle = 'Build State JSON'

    # Keyword for 'completed_builds' to indicate that all cached builds should
    # be returned.
    CACHED = 'cached'

    # Specific build fields that will be stripped unless requested. These map
    # user-specified query parameters (lowercase) to
    # buildbot.status.Build.asDict keyword arguments.
    BUILD_FIELDS = ('blame', 'logs', 'sourceStamp', 'properties', 'steps',
                    'eta',)
    # Special build field to indicate all fields should be returned.
    BUILD_FIELDS_ALL = 'all'

    EXTRA_FLAGS = """\
  - builder
    - A builder name to explicitly include in the results. This can be supplied
      multiple times. If omitted, data for all builders will be returned.
      Globbing via asterisk is permitted (e.g., "*_rel")
  - current_builds
    - Controls whether the builder's current-running build data will be
      returned. By default, no current build data will be returned; setting
      current_builds=1 will enable this.
  - completed_builds
    - Controls whether the builder's completed build data will be returned. By
      default, no completed build data will be retured. Setting
      completed_builds=%(completed_builds_cached)s will return build data for
      all cached builds.  Setting it to a positive integer 'N' (e.g.,
      completed_builds=3) will cause data for the latest 'N' completed builds to
      be returned.
  - pending_builds
    - Controls whether the builder's pending build data will be
      returned. By default, no pending build data will be returned; setting
      pending_builds=1 will enable this.
  - build_field
    - The specific build fields to include. Collecting and packaging more fields
      will take more time. This can be supplied multiple times to request more
      than one field. If '%(build_fields_all)s' is supplied, all build fields
      will be included.  Available individual fields are: %(build_fields)s.
  - slaves
    - Controls whether the builder's slave data will be returned. By default, no
      slave build data will be returned; setting slaves=1 will enable this.
"""
    def __init__(self, status):
        JsonResource.__init__(self, status)
        context = {
                'completed_builds_cached': self.CACHED,
                'build_fields_all': self.BUILD_FIELDS_ALL,
                'build_fields': ', '.join(sorted([f.lower()
                                                  for f in self.BUILD_FIELDS])),
        }
        self.FLAGS = FLAGS + self.EXTRA_FLAGS % context

        self.putChild('project', ProjectJsonResource(status))

    @classmethod
    def _CountOrCachedRequestArg(cls, request, arg):
        value = RequestArg(request, arg, 0)
        if value == cls.CACHED:
            return value
        try:
            value = int(value)
        except ValueError:
            return 0
        return max(0, value)

    @defer.deferredGenerator
    def asDict(self, request):
        builders = request.args.get('builder', ())
        build_fields = request.args.get('build_field', ())
        current_builds = RequestArgToBool(request, 'current_builds', False)
        completed_builds = self._CountOrCachedRequestArg(request,
                                                         'completed_builds')
        pending_builds = RequestArgToBool(request, 'pending_builds', False)
        slaves = RequestArgToBool(request, 'slaves', False)

        builder_names = self.status.getBuilderNames()
        if builders:
            builder_regex = re.compile('|'.join(fnmatch.translate(b)
                                                for b in builders))
            builder_names = [b for b in builder_names
                             if builder_regex.match(b)]

        # Collect child endpoint data.
        wfd = defer.waitForDeferred(
                defer.maybeDeferred(JsonResource.asDict, self, request))
        yield wfd
        response = wfd.getResult()

        # Collect builder data.
        wfd = defer.waitForDeferred(
                defer.gatherResults(
                    [self._getBuilderData(self.status.getBuilder(builder_name),
                                          current_builds, completed_builds,
                                          pending_builds, build_fields)
                     for builder_name in builder_names]))
        yield wfd
        response['builders'] = wfd.getResult()

        # Add slave data.
        if slaves:
            response['slaves'] = self._getAllSlavesData()

        # Add timestamp and return.
        response['timestamp'] = now()
        response['accepting_builds'] = bool(
                self.status.master.botmaster.brd.running)
        yield response

    @defer.deferredGenerator
    def _getBuilderData(self, builder, current_builds, completed_builds,
                        pending_builds, build_fields):
        # Load the builder dictionary. We use the synchronous path, since the
        # asynchronous waits for pending builds to load. We handle that path
        # explicitly via the 'pending_builds' option.
        #
        # This also causes the cache to be updated with recent builds, so we
        # will call it first.
        response = builder.asDict()
        response['builderName'] = builder.getName()
        tasks = []

        # Get current/completed builds.
        if current_builds or completed_builds:
            tasks.append(
                    defer.maybeDeferred(self._loadBuildData, builder,
                                        current_builds, completed_builds,
                                        build_fields))

        # Get pending builds.
        if pending_builds:
            tasks.append(
                    self._loadPendingBuildData(builder))

        # Collect a set of build data dictionaries to combine.
        wfd = defer.waitForDeferred(
                defer.gatherResults(tasks))
        yield wfd
        build_data_entries = wfd.getResult()

        # Construct our build data from the various task entries.
        build_state = response.setdefault('buildState', {})
        for build_data_entry in build_data_entries:
            build_state.update(build_data_entry)
        yield response

    def _loadBuildData(self, builder, current_builds, completed_builds,
                       build_fields):
        build_state = {}
        builds = set()
        build_data_entries = []

        current_build_numbers = set(b.getNumber()
                                    for b in builder.currentBuilds)
        if current_builds:
            builds.update(current_build_numbers)
            build_data_entries.append(('current', current_build_numbers))

        if completed_builds:
            if completed_builds == self.CACHED:
                build_numbers = set(builder.buildCache.cache.keys())
                build_numbers.difference_update(current_build_numbers)
            else:
                build_numbers = []
                candidate = -1
                while len(build_numbers) < completed_builds:
                    build_number = builder._resolveBuildNumber(candidate)
                    if not build_number:
                        break

                    candidate -= 1
                    if build_number in current_build_numbers:
                        continue
                    build_numbers.append(build_number)
            builds.update(build_numbers)
            build_data_entries.append(('completed', build_numbers))

        # Determine which build fields to request.
        build_fields = [f.lower() for f in build_fields]
        build_field_kwargs = {}
        if self.BUILD_FIELDS_ALL not in build_fields:
            build_field_kwargs.update(dict(
                    (kwarg, kwarg.lower() in build_fields)
                    for kwarg in self.BUILD_FIELDS))

        # Load all builds referenced by 'builds'.
        builds = builder.getBuilds(builds)
        build_map = dict((build_dict['number'], build_dict)
                         for build_dict in [build.asDict(**build_field_kwargs)
                                            for build in builds
                                            if build])

        # Map the collected builds to their repective keys. This dictionary
        # takes the form: Build# => BuildDict.
        for key, build_numbers in build_data_entries:
            build_state[key] = dict((number, build_map.get(number, {}))
                                    for number in build_numbers)
        return build_state

    def _loadPendingBuildData(self, builder):
        d = builder.getPendingBuildRequestStatuses()

        def cb_load_status(statuses):
            statuses.sort(key=lambda s: s.getSubmitTime())
            return defer.gatherResults([status.asDict_async()
                                        for status in statuses])
        d.addCallback(cb_load_status)

        def cb_collect(status_dicts):
            return {
                    'pending': status_dicts,
            }
        d.addCallback(cb_collect)

        return d

    def _getAllSlavesData(self):
        return dict((slavename, self._getSlaveData(slavename))
                    for slavename in self.status.getSlaveNames())

    def _getSlaveData(self, slavename):
        slave = self.status.getSlave(slavename)
        return {
                'host': slave.getHost(),
                'connected': slave.isConnected(),
                'connect_times': slave.getConnectTimes(),
                'last_message_received': slave.lastMessageReceived(),
        }


class MasterClockResource(JsonResource):
    help = """Show current time, boot time and uptime of the master."""
    pageTitle = 'MasterClock'

    def asDict(self, _request):
        # The only reason we include local time is because the buildbot UI
        # displays it. Any computations on time should be done in UTC.
        current_timeblock = get_timeblock()

        return {
            'server_started': SERVER_STARTED,
            'current': current_timeblock,
            'server_uptime': (
                    current_timeblock['utc_ts'] - SERVER_STARTED['utc_ts'])
        }


class AcceptingBuildsResource(JsonResource):
    help = """Show whether the master is scheduling new builds."""
    pageTitle = 'AcceptingBuilds'

    def asDict(self, _request):
        return {
            'accepting_builds': bool(self.status.master.botmaster.brd.running),
        }


class VarzResource(JsonResource):
    help = 'Minimal set of metrics that are scraped frequently for monitoring.'
    pageTitle = 'Varz'

    @defer.deferredGenerator
    def asDict(self, request):
        builders = {}
        for builder_name in self.status.getBuilderNames():
            builder = self.status.getBuilder(builder_name)
            slaves = builder.getSlaves()
            builders[builder_name] = {
                'connected_slaves': sum(1 for x in slaves if x.connected),
                'current_builds': len(builder.getCurrentBuilds()),
                'pending_builds': 0,
                'state': builder.currentBigState,
                'total_slaves': len(slaves),
            }

        # Get pending build requests directly from the db for all builders at
        # once.
        d = self.status.master.db.buildrequests.getBuildRequests(claimed=False)
        def pending_builds_callback(brdicts):
            for brdict in brdicts:
                if brdict['buildername'] in builders:
                    builders[brdict['buildername']]['pending_builds'] += 1
        d.addCallback(pending_builds_callback)
        yield defer.waitForDeferred(d)

        yield {
            'accepting_builds': bool(self.status.master.botmaster.brd.running),
            'builders': builders,
            'server_uptime': (
                    get_timeblock()['utc_ts'] - SERVER_STARTED['utc_ts']),
        }


class JsonStatusResource(JsonResource):
    """Retrieves all json data."""
    help = """JSON status

Root page to give a fair amount of information in the current buildbot master
status. You may want to use a child instead to reduce the load on the server.

For help on any sub directory, use url /child/help
"""
    pageTitle = 'Buildbot JSON'

    def __init__(self, status):
        JsonResource.__init__(self, status)
        self.level = 1
        self.putChild('builders', BuildersJsonResource(status))
        self.putChild('changes', ChangesJsonResource(status, None))
        self.putChild('change_sources', ChangeSourcesJsonResource(status))
        self.putChild('project', ProjectJsonResource(status))
        self.putChild('slaves', SlavesJsonResource(status))
        self.putChild('metrics', MetricsJsonResource(status))
        self.putChild('clock', MasterClockResource(status))
        self.putChild('accepting_builds', AcceptingBuildsResource(status))
        self.putChild('buildstate', BuildStateJsonResource(status))
        self.putChild('varz', VarzResource(status))
        # This needs to be called before the first HelpResource().body call.
        self.hackExamples()

    def content(self, request):
        result = JsonResource.content(self, request)
        # This is done to hook the downloaded filename.
        request.path = 'buildbot'
        return result

    def hackExamples(self):
        global EXAMPLES
        # Find the first builder with a previous build or select the last one.
        builder = None
        for b in self.status.getBuilderNames():
            builder = self.status.getBuilder(b)
            if builder.getBuild(-1):
                break
        if not builder:
            return
        EXAMPLES = EXAMPLES.replace('<A_BUILDER>', builder.getName())
        build = builder.getBuild(-1)
        if build:
            EXAMPLES = EXAMPLES.replace('<A_BUILD>', str(build.getNumber()))
        if builder.slavenames:
            EXAMPLES = EXAMPLES.replace('<A_SLAVE>', builder.slavenames[0])

# vim: set ts=4 sts=4 sw=4 et:
