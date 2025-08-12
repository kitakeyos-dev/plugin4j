package me.kitakeyos.plugin.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Plugin {
    String name();

    String version();

    String description() default "";

    String author() default "";

    String[] dependencies() default {};

    String apiVersion() default "1.0";
}
