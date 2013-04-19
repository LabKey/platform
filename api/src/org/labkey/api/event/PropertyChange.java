package org.labkey.api.event;

/**
 * Represents a property change similar to java.beans.PropertyChangeEvent but uses an Enum for the property type.
 *
 * User: kevink
 * Date: 4/17/13
 */
public interface PropertyChange<P extends Enum<P>, V>
{
    /**
     * The Property that has changed or null if more than one property has changed.
     * @return The changed Property.
     */
    public P getProperty();

    /**
     * The previous version of the value or null if more than one property has changed.
     * @return
     */
    public V getOldValue();

    /**
     * The new version of the value or null if more than one property has changed.
     * @return
     */
    public V getNewValue();
}
