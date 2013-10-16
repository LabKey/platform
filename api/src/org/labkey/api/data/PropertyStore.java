package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: adam
 * Date: 10/11/13
 * Time: 4:55 PM
 */
public interface PropertyStore
{
    @NotNull Map<String, String> getProperties(User user, Container container, String category);
    @NotNull Map<String, String> getProperties(Container container, String category);
    @NotNull Map<String, String> getProperties(String category);

    // If create == true, then never returns null. If create == false, will return null if property set doesn't exist.
    public PropertyManager.PropertyMap getWritableProperties(User user, Container container, String category, boolean create);
    public PropertyManager.PropertyMap getWritableProperties(Container container, String category, boolean create);
    public PropertyManager.PropertyMap getWritableProperties(String category, boolean create);

    public void deletePropertySet(User user, Container container, String category);
    public void deletePropertySet(Container container, String category);
    public void deletePropertySet(String category);

    // Map must be a PropertyMap
    public void saveProperties(Map<String, String> map);
}
