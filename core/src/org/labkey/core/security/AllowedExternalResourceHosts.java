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

    public record Substitution(Directive directive, String host) { }

    public static void saveSubstitutions(@Nullable Collection<Substitution> substitutions, User user) throws JsonProcessingException
    {
        if (null != substitutions)
        {
            String json = JsonUtil.createDefaultMapper().writeValueAsString(substitutions);

            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setAllowedExternalSources(json);
            props.save(user);

            // Group the substitutions by directive
            Map<Directive, List<String>> map = substitutions.stream()
                .collect(Collectors.groupingBy(Substitution::directive, Collectors.mapping(Substitution::host, Collectors.toCollection(ArrayList::new))));

            // Unregister all supported directives then register the directives with substitutions
            Arrays.stream(Directive.values()).forEach(dir -> {
                ContentSecurityPolicyFilter.unregisterAllowedSources(dir, ALLOWED_EXTERNAL_RESOURCES);
                List<String> list = map.get(dir);
                if (list != null)
                    ContentSecurityPolicyFilter.registerAllowedSources(dir, ALLOWED_EXTERNAL_RESOURCES, list.toArray(new String[0]));
            });
        }
    }

    // Returns a mutable list (mutating it won't affect any cached values)
    public static List<Substitution> readSubstitutions() throws JsonProcessingException
    {
        String json = AppProps.getInstance().getAllowedExternalResources();
        return JsonUtil.createDefaultMapper().readValue(json, new TypeReference<>() {});
    }

    public static void registerHosts()
    {
        final List<Substitution> list;

        try
        {
            list = AllowedExternalResourceHosts.readSubstitutions();
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
