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
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.admin.AdminUrls;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: migra
 * Date: Jul 19, 2005
 * Time: 1:16:28 PM
 */
public class ModuleContext implements Cloneable
{
    private double installedVersion;
    private String className;
    private boolean express = false;
    private String name;
    private Map<String, Object> properties = Collections.synchronizedMap(new HashMap<String, Object>());
    private ModuleLoader.ModuleState moduleState = ModuleLoader.ModuleState.Loading;
    private static Logger _log = Logger.getLogger(ModuleContext.class);

    public ModuleContext()
    {
    }

    public ModuleContext(Module module)
    {
        className = module.getClass().getName();
        this.setName(module.getName());
    }


    public boolean getExpress()
    {
        return express;
    }

    public void setExpress(boolean express)
    {
        this.express = express;
    }

    public double getInstalledVersion()
    {
        return installedVersion;
    }

    public void setInstalledVersion(double installedVersion)
    {
        this.installedVersion = installedVersion;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String name)
    {
        this.className = name;
    }

    public String getMessage()
    {
        double targetVersion = ModuleLoader.getInstance().getModule(name).getVersion();
        return getModuleState().describeModuleState(installedVersion, targetVersion);
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
        return moduleState;
    }

    public void setModuleState(ModuleLoader.ModuleState moduleState)
    {
        this.moduleState = moduleState;
    }

    public void upgradeComplete(double version)
    {
        installedVersion = version;
        setModuleState(ModuleLoader.ModuleState.ReadyToRun);
        ModuleLoader.getInstance().saveModuleContext(this);
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public ActionURL getUpgradeCompleteURL(double newVersion)
    {
        return PageFlowUtil.urlProvider(AdminUrls.class).getModuleUpgradeURL(getName(), getInstalledVersion(), newVersion, ModuleLoader.ModuleState.InstallComplete, getExpress());
    }

    public ActionURL getContinueUpgradeURL(double newVersion)
    {
        return PageFlowUtil.urlProvider(AdminUrls.class).getModuleUpgradeURL(getName(), getInstalledVersion(), newVersion, ModuleLoader.ModuleState.Installing, false);
    }

    @Deprecated
    public Map<String, Object> getProperties()
    {
        return properties;
    }
}
