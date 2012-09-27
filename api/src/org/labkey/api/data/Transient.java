package org.labkey.api.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meant to have same semantics as javax.persistence.Transient without needing jpa2.jar
 * See BeanObjectFactory
 */
@Target(value={ElementType.METHOD, ElementType.FIELD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Transient
{
}
