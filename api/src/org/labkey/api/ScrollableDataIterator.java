package org.labkey.api;

import org.labkey.api.etl.DataIterator;

/**
 *
 * (MAB) I go back and forth between adding methods to DataIterator and having extended/marker interfaces.
 *
 * I'm going to go with the idea of having the 'purest' interface possible, and see how it goes.
 *
 * Why have the isScrollable() method on a ScrollableDataIterator inteface?  This is so that wrapper/pass-through
 * base classes can pass-through the scrollability of their input class if appropriate.
 */
public interface ScrollableDataIterator extends DataIterator
{
    boolean isScrollable();
    void beforeFirst();
}
