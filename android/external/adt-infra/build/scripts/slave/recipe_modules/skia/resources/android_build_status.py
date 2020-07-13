"""
Script to query the internal Android Build APIs to determine if Skia built
successfully.

The script returns the following exit codes depending on the state of the build
that contains the provided git_revision for a given target...

  exit 0: the build completed successfully
       1: the build failed and we suspect it to be a result of a Skia commit
       2: the build failed but the state of the android tree is suspect
"""

import argparse
import httplib2
import os
import sys
import time

from apiclient import discovery
from apiclient import errors
from oauth2client import file
from oauth2client import client
from oauth2client import tools
from oauth2client import gce

def local_authenticate(flags):
  """Authenticate to the service using 3-legged OAuth flow for local testing"""
  # CLIENT_SECRET is name of a file containing the OAuth 2.0 information for
  # this application, including client_id and client_secret. You can see the
  # Client ID and Client secret on the APIs page in the Cloud Console:
  # <https://cloud.google.com/console#/project/287816926224/apiui>
  CLIENT_SECRET = os.path.join(os.path.dirname(__file__), 'client_secrets.json')

  # Set up a Flow object to be used for authentication.
  # Add one or more of the following scopes. PLEASE ONLY ADD THE SCOPES YOU
  # NEED. For more information on using scopes please see
  # <https://developers.google.com/+/best-practices>.
  FLOW = client.flow_from_clientsecrets(CLIENT_SECRET,
      scope=['https://www.googleapis.com/auth/androidbuild.internal'],
      message=tools.message_if_missing(CLIENT_SECRET))

  # If the credentials don't exist or are invalid run through the native client
  # flow. The Storage object will ensure that if successful the good
  # credentials will get written back to the file.
  storage = file.Storage('credentials.json')
  credentials = storage.get()
  if not credentials or credentials.invalid:
    credentials = tools.run_flow(FLOW, storage, flags)
  return credentials


def gce_authenticate():
  """Authenticate to the build service using the GCE instances credentials"""
  return gce.AppAssertionCredentials(
      scope=['https://www.googleapis.com/auth/androidbuild.internal'])


def query_for_build(service, target, build_id):
  """Query Android Build Service for the state of a specific build"""
  try:
    return service.build().get(target=target, buildId=build_id).execute()
  except errors.HttpError as error:
    print 'HTTP Error while attempting to query the build status.'
    print error
    return None


def query_for_builds(service, branch, target):
  """Query Android Build Service for a list of all the recent builds of the
     provided target in the specified branch. This listing also includes the git
     revisions that compose each build"""
  try:
    print 'Querying Android Build APIs for recent builds of {} on {}'.format(target, branch)
    return service.build().list(buildType='submitted', branch=branch,
                                target=target, extraFields='changeInfo',
                                maxResults='40').execute()
  except errors.HttpError as error:
    print 'HTTP Error while attempting to query the build status.'
    print error
    return None


def query_for_build_status(service, branch, target, starting_build_id):
  """Query Android Build Service for the status of the 4 builds in the target
     branch whose build IDs are >= to the provided build ID"""
  try:
    print ('Querying Android Build APIs for builds of {} on {} starting at'
           ' buildID {}').format(target, branch, starting_build_id)
    return service.build().list(buildType='submitted',
                                branch=branch, target=target, maxResults='4',
                                startBuildId=starting_build_id).execute()
  except errors.HttpError as error:
    print 'HTTP Error while attempting to query the build status.'
    print error
    return None


def query_for_completed_build(service, branch, target):
  """Query Android Build Service for the status of the 4 builds in the target
     branch whose build IDs are >= to the provided build ID"""
  try:
    print ('Querying Android Build APIs for last completed build of {}'
           ' on {}').format(target, branch)
    result = service.build().list(buildType='submitted',
                                  branch=branch, target=target, maxResults='1',
                                  buildAttemptStatus='complete').execute()
    return result['builds'][0]
  except errors.HttpError as error:
    print 'HTTP Error while attempting to query the build status.'
    print error
    return None


def find_build(service, branch, target, git_revision):
  """Find the Android build with the branch/target combo that contains the
     requested git revision"""
  build_list = query_for_builds(service, branch, target)
  if build_list is None:
    return None
  for build in build_list['builds']:
    # short circuit this iteration if the query does not return any changes
    if 'changes' not in build:
      continue
    for change in build['changes']:
      if change['latestRevision'] == git_revision:
        print 'Build ID found. ID: {}'.format(build['buildId'])
        return build
  return None


