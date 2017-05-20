// Copyright 2016 Google Inc. All rights reserved
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "affinity.h"

#include "flags.h"
#include "log.h"

#ifdef __linux__

#include <sched.h>

void SetAffinityForSingleThread() {
  cpu_set_t cs;
  CPU_ZERO(&cs);
  int n = g_flags.num_cpus / 2;
  CPU_SET(n, &cs);
  if (n > 1)
    CPU_SET(n + 1, &cs);
  if (sched_setaffinity(0, sizeof(cs), &cs) < 0)
    WARN("sched_setaffinity: %s", strerror(errno));
}

void SetAffinityForMultiThread() {
  cpu_set_t cs;
  CPU_ZERO(&cs);
  for (int i = 0; i < g_flags.num_cpus; i++) {
    CPU_SET(i, &cs);
  }
  if (sched_setaffinity(0, sizeof(cs), &cs) < 0)
    WARN("sched_setaffinity: %s", strerror(errno));
}

#else

void SetAffinityForSingleThread() {}
void SetAffinityForMultiThread() {}

#endif
