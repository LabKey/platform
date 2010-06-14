/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;

import java.text.DecimalFormat;

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
    private volatile double _originalVersion = 0.0;
    private volatile String _className;
    private volatile String _name;

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

    public void setInstalledVersion(double installedVersion)
    {
        _installedVersion = installedVersion;
        _originalVersion = installedVersion;
    }

    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public boolean isInstallComplete()
    {
        return ModuleLoader.ModuleState.ReadyToRun == _moduleState || ModuleLoader.ModuleState.Running == _moduleState;
    }

    public String getClassName()
    {
        return _className;
    }

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
        return getModuleState().describeModuleState(_installedVersion, targetVersion);
    }

    private static DecimalFormat df2 = new DecimalFormat("0.00#");

    public static String formatVersion(double version)
    {
        return df2.format(version);
    }

    public static String formatVersion(String version)
    {
        return formatVersion(Double.parseDouble(version));
    }

    public ModuleLoader.ModuleState getModuleState()
    {
        return _moduleState;
    }

    public void setModuleState(ModuleLoader.ModuleState moduleState)
    {
        _moduleState = moduleState;
    }

    public void upgradeComplete(double version)
    {
        _installedVersion = version;
        setModuleState(ModuleLoader.ModuleState.ReadyToRun);
        ModuleLoader.getInstance().saveModuleContext(this);
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
}