def find_completed_build(service, branch, target, git_revision):
  """Find the completed Android build with the branch/target combo that contains
     the requested git revision. If the found build is not complete this
     function will block until it has completed or 30 minutes has expired."""
  build = None
  for i in range(15):
    build = find_build(service, branch, target, git_revision)
    if build is not None:
      break
    print ('Failed to find a buildID containing the provided git_revision.'
           ' Retrying...')
    time.sleep(120)

  # If unable to find the buildID then quit
  if build is None:
    sys.exit('Failed to find a buildID containing the provided git_revision'
             ' after multiple attempts.')

  # Wait until the build completes or ends in error
  build_id = build['buildId']
  pending_build_count = 0
  while build['buildAttemptStatus'] not in ['complete', 'error']:
    print ('Current build status is {}. Waiting for'
           ' build to complete...').format(build['buildAttemptStatus'])
    time.sleep(120)

    # wait 40 minutes for the build server to queue the build before exiting
    if build['buildAttemptStatus'] in ['pending']:
      pending_build_count += 1
      if pending_build_count > 20:
        print ('WARNING: The android build server has yet to queue this build'
               ' and there is a high likelyhood that it will not complete'
               ' in a reasonable timeframe.')
        sys.exit(2)

    for i in range(2):
      build = query_for_build(service, target, build_id)
      if build is not None:
        break;
      print 'Failed to find a valid build status. Retrying...'
      time.sleep(30)

    if build is None:
      sys.exit('Unable to query the status of the build.')
  return build


def find_builds_with_status(service, branch, target, starting_build_id):
  """Find Android builds (and their status) in the target branch whose build IDs
     are >= to the provided build ID."""
  build_list = None
  for i in range(2):
    build_list = query_for_build_status(service, branch, target, starting_build_id)
    if build_list is not None:
      break;
    print 'Failed to find a buildList. Retrying...'
    time.sleep(120)
  
  # If unable to find the buildID then quit
  if build_list is None:
    sys.exit('Failed to find a buildList after multiple attempts.')
  return build_list


def main(argv):
  # Parse the command-line flags.
  parser = argparse.ArgumentParser(description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter,
      parents=[tools.argparser])
  parser.add_argument('target', help='target device for the build')
  parser.add_argument('git_revision', help='Git revision in the form of a SHA')
  parser.add_argument('--local', dest='local', action='store_const',
                      const=True, default=False,
                      help='Authenticate to the build service locally')

  flags = parser.parse_args(argv[1:])

  # check to see if we are building locally (e.g. --local flag)
  credentials = local_authenticate(flags) if flags.local else gce_authenticate()

  # Create an httplib2.Http object to handle our HTTP requests and authorize it
  # with our good Credentials.
  http = credentials.authorize(httplib2.Http())

  # Construct the service object for the interacting with the BigQuery API.
  # see https://www.googleapis.com/discovery/v1/apis/androidbuildinternal/v2beta1/rest
  service = discovery.build('androidbuildinternal', 'v2beta1', http=http)

  # Find the Android Build ID in master-skia that contains the request git revision
  build = find_completed_build(service, 'git_master-skia', flags.target, flags.git_revision)
  if build['successful'] is True:
    print 'The build completed successfully!'
    sys.exit(0)

  # If we have a failure we first look at the builds in master whose buildIDs
  # are as close to  the buildID (without going over) from master-skia.
  # If the selected master builds have completed successfully, then we have a
  # high confidence level that a Skia change is the result of the breakage,
  # otherwise we are in an undetermined state
  unknown_build_state = False
  completed_build_found = False
  master_build_list = find_builds_with_status(service, 'git_master', 
                                              flags.target, build['buildId'])
  for master_build in master_build_list['builds']:
    if master_build['buildAttemptStatus'] in ['complete', 'error']:
      completed_build_found = True
      if not master_build['successful']:
        unknown_build_state = True
        break

  # if we haven't found a completed build in that set then lets look for the
  # most recent build that has completed
  if unknown_build_state == False and completed_build_found == False:
    print 'Checking the status of the last successful build in master...'
    master_build = query_for_completed_build(service, 'git_master', flags.target)
    if master_build and not master_build['successful']:
        unknown_build_state = False

  # Print links to both the master-skia build breakage and android build pages
  print '********************************************************************************'
  print 'Links to the broken master-skia build and corresponding master changes'
  print (' master-skia: https://android-build-uber.corp.google.com/builds.html'
         '?branch=git_master-skia&lower_limit={0}&upper_limit={0}').format(build['buildId'])
  print (' master: https://android-build-uber.corp.google.com/builds.html'
         '?branch=git_master&upper_limit={0}').format(build['buildId'])
  print '********************************************************************************'

  if unknown_build_state:
    print ('WARNING: The master build is in a broken/unknown state so there is'
           ' a high likelyhood that the Skia build is broken as a result')
    sys.exit(2)
  else:
    sys.exit('ERROR: The skia build is broken but it appears that the'
             ' corresponding master builds are green')


if __name__ == '__main__':
  main(sys.argv)
