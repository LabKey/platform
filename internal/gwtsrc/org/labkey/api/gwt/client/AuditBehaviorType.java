package org.labkey.api.gwt.client;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 10/17/12
 */
public enum AuditBehaviorType
{
    NONE("None"),
    DETAILED("Detailed"),
    SUMMARY("Summary");

    private String _label;

    AuditBehaviorType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }
}
