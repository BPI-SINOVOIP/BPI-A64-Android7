/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import java.lang.Object;

class Buggy extends Object implements BuggyInterface {
  public void getA(){}
  public void getB(){}
  public void getC(){}
  public void getD(){}
  public void getE(){}
  public void getF(){}
  public void getA1(){}
  public void getA2(){}
  public void getA3(){}
  public void getA4(){}
  public void getA5(){}
  public void getA6(){}
  public void getA7(){}
  public void getA8(){}
}