package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Bit of a misnomer, but I couldn't think of a better name. These modules provide code and resources, but don't manage
 * any schemas, don't run SQL scripts, and don't need to do anything at upgrade time. This simplifies the implementation
 * of such modules and eases module bumps at release time.
 *
 * Perhaps this should implement Module instead of extending DefaultModule.
 *
 * Created by adam on 6/29/2016.
 */
public abstract class CodeOnlyModule extends DefaultModule
{
    /**
     * Update this version number immediately before release; all modules that extend this class will inherit the new version.
     *
     * @return The module version
     */
    @Override
    public final double getVersion()
    {
        return 16.20;
    }

    @Override
    public final boolean hasScripts()
    {
        return false;
    }

    @NotNull
    @Override
    public final Collection<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    @NotNull
    @Override
    public final Collection<String> getProvisionedSchemaNames()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public final Set<DbSchema> getSchemasToTest()
    {
        return Collections.emptySet();
    }

    @Override
    public final void beforeUpdate(ModuleContext moduleContext)
    {
    }

    @Override
    public final void versionUpdate(ModuleContext moduleContext) throws Exception
    {
    }

    @Override
    public final void afterUpdate(ModuleContext moduleContext)
    {
    }

    @Nullable
    @Override
    public final UpgradeCode getUpgradeCode()
    {
        return null;
    }
}
