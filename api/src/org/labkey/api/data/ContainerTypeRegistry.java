/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
