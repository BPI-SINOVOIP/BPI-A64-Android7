import groovy.transform.InheritConstructors

class Base<T> {
  def Base(T x) {}
}

@InheritConstructors
class Inheritor extends Base<Date> {

}

Inheritor i = new Inheritor(new Date())
Inheritor i2 = new Inheritor<warning descr="Constructor 'Inheritor' in 'Inheritor' cannot be applied to '(java.lang.Integer)'">(2)</warning>