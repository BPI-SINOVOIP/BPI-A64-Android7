import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 06.08.11
 * Time: 14:56
 * To change this template use File | Settings | File Templates.
 */

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.LOCAL_VARIABLE})
public @interface A2 {
}
