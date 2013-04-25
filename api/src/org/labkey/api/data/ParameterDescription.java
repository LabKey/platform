package org.labkey.api.data;

/**
* Created with IntelliJ IDEA.
* User: matthew
* Date: 4/24/13
* Time: 9:59 AM
*/

public interface ParameterDescription
{
    String getName();
    String getURI();
    JdbcType getType();
}
