/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

/**
 * @author irengrig
 */
public interface WireEventsListener {
  void setWireStartsNumber(int row, final Integer[] number);
  void wireStarts(int row);
  void wireEnds(int row);
  void setEnds(int row, int[] commitEnds);
  //void addWireEvent(int row, int[] branched);
  void addStartToEvent(int row, int parentRow, int wireNumber);
  void parentWireEnds(int row, int parentRow);
}
