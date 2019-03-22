/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.module;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: migra
 * Date: Jul 19, 2005
 * Time: 1:16:28 PM
 */
public class ModuleContext implements Cloneable
{
    // ModuleContext fields are written by the main upgrade thread and read by request threads to show current
    // upgrade status.  Use volatile to ensure reads see the latest values.

    // These three fields are effectively "final" -- they are set on construction (new module) or when loaded from DB,
    // then never changed.
    private volatile double _originalVersion = -1.0;
    private volatile String _className;
    private volatile String _name;

    private volatile boolean _autoUninstall;
    private volatile String _schemas;

    // These two are updated during the upgrade process.
    private volatile double _installedVersion;
    private volatile ModuleLoader.ModuleState _moduleState = ModuleLoader.ModuleState.Loading;

    private final boolean _newInstall;

    public ModuleContext()
    {
        _newInstall = false;  // ModuleContext is being loaded from the database
    }

    public ModuleContext(Module module)
    {
        _className = module.getClass().getName();
        setName(module.getName());
        assert _installedVersion == 0.00;
        _newInstall = true;  // ModuleContext has not been seen before
    }


    public double getInstalledVersion()
    {
        return _installedVersion;
    }

    // InstalledVersion gets changed after upgrade... OriginalVersion keeps the version as it existed at server startup
    public double getOriginalVersion()
    {
        return _originalVersion;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setInstalledVersion(double installedVersion)
    {
        _installedVersion = installedVersion;
        _originalVersion = installedVersion;
    }

    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public String getClassName()
    {
        return _className;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setClassName(String name)
    {
        _className = name;
    }

    public String getMessage()
    {
        Module module = ModuleLoader.getInstance().getModule(_name);

        // This could happen if a module failed to initialize
        if (null == module)
        {
            //noinspection ThrowableInstanceNeverThrown
            ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("Module " + _name + " failed to initialize"));
            return "Configuration problem with this module";
        }

        double targetVersion = module.getVersion();
        return getModuleState().describeModuleState(this, _installedVersion, targetVersion);
    }

    private static DecimalFormat df2 = new DecimalFormat("0.00#");

    public static String formatVersion(double version)
    {
        return df2.format(version);
    }

    public ModuleLoader.ModuleState getModuleState()
    {
        return _moduleState;
    }

    public void setModuleState(ModuleLoader.ModuleState moduleState)
    {
        _moduleState = moduleState;
    }

    public void upgradeComplete(Module module)
    {
        _installedVersion = module.getVersion();
        setModuleState(ModuleLoader.ModuleState.ReadyToStart);
        ModuleLoader.getInstance().saveModuleContext(this);

        SqlScriptRunner.SqlScriptProvider provider = new FileSqlScriptProvider(module);

        for (DbSchema schema : provider.getSchemas())
        {
            if (schema.getSqlDialect().canExecuteUpgradeScripts())
            {
                SqlScriptManager manager = SqlScriptManager.get(provider, schema);
                manager.updateSchemaVersion(_installedVersion);
            }
        }
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public User getUpgradeUser()
    {
        return ModuleLoader.getInstance().getUpgradeUser();
    }

    public boolean isAutoUninstall()
    {
        return _autoUninstall;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAutoUninstall(Boolean autoUninstall)
    {
        if (autoUninstall != null)
        {
            _autoUninstall = autoUninstall;
        }
    }

    public @Nullable String getSchemas()
    {
        return _schemas;
    }

    public List<String> getSchemaList()
    {
        if (StringUtils.isEmpty(getSchemas()))
            return Collections.emptyList();
        else
            return Arrays.asList(getSchemas().split(","));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setSchemas(String schemas)
    {
        _schemas = schemas;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleContext that = (ModuleContext) o;

        if (_className != null ? !_className.equals(that._className) : that._className != null) return false;
        if (_name != null ? !_name.equals(that._name) : that._name != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _className != null ? _className.hashCode() : 0;
        result = 31 * result + (_name != null ? _name.hashCode() : 0);
        return result;
    }

    public void addDeferredUpgradeRunnable(String description, Runnable runnable)
    {
        Module module = ModuleLoader.getInstance().getModule(_name);
        if (module != null)
            module.addDeferredUpgradeRunnable(description, runnable);
        else
            ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("Module " + _name + " failed to initialize"));
    }
}
