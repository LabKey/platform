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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class AdminBean
{
    public static class ActiveUser
    {
        public final String email;
        public final long minutes;
        ActiveUser(String email, Long minutes)
        {
            this.email = email;
            this.minutes = null==minutes ? 0 : minutes.longValue();
        }
    }

    public static final String javaVendor = System.getProperty("java.vendor");
    public static final String javaRuntimeName = System.getProperty("java.runtime.name");
    public static final String javaVersion = System.getProperty("java.runtime.version");
    public static final String javaHome = System.getProperty("java.home");
    public static final String userName = System.getProperty("user.name");
    public static final String userHomeDir = System.getProperty("user.home");
    public static final String webappDir = ModuleLoader.getServletContext().getRealPath("");
    public static final String workingDir = new File("file").getAbsoluteFile().getParent();
    public static final String osName = System.getProperty("os.name");
    public static final @Nullable String releaseVersion = ModuleLoader.getInstance().getCoreModule().getReleaseVersion();
    public static final String mode = AppProps.getInstance().isDevMode() ? "Development" : "Production";
    public static final String serverGuid = AppProps.getInstance().getServerGUID();
    public static final String serverSessionGuid = AppProps.getInstance().getServerSessionGUID();
    public static final String servletContainer = ModuleLoader.getServletContext().getServerInfo();
    public static final List<Module> modules;

    public static String asserts = "disabled";

    @JsonIgnore
    public static final DbScope scope = CoreSchema.getInstance().getSchema().getScope();

    private final static Map<String, String> propertyMap = new TreeMap<>();

    static
    {
        Arrays.stream(AdminBean.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .filter(f -> f.getType().equals(String.class))
            .forEach(f -> propertyMap.put(f.getName(), getValue(f)));

        Arrays.stream(scope.getClass().getMethods())
            .filter(m -> m.getReturnType().equals(String.class))
            .filter(m -> m.getName().startsWith("get"))
            .forEach(m -> {
                String key = StringUtils.uncapitalize(m.getName().substring(3));
                propertyMap.put(key, getValue(m));
            });

        //noinspection ConstantConditions,AssertWithSideEffects
        assert null != (asserts = "enabled");

        modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
    }

    private static @Nullable String getValue(Field field)
    {
        try
        {
            return (String)field.get(null);
        }
        catch (IllegalAccessException e)
        {
            return null;
        }
    }

    private static @Nullable String getValue(Method method)
    {
        try
        {
            return (String)method.invoke(scope);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            return null;
        }
    }

    // No constructing for you!
    private AdminBean()
    {
    }

    public static List<NavTree> getLinks(ViewContext ctx)
    {
        List<NavTree> links = new ArrayList<>();
        for (AdminConsoleHeaderLinkProvider headerLinkProvider : AdminConsoleService.get().getAdminConsoleHeaderProviders())
        {
            links.addAll(headerLinkProvider.getLinks(ctx));
        }
        return links;
    }

    public static List<ActiveUser> getActiveUsers()
    {
        return UserManager.getRecentUsers(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR).stream()
            .map(p -> new ActiveUser(p.first, p.second))
            .collect(Collectors.toList());
    }

    public static Map<String, String> getPropertyMap()
    {
        return propertyMap;
    }
}
