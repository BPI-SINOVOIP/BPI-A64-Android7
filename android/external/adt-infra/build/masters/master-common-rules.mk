# -*- makefile -*-
# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This should be included by a makefile which lives in a buildmaster/buildslave
# directory (next to the buildbot.tac file). That including makefile *must*
# define MASTERPATH.

# The 'start' and 'stop' targets start and stop the buildbot master.
# The 'reconfig' target will tell a buildmaster to reload its config file.

# Note that a relative PYTHONPATH entry is relative to the current directory.

# Confirm that MASTERPATH has been defined.
ifeq ($(MASTERPATH),)
  $(error MASTERPATH not defined.)
endif

# Use the puppet-managed infra-python CIPD deployment (which all masters have).
INFRA_RUNPY = /opt/infra-python/run.py

# Get the current host's short hostname.  We may use this in Makefiles that
# include this file.
SHORT_HOSTNAME := $(shell hostname -s)
CURRENT_DIR = $(shell pwd)

printstep:
ifndef NO_REVISION_AUDIT
	@echo "**  `python -c 'import datetime; print datetime.datetime.utcnow().isoformat() + "Z"'`	make $(MAKECMDGOALS)" >> actions.log
	@pstree --show-parents $$$$ --ascii --arguments --show-pids >> actions.log
endif

notify:
	@if (hostname -f | grep -q '^master.*\.chromium\.org'); then \
		/bin/echo ; \
		/bin/echo -e "\033[1;31m***"; \
		/bin/echo "Are you manually restarting a master? This master is most likely"; \
		/bin/echo "being managed by master manager. Check out 'Issuing a restart' at"; \
		/bin/echo -e "\033[1;34mgo/master-manager\033[1;31m for more details."; \
		/bin/echo -e "***\033[0m"; \
		/bin/echo ; \
	fi

ifeq ($(BUILDBOT_PATH),$(BUILDBOT8_PATH))
start: notify printstep bootstrap
else
start: notify printstep
endif
	@echo 'Now running Buildbot master.'
	PYTHONPATH=$(PYTHONPATH) python $(SCRIPTS_DIR)/common/twistd --no_save -y buildbot.tac

ifeq ($(BUILDBOT_PATH),$(BUILDBOT8_PATH))
start-prof: bootstrap
else
start-prof:
endif
	TWISTD_PROFILE=1 PYTHONPATH=$(PYTHONPATH) python $(SCRIPTS_DIR)/common/twistd --no_save -y buildbot.tac

stop: notify printstep
ifndef NO_REVISION_AUDIT
	@($(INFRA_RUNPY) infra.tools.send_monitoring_event \
                   --service-event-type=STOP \
                   --event-mon-run-type=prod \
                   --event-mon-service-name \
                   buildbot/master/$(MASTERPATH) \
   || echo 'Running send_monitoring_event failed, skipping sending events' \
  ) 2>&1 | tee -a actions.log
endif

	if `test -f twistd.pid`; then kill -TERM -$$(ps h -o pgid= $$(cat twistd.pid) | awk '{print $$1}'); fi;

kill: notify printstep
	if `test -f twistd.pid`; then kill -KILL -$$(ps h -o pgid= $$(cat twistd.pid) | awk '{print $$1}'); fi;

reconfig: printstep
	kill -HUP `cat twistd.pid`

no-new-builds: notify printstep
	kill -USR1 `cat twistd.pid`

log:
	tail -F twistd.log

exceptions:
# Searches for exception in the last 11 log files.
	grep -A 10 "exception caught here" twistd.log twistd.log.?

last-restart:
	@if `test -f twistd.pid`; then stat -c %y `readlink -f twistd.pid` | \
	    cut -d "." -f1; fi;
	@ls -t -1 twistd.log* | while read f; do tac $$f | grep -m 1 \
	    "Creating BuildMaster"; done | head -n 1

wait:
	while `test -f twistd.pid`; do sleep 1; done;

restart: notify stop wait start log

restart-prof: stop wait start-prof log

# This target is only known to work on 0.8.x masters.
upgrade: printstep
	@[ -e '.dbconfig' ] || [ -e 'state.sqlite' ] || \
	PYTHONPATH=$(PYTHONPATH) python buildbot upgrade-master .

# This target is only known to be useful on 0.8.x masters.
bootstrap: printstep
	@[ -e '.dbconfig' ] || [ -e 'state.sqlite' ] || \
	PYTHONPATH=$(PYTHONPATH) python $(SCRIPTS_DIR)/tools/state_create.py \
	--restore --db='state.sqlite' --txt '../state-template.txt'

setup:
	@echo export PYTHONPATH=$(PYTHONPATH)
