/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.labkey.api.security.User;

import java.text.DecimalFormat;

/**
 * User: migra
 * Date: Jul 19, 2005
 * Time: 1:16:28 PM
 */
public class ModuleContext implements Cloneable
{
    private double _installedVersion;
    private double _originalVersion = 0.0;
    private String _className;
    private String _name;
    private ModuleLoader.ModuleState _moduleState = ModuleLoader.ModuleState.Loading;
    private boolean _newInstall = false;

    private static Logger _log = Logger.getLogger(ModuleContext.class);

    public ModuleContext()
    {
    }

    public ModuleContext(Module module)
    {
        _className = module.getClass().getName();
        setName(module.getName());
        assert _installedVersion == 0.00;
        _newInstall = true;
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
        double targetVersion = ModuleLoader.getInstance().getModule(_name).getVersion();
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
