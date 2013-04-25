package org.labkey.di;

import org.labkey.api.data.ParameterDescription;
import org.labkey.api.pipeline.RecordedAction;

/**
* Created with IntelliJ IDEA.
* User: matthew
* Date: 4/22/13
* Time: 11:43 AM
*/
public interface VariableDescription extends ParameterDescription
{
    public RecordedAction.ParameterType getParameterType();
}
