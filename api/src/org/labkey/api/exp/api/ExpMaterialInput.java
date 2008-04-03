package org.labkey.api.exp.api;

import org.labkey.api.exp.PropertyDescriptor;

public interface ExpMaterialInput
{
    ExpMaterial getMaterial();
    ExpProtocolApplication getTargetApplication();
    PropertyDescriptor getPropertyDescriptor();
}
