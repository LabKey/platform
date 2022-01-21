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
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.module.ModuleLoader.ModuleState;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * User: migra
 * Date: Jul 19, 2005
 * Time: 1:16:28 PM
 */
public class ModuleContext implements Cloneable
{
    // ModuleContext fields are written by the main upgrade thread and read by request threads to show current
    // upgrade status. Use volatile to ensure reads see the latest values.

    // These three fields are effectively "final" -- they are set on construction (new module) or when loaded from DB,
    // then never changed.
    private volatile Double _originalVersion = null;
    private volatile String _className;
    private volatile String _name;

    private volatile boolean _autoUninstall;
    private volatile String _schemas;

    // These two are updated during the upgrade process.
    private volatile Double _installedSchemaVersion;
    private volatile ModuleState _moduleState = ModuleState.Loading;

    private final boolean _newInstall;

    public ModuleContext()
    {
        _newInstall = false;  // ModuleContext is being loaded from the database
    }

    public ModuleContext(Module module)
    {
        _className = module.getClass().getName();
        setName(module.getName());
        assert _installedSchemaVersion == null;
        _newInstall = true;  // ModuleContext has not been seen before
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public Double getSchemaVersion()
    {
        return _installedSchemaVersion;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setSchemaVersion(Double schemaVersion)
    {
        // Now that we no longer upgrade from 20.2, member should always be null when called
        assert null == _installedSchemaVersion;

        _installedSchemaVersion = schemaVersion;
        _originalVersion = schemaVersion;
    }

    // For convenience and backwards compatibility, this method returns schema version as a double, returning 0.0 for null
    public double getInstalledVersion()
    {
        return null != _installedSchemaVersion ? _installedSchemaVersion : 0.0;
    }

    // InstalledVersion gets changed after upgrade process... OriginalVersion keeps the version as it existed at server startup
    public double getOriginalVersion()
    {
        return null != _originalVersion ? _originalVersion : 0.0;
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

    private static final DecimalFormat df2 = new DecimalFormat("0.00#");
    private static final DecimalFormat df3 = new DecimalFormat("0.000");

    public static String formatVersion(double version)
    {
        // Use three-digit minor versions for 0.000 and 20.000+, otherwise two-digit + optional third
        return (version < 20 && version != 0.0 ? df2 : df3).format(version);
    }

    public ModuleState getModuleState()
    {
        return _moduleState;
    }

    public void setModuleState(ModuleState moduleState)
    {
        _moduleState = moduleState;
    }

    public void upgradeComplete(Module module)
    {
        _installedSchemaVersion = module.getSchemaVersion();
        setModuleState(ModuleState.ReadyToStart);
        ModuleLoader.getInstance().saveModuleContext(this);

        SqlScriptRunner.SqlScriptProvider provider = new FileSqlScriptProvider(module);

        for (DbSchema schema : provider.getSchemas())
        {
            if (schema.getSqlDialect().canExecuteUpgradeScripts())
            {
                SqlScriptManager manager = SqlScriptManager.get(provider, schema);
                manager.updateSchemaVersion(_installedSchemaVersion);
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
        return User.getSearchUser();
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

    public boolean needsUpgrade(Double newSchemaVersion)
    {
        return isNewInstall() || needsUpgrade(_installedSchemaVersion, newSchemaVersion);
    }

    private static boolean needsUpgrade(Double installedSchemaVersion, Double newSchemaVersion)
    {
        // Both null or same version
        if (Objects.equals(installedSchemaVersion, newSchemaVersion))
            return false;

        // One is null... allow "upgrade" from or to null
        if (null == installedSchemaVersion || null == newSchemaVersion)
            return true;

        return installedSchemaVersion < newSchemaVersion;
    }

    public boolean isDowngrade(Double newSchemaVersion)
    {
        return isDowngrade(_installedSchemaVersion, newSchemaVersion);
    }

    private static boolean isDowngrade(Double installedSchemaVersion, Double newSchemaVersion)
    {
        if (null == installedSchemaVersion || null == newSchemaVersion)
            return false;

        return newSchemaVersion < installedSchemaVersion;
    }

    public void addDeferredUpgradeRunnable(String description, Runnable runnable)
    {
        Module module = ModuleLoader.getInstance().getModule(_name);
        if (module != null)
            module.addDeferredUpgradeRunnable(description, runnable);
        else
            ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("Module " + _name + " failed to initialize"));
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

    public static class TestCase extends Assert
    {
        @Test
        public void testNeedsUpgrade()
        {
            assertFalse(needsUpgrade(null, null));
            assertFalse(needsUpgrade(1.0, 1.0));
            assertFalse(needsUpgrade(2.0, 1.0));

            assertTrue(needsUpgrade(null, 1.0));
            assertTrue(needsUpgrade(1.0, null));
            assertTrue(needsUpgrade(1.0, 2.0));
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        public void testIsDowngrade()
        {
            assertFalse(isDowngrade(null, null));
            assertFalse(isDowngrade(1.0, 1.0));
            assertFalse(isDowngrade(null, 1.0));
            assertFalse(isDowngrade(1.0, null));
            assertFalse(isDowngrade(1.0, 2.0));

            assertTrue(isDowngrade(2.0, 1.0));
            assertTrue(isDowngrade(20.000, 19.35));
            assertTrue(isDowngrade(20.001, 20.000));
            assertTrue(isDowngrade(21.000, 20.000));
        }
    }
}
