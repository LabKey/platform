package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;

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
            if (!_handlers.containsKey(provider.getName()))
            {
                _handlers.put(provider.getName(), handler);
            }
            else
                throw new RuntimeException("A Flag Handler for Assay provider : " + provider.getName() + " is already registered");
        }
        else
            throw new RuntimeException("The specified assay provider is null");
    }

    @Nullable
    static AssayFlagHandler getHandler(AssayProvider provider)
    {
        return _handlers.get(provider.getName());
    }

    BaseColumnInfo createFlagColumn(ExpProtocol protocol, TableInfo parent, String schemaName, boolean editable);

    BaseColumnInfo createQCEnabledColumn(ExpProtocol protocol, TableInfo parent, String schemaName);

    /**
     * Saves a ExpQCFlag instance for the specified run.
     */
    <FlagType extends ExpQCFlag> void saveFlag(Container container, User user, FlagType flag);

    /**
     * Delete all flags for the run.
     */
    int deleteFlags(Container container, User user, int runId);

    /**
     * Returns the flags for the specified run.
     */
    <FlagType extends ExpQCFlag> List<FlagType> getFlags(int runId, Class<FlagType> cls);
}
