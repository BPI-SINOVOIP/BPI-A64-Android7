/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.IMerger;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

import java.io.File;
import java.util.List;

public class RecordOnlyMergerFactory extends ChangeListsMergerFactory {
  private final boolean myUndo;

  public RecordOnlyMergerFactory(final List<CommittedChangeList> changeListsList, final boolean isUndo) {
    super(changeListsList);
    myUndo = isUndo;
  }

  public IMerger createMerger(final SvnVcs vcs,
                              final File target,
                              final UpdateEventHandler handler,
                              final SVNURL currentBranchUrl,
                              String branchName) {
    return new Merger(vcs, myChangeListsList, target, handler, currentBranchUrl, branchName) {
      @Override
      protected SVNRevisionRange createRange() {
        if (myUndo) {
            return new SVNRevisionRange(SVNRevision.create(myLatestProcessed.getNumber()), SVNRevision.create(myLatestProcessed.getNumber() - 1));
        }
        return super.createRange();
      }

      @Override
      protected boolean isRecordOnly() {
        return true;
      }
    };
  }
}
