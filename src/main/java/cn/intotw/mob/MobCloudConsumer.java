package cn.intotw.mob;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface MobCloudConsumer {
    String feignClientPrefix() default "";
    String requestMappingPrefix() default "";
}