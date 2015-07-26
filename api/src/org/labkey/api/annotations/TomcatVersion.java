package org.labkey.api.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by adam on 7/25/2015.
 *
 * Used to mark code that is specific to a particular version of Apache Tomcat, for example, hacks that allow JSPs compiled
 * with one version of Tomcat to run in a different version. Re-evaluate these when new Tomcat versions are supported or
 * deprecated. Search for text "@TomcatVersion" to find all usages.
 */

@Retention(RetentionPolicy.SOURCE)
public @interface TomcatVersion
{
}
