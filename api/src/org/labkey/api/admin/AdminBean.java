/*
 * Copyright (c) 2018 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AdminBean
{
    public final String javaVendor = System.getProperty("java.vendor");
    public final String javaRuntimeName = System.getProperty("java.runtime.name");
    public final String javaVersion = System.getProperty("java.runtime.version");
    public final String javaHome = System.getProperty("java.home");
    public final String userName = System.getProperty("user.name");
    public final String userHomeDir = System.getProperty("user.home");
    public final String webappDir = ModuleLoader.getServletContext().getRealPath("");
    public final String workingDir = new File("file").getAbsoluteFile().getParent();
    public final String osName = System.getProperty("os.name");
    public final @Nullable String releaseVersion = ModuleLoader.getInstance().getCoreModule().getProperties().get("Release Version");
    public final String mode = AppProps.getInstance().isDevMode() ? "Development" : "Production";
    public final String serverGuid = AppProps.getInstance().getServerGUID();
    public final String serverSessionGuid = AppProps.getInstance().getServerSessionGUID();
    public final String servletContainer = ModuleLoader.getServletContext().getServerInfo();
    public final DbScope scope = CoreSchema.getInstance().getSchema().getScope();
    public final List<Pair<String, Long>> active = UserManager.getRecentUsers(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR);

    public final String userEmail;
    public final List<Module> modules;

    public String asserts = "disabled";

    public AdminBean(User user)
    {
        //noinspection ConstantConditions,AssertWithSideEffects
        assert null != (asserts = "enabled");
        userEmail = user.getEmail();
        modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
    }

    public List<NavTree> getLinks(ViewContext ctx)
    {
        List<NavTree> links = new ArrayList<>();
        for (AdminConsoleHeaderLinkProvider headerLinkProvider : AdminConsoleService.get().getAdminConsoleHeaderProviders())
        {
            links.addAll(headerLinkProvider.getLinks(ctx));
        }
        return links;
    }
}
