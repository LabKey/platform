package org.labkey.api.gwt.client;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Enum to set a type of lock on a column, since binary values of either fully locked or not locked is not sufficient to handle
 * combined cases of various domain designs, esp. when a column is partially locked.
**/
public enum LockedPropertyType implements IsSerializable
{
    FullyLocked("Fully Locked"), // can't change any properties
    PartiallyLocked("Partially Locked"), // can't change name and type, for example, but can change other properties
    NotLocked("Not Locked"); // not locked, can change all properties

    LockedPropertyType() {}

    private String _label;

    LockedPropertyType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }
}