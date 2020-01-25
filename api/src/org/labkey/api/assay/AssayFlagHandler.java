/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helps manage and associate ExpQCFlag information on a per run basis. Multiple flags can be stored per run ID and is typically used
 * for well exclusion workflows but is not limited to them. Flag columns can be added to the run domain to render information
 * about assay flags on the run grids.
 *
 * AssayFlagHandlers must be registered on a per provider instance.
 */
public interface AssayFlagHandler
{
    Map<String, AssayFlagHandler> _handlers = new HashMap<>();

    static void registerHandler(AssayProvider provider, AssayFlagHandler handler)
    {
        if (provider != null)
        {
            registerHandler(provider.getClass().getName(), handler);
        }
        else
            throw new RuntimeException("The specified assay provider is null");
    }

    static void registerHandler(String providerClassName, AssayFlagHandler handler)
    {
        if (providerClassName != null)
        {
            if (!_handlers.containsKey(providerClassName))
            {
                _handlers.put(providerClassName, handler);
            }
            else
                throw new RuntimeException("A Flag Handler for Assay provider : " + providerClassName + " is already registered");
        }
        else
            throw new RuntimeException("The specified assay provider is null");
    }

    @Nullable
    static AssayFlagHandler getHandler(AssayProvider provider)
    {
        if (provider != null)
            return _handlers.get(provider.getClass().getName());
        else
            return null;
    }

    BaseColumnInfo createFlagColumn(ExpProtocol protocol, ExpRunTable runTable, String schemaName, boolean editable);

    BaseColumnInfo createQCEnabledColumn(ExpProtocol protocol, ExpRunTable runTable, String schemaName);

    void fixupQCFlagTable(ExpQCFlagTable table, AssayProvider provider, ExpProtocol assayProtocol);

    /**
     * Saves a ExpQCFlag instance for the specified run.
     */
    <FlagType extends ExpQCFlag> void saveFlag(Container container, User user, FlagType flag);

    /**
     * Delete all flags for the run.
     */
    int deleteFlagsForRun(Container container, User user, int runId);

    /**
     * Delete the specified flag
     */
    <FlagType extends ExpQCFlag> void deleteFlag(Container container, User user, FlagType flag);

    /**
     * Returns the flags for the specified run.
     */
    <FlagType extends ExpQCFlag> List<FlagType> getFlags(int runId, Class<FlagType> cls);
}
