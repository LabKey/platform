package org.labkey.api.data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for different types of container.  The name provided when registering is the value
 * stored in the "type" field of the container table.
 */
public class ContainerTypeRegistry
{
    private ConcurrentMap<String, ContainerType> types = new ConcurrentHashMap<>();
    private static ContainerTypeRegistry instance = new ContainerTypeRegistry();

    private ContainerTypeRegistry()
    {
        // private to implement singleton
    }

    public static ContainerTypeRegistry get()
    {
        return instance;
    }

    public void register(String name, ContainerType containerType) throws Exception
    {
        name = name.toLowerCase();
        if (types.get(name) != null)
            throw new Exception("Container type with name '" + name + "' already exists.  Type names are case-insensitive and must be globally unique.");

        types.put(name, containerType);
    }

    public Boolean hasType(String name)
    {
        return types.containsKey(name.toLowerCase());
    }

    public ContainerType getType(String name)
    {
        return types.get(name.toLowerCase());
    }

    public Set<String> getTypeNames()
    {
        return types.keySet();
    }
}
