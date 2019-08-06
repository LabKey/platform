package org.labkey.api.gwt.client;

/**
 * Enum to set a type of lock on a column, since binary values of either fully locked or not locked is not sufficient to handle
 * combined cases of various domain designs, esp. when a column is partially locked.
**/
public enum LockedPropertyType
{
    FullyLocked, // can't change any properties
    PartiallyLocked, // can't change name and type, for example, but can change other properties
    NotLocked // not locked, can change all properties
}