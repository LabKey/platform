package org.labkey.specimen.report;

import org.labkey.api.query.FieldKey;

public class SpecimenTypeBeanProperty
{
    private final FieldKey _typeKey;
    private final String _beanProperty;
    private final SpecimenTypeLevel _level;

    public SpecimenTypeBeanProperty(FieldKey typeKey, String beanProperty, SpecimenTypeLevel level)
    {
        _typeKey = typeKey;
        _beanProperty = beanProperty;
        _level = level;
    }

    public FieldKey getTypeKey()
    {
        return _typeKey;
    }

    public String getBeanProperty()
    {
        return _beanProperty;
    }

    public SpecimenTypeLevel getLevel()
    {
        return _level;
    }
}
