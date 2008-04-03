package org.labkey.api.study;

import org.labkey.api.data.Container;

import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 5:12:37 PM
 */
public interface PropertySet
{
    Set<String> getPropertyNames();

    void setProperty(String name, Object value);

    Object getProperty(String name);

    Container getContainer();
    
    String getLSID();
}
