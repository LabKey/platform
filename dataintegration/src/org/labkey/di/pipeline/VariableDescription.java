package org.labkey.di.pipeline;

import org.labkey.api.data.JdbcType;

/**
* Created with IntelliJ IDEA.
* User: matthew
* Date: 4/22/13
* Time: 11:43 AM
* To change this template use File | Settings | File Templates.
*/
public interface VariableDescription
{
    String getName();
    String getURI();
    JdbcType getJdbcType();
}
