/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.core.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Directive;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.admin.AdminController;
import org.labkey.filters.ContentSecurityPolicyFilter;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.api.settings.AppProps.ADMIN_PROVIDED_ALLOWED_EXTERNAL_RESOURCES;

public class AllowedExternalResourceHosts
{
    private static final Logger LOG = LogHelper.getLogger(AllowedExternalResourceHosts.class, "Exceptions reading persisted external resource hosts");

    private AllowedExternalResourceHosts()
    {
    }

    public record AllowedHost(Directive directive, String host) { }

    // Callers must ensure thread-safe access to this method
    public static void saveAllowedHosts(@Nullable Collection<AllowedHost> allowedHosts, User user)
    {
        if (null != allowedHosts)
        {
            final String json;

            try
            {
                json = JsonUtil.createDefaultMapper().writeValueAsString(allowedHosts);
            }
            catch (JsonProcessingException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
                return;
            }

            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setAllowedExternalResourceHosts(json);
            props.save(user);

            // Group the allowed hosts by directive
            Map<Directive, List<String>> map = allowedHosts.stream()
                .collect(Collectors.groupingBy(AllowedHost::directive, Collectors.mapping(AllowedHost::host, Collectors.toCollection(ArrayList::new))));

            // Unregister all supported directives then register the directives that have at least one allowed host
            Arrays.stream(Directive.values()).forEach(dir -> {
                unregister(dir);
                List<String> list = map.get(dir);
                if (list != null)
                    register(dir, list.toArray(new String[0]));
            });
        }
    }

    private static void register(Directive dir, String... hosts)
    {
        ContentSecurityPolicyFilter.registerAllowedSources(ADMIN_PROVIDED_ALLOWED_EXTERNAL_RESOURCES, dir, hosts);
    }

    private static void unregister(Directive dir)
    {
        ContentSecurityPolicyFilter.unregisterAllowedSources(ADMIN_PROVIDED_ALLOWED_EXTERNAL_RESOURCES, dir);
    }

    // Returns a mutable list (mutating it won't affect any cached values)
    public static List<AllowedHost> readAllowedHosts() throws JsonProcessingException
    {
        String json = AppProps.getInstance().getAllowedExternalResourceHosts();
        return JsonUtil.createDefaultMapper().readValue(json, new TypeReference<>() {});
    }

    public static void registerHosts()
    {
        final List<AllowedHost> list;

        try
        {
            list = AllowedExternalResourceHosts.readAllowedHosts();
        }
        catch (JsonProcessingException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return;
        }

        list.forEach(sub -> register(sub.directive(), sub.host()));
        LOG.debug("Registered [{}] as allowed external sources", list);
    }

    public static void registerStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>("AllowedExternalResourceHosts", Directive.class)
        {
            @Override
            public void handle(Map<Directive, StartupPropertyEntry> properties)
            {
                // If any allowed-hosts startup properties are provided, they completely replace *all* values that were
                // previously configured
                if (!properties.isEmpty())
                {
                    BindException errors = new BindException(new Object(), "form");
                    List<AllowedHost> allowedHosts = properties.entrySet().stream()
                        .flatMap(e -> Arrays.stream(e.getValue().getValue().trim().split("\\s+"))
                            .map(host -> AdminController.ExternalSourcesForm.validateHost(e.getKey().name(), host, errors))
                        )
                        .toList();

                    if (!errors.hasErrors())
                    {
                        AdminController.ExternalSourcesForm.checkDuplicates(allowedHosts, errors);
                    }

                    if (errors.hasErrors())
                    {
                        LOG.error("Invalid AllowedExternalResourceHosts startup properties", errors);
                    }
                    else
                    {
                        // No need to synchronize since startup property handling is single-threaded
                        saveAllowedHosts(allowedHosts, User.getAdminServiceUser());
                    }
                }
            }
        });
    }
}
