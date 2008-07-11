package org.labkey.api.action;

import org.springframework.validation.BindException;
import org.springframework.beans.PropertyValues;

public interface HasBindParameters
{
    public BindException bindParameters(PropertyValues m);
}
