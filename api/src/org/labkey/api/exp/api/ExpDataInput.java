package org.labkey.api.exp.api;

import org.labkey.api.exp.PropertyDescriptor;

public interface ExpDataInput
{
    ExpData getData();
    ExpProtocolApplication getTargetApplication();
    PropertyDescriptor getPropertyDescriptor();
}
