package org.labkey.api.gwt.client;

/**
 * User: cnathe
 * Date: 3/18/14
 */
public enum DefaultScaleType
{
    LINEAR("Linear"),
    LOG("Log");

    private String _label;

    DefaultScaleType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }
}
