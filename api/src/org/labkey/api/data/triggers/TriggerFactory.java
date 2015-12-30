package org.labkey.api.data.triggers;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;

import java.util.Collection;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/21/15
 */
public interface TriggerFactory
{
    @NotNull
    Collection<Trigger> createTrigger(Container c, TableInfo table, Map<String, Object> extraContext);
}
