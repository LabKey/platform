package org.labkey.api.assay.dilution;

import org.labkey.api.exp.PropertyType;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 5/8/13
*/
public enum SampleProperty
{
    InitialDilution(PropertyType.DOUBLE, false),
    SampleId(PropertyType.STRING, false),
    SampleDescription(PropertyType.STRING, false),
    Factor(PropertyType.DOUBLE, false),
    Method(PropertyType.STRING, false),
    Slope(PropertyType.DOUBLE, false),
    EndpointsOptional(PropertyType.BOOLEAN, false),
    ReverseDilutionDirection(PropertyType.BOOLEAN, true),
    FitError(PropertyType.DOUBLE, false);

    private PropertyType _type;
    private boolean _isTemplateProperty;

    SampleProperty(PropertyType type, boolean setInTemplateEditor)
    {
        _type = type;
        _isTemplateProperty = setInTemplateEditor;
    }

    public PropertyType getType()
    {
        return _type;
    }

    public boolean isTemplateProperty()
    {
        return _isTemplateProperty;
    }
}
