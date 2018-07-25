/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.admin;

import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AdminBean
{
    public final List<Module> modules;
    public String javaVersion = System.getProperty("java.version");
    public String javaHome = System.getProperty("java.home");
    public String userName = System.getProperty("user.name");
    public String userHomeDir = System.getProperty("user.home");
    public String webappDir = ModuleLoader.getServletContext().getRealPath("");
    public String workingDir = new File("file").getAbsoluteFile().getParent();
    public String osName = System.getProperty("os.name");
    public String mode = AppProps.getInstance().isDevMode() ? "Development" : "Production";
    public String asserts = "disabled";
    public String serverGuid = AppProps.getInstance().getServerGUID();
    public String servletContainer = ModuleLoader.getServletContext().getServerInfo();
    public DbScope scope = CoreSchema.getInstance().getSchema().getScope();
    public List<Pair<String, Long>> active = UserManager.getRecentUsers(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR);
    public String userEmail;

    public AdminBean(User user)
    {
        //noinspection ConstantConditions,AssertWithSideEffects
        assert null != (asserts = "enabled");
        userEmail = user.getEmail();
        modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
    }
}
