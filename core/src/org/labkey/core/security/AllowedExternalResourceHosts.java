package org.labkey.core.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.Directive;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.filters.ContentSecurityPolicyFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.api.settings.AppProps.ALLOWED_EXTERNAL_RESOURCES;

public class AllowedExternalResourceHosts
{
    private static final Logger LOG = LogHelper.getLogger(AllowedExternalResourceHosts.class, "Exceptions reading persisted external resource hosts");

    private AllowedExternalResourceHosts()
    {
    }

    public record AllowedHost(Directive directive, String host) { }

    public static void saveAllowedHosts(@Nullable Collection<AllowedHost> allowedHosts, User user) throws JsonProcessingException
    {
        if (null != allowedHosts)
        {
            String json = JsonUtil.createDefaultMapper().writeValueAsString(allowedHosts);

            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setAllowedExternalResourceHosts(json);
            props.save(user);

            // Group the allowed hosts by directive
            Map<Directive, List<String>> map = allowedHosts.stream()
                .collect(Collectors.groupingBy(AllowedHost::directive, Collectors.mapping(AllowedHost::host, Collectors.toCollection(ArrayList::new))));

            // Unregister all supported directives then register the directives with that have allowed hosts
            Arrays.stream(Directive.values()).forEach(dir -> {
                ContentSecurityPolicyFilter.unregisterAllowedSources(dir, ALLOWED_EXTERNAL_RESOURCES);
                List<String> list = map.get(dir);
                if (list != null)
                    ContentSecurityPolicyFilter.registerAllowedSources(dir, ALLOWED_EXTERNAL_RESOURCES, list.toArray(new String[0]));
            });
        }
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

        list.forEach(sub -> ContentSecurityPolicyFilter.registerAllowedSources(sub.directive(), sub.host()));
        LOG.debug("Registered [{}] as allowed external sources", list);
    }
}
