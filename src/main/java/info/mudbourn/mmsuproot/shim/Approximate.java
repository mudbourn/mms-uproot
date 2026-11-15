package info.mudbourn.mmsuproot.shim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a shim method that answers a 1.21.11 call with less than the old call did, on purpose: the value says what is given up. The
 * offline scan lists every mod call that lands on one as a lossy mapping, which a compiled body that does real work would otherwise hide.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Approximate {
	String value();
}
