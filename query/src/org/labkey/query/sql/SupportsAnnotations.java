package org.labkey.query.sql;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-05-24
 * Time: 2:40 PM
 */
public interface SupportsAnnotations
{
    void setAnnotations(Map<String,Object> a);
    Map<String,Object> getAnnotations();
}
