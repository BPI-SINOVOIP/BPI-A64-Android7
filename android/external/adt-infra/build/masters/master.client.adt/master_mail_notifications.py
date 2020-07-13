# This file is used to define mail notifier for this master.

import os

from buildbot.status import mail, builder
from twisted.python import log as twlog

BUILD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                                         os.pardir)

def emailMessage(mode, name, build, results, master_status):
  """Generate a specalized buildbot mail message for new emulator failures and
     return a tuple of message text and type."""
  text = 'The Android Emulator bot found a new failure:\n'
  text += 'link: %s\n' % master_status.getURLForThing(build)
  text += 'builder: %s\n' % name
  text += 'buildslave: %s\n' % build.getSlavename()
  text += 'build reason: %s\n\n' % build.getReason()

  result_text = build.getText()
  if result_text:
    text += 'Failed steps: %s\n' % '\n'.join(result_text[1:][::2])

  #Include name of failure tests
  text += '\nFailed Tests:\n'
  for logf in build.getLogs():
    logName = logf.getName()
    logStatus,_ = logf.getStep().getResults()
    if logStatus == builder.FAILURE and ':' in logName:
      text += '%s\n' % logName

  text += '\n'
  ss = build.getSourceStamp()
  if ss:
    def getRevStr(props):
      return 'emulator: %s, mnc_revision: %s, lmp_revision: %s' % (props.getProperty('emu_revision', 'None'),
                                                                   props.getProperty('mnc_revision', 'None'),
                                                                   props.getProperty('lmp_revision', 'None'))
    failing_props = build.getProperties()
    previous_props = build.getPreviousBuild().getProperties()

    text += 'Failing build revisions: %s\n' % getRevStr(failing_props)
    #text += 'Previous passing revisions: %s\n\n' % getRevStr(previous_props)

  subject = 'Emulator build/test failure on %s' % name
  return { 'subject': subject, 'body': text, 'type': 'plain' }

def AddMailNotifier(BuildmasterConfig):
  try:
    with open(os.path.join(BUILD_DIR, 'site_config', '.mail_password')) as f:
      p = f.read()
    BuildmasterConfig['status'] = []
    BuildmasterConfig['status'].extend([
      mail.MailNotifier(
        fromaddr='adtinfrastructure@gmail.com',
        mode='failing',
        messageFormatter=emailMessage,
        extraRecipients=['adtinfrastructure@gmail.com',
                         'emu-build-police-pst@grotations.appspotmail.com'],
        smtpServer = 'smtp.gmail.com',
        smtpUser = 'adtinfrastructure',
        smtpPassword = p,
        smtpPort = 587,
       ),
     ])
  except Exception as ex:
    twlog.msg('Warning: Not adding MailNotifier. Could not read password file.')
