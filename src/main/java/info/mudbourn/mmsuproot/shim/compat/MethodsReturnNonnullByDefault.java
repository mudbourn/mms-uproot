package info.mudbourn.mmsuproot.shim.compat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stands in for Mojang's annotation of the same name, which the logging library no longer ships: the methods of the annotated package or
 * class return no null unless marked. It only informs tools; nothing reads it at run time.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface MethodsReturnNonnullByDefault {
}
