package org.labkey.api.security;

import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.AbstractSetValuedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthenticationConfigurationCache
{
    // We have just a single object to cache (a global AuthenticationConfigurationCollections), but use standard cache (blocking cache wrapping the
    // shared cache) for convenience and to ensure that configuration changes will get propagated once multiple application servers are supported.
    private static final BlockingCache<String, AuthenticationConfigurationCollections> CACHE = new BlockingStringKeyCache<>(CacheManager.getSharedCache(), (key, argument) -> new AuthenticationConfigurationCollections());
    private static final String CACHE_KEY = AuthenticationConfigurationCache.class.getName();

    static
    {
        // Cache AuthenticationConfigurationCollections forever
        CACHE.setCacheTimeChooser((key, argument) -> CacheManager.YEAR);
    }

    private static class AuthenticationConfigurationCollections
    {
        private final SetValuedMap<Class<? extends AuthenticationConfiguration>, AuthenticationConfiguration> _allMap = new AbstractSetValuedMap<>(new LinkedHashMap<>())
        {
            @Override
            protected Set<AuthenticationConfiguration> createCollection()
            {
                return new LinkedHashSet<>();
            }
        };

        private final SetValuedMap<Class<? extends AuthenticationConfiguration>, AuthenticationConfiguration> _activeMap = new AbstractSetValuedMap<>(new LinkedHashMap<>())
        {
            @Override
            protected Set<AuthenticationConfiguration> createCollection()
            {
                return new LinkedHashSet<>();
            }
        };

        private AuthenticationConfigurationCollections()
        {
            boolean acceptOnlyFicamProviders = AuthenticationManager.isAcceptOnlyFicamProviders();

            // Select all the configurations listed in the core.AuthenticationConfigurations table and group by provider.
            // We group them so we can make a single call to each AuthenticationProvider to convert all of its maps into
            // AuthenticationConfigurations in a single operation. This allows the provider to definitively record current
            // state, for example, the LDAP provider can stash all the email domains tied to LDAP authentication, to tailor
            // messages on administration pages.
            Map<PrimaryAuthenticationProvider, List<Map<String, Object>>> configurationMap =
                new TableSelector(CoreSchema.getInstance().getTableInfoAuthenticationConfigurations()) // Don't bother sorting since we're going to group by provider
                    .mapStream()
                    .filter(m->null != getProvider(m))  // Filter out providers that no longer exist - null keys throw
                    .collect(Collectors.groupingBy(this::getProvider));

            // Bit of a hack: LdapProvider sets "ldapDomain" to the first configuration's domain. We should add getDomain()
            // to AuthenticationConfiguration and collect all email domains, caching them with the collections. This is only
            // used for administrative messages, so we'll continue to tolerate this approach for a little while longer.
            AuthenticationManager.setLdapDomain(null);

            // Add each group of configurations
            addConfigurations(configurationMap, acceptOnlyFicamProviders);

            // Gather and add all the "permanent" configurations -- this should be just a single configuration for Database authentication
            Map<PrimaryAuthenticationProvider, List<Map<String, Object>>> permanentMap = AuthenticationManager.getAllPrimaryProviders().stream()
                .filter(AuthenticationProvider::isPermanent)
                .collect(Collectors.toMap(p->p, p->Collections.emptyList()));

            addConfigurations(permanentMap, acceptOnlyFicamProviders);
        }

        // Little helper method simplifies the stream handling above
        private @Nullable PrimaryAuthenticationProvider<?> getProvider(Map<String, Object> map)
        {
            return AuthenticationProviderCache.getProvider(PrimaryAuthenticationProvider.class, (String)map.get("Provider"));
        }

        // Add all the configurations, one provider group at a time
        private void addConfigurations(Map<PrimaryAuthenticationProvider, List<Map<String, Object>>> configurationMap, boolean acceptOnlyFicamProviders)
        {
            configurationMap.entrySet().stream()
                .filter(e->(!acceptOnlyFicamProviders || e.getKey().isFicamApproved()))
                .map(e->getConfigurations(e.getKey(), e.getValue()))
                .flatMap(Collection::stream)
                .sorted(AUTHENTICATION_CONFIGURATION_COMPARATOR)
                .forEach(this::addConfiguration);
        }

        // Order by SortOrder & RowId
        private static final Comparator<AuthenticationConfiguration> AUTHENTICATION_CONFIGURATION_COMPARATOR = Comparator.<AuthenticationConfiguration>comparingInt(AuthenticationConfiguration::getSortOrder).thenComparingInt(AuthenticationConfiguration::getRowId);

        // Translate a provider's maps into ConfigurationSettings and then ask the provider to convert these into AuthenticationConfigurations
        private List<AuthenticationConfiguration> getConfigurations(PrimaryAuthenticationProvider provider, List<Map<String, Object>> list)
        {
            List<ConfigurationSettings> settings = list.stream()
                .map(ConfigurationSettings::new)
                .collect(Collectors.toList());

            return provider.getAuthenticationConfigurations(settings);
        }

        private void addConfiguration(AuthenticationConfiguration<?> configuration)
        {
            addToMap(_allMap, configuration);

            if (configuration.isEnabled())
                addToMap(_activeMap, configuration);
        }

        private void addToMap(SetValuedMap<Class<? extends AuthenticationConfiguration>, AuthenticationConfiguration> map, AuthenticationConfiguration configuration)
        {
            map.put(configuration.getClass(), configuration);
            AuthenticationConfiguration.ALL_CONFIGURATION_INTERFACES
                .stream()
                .filter(providerClass -> providerClass.isInstance(configuration))
                .forEach(providerClass -> map.put(providerClass, configuration));
        }

        private @NotNull <T extends AuthenticationConfiguration> Collection<T> getAll(Class<T> clazz)
        {
            Collection<T> configurations = (Collection<T>) _allMap.get(clazz);

            return null != configurations ? configurations : Collections.emptyList();
        }

        private @NotNull <T extends AuthenticationConfiguration> Collection<T> getActive(Class<T> clazz)
        {
            Collection<T> configurations = (Collection<T>) _activeMap.get(clazz);

            return null != configurations ? configurations : Collections.emptyList();
        }
    }

    /**
     * Get all configurations (whether active or not) that implement the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return A collection of the requested configurations
     */
    public static @NotNull <T extends AuthenticationConfiguration<?>> Collection<T> getConfigurations(Class<T> clazz)
    {
        return CACHE.get(CACHE_KEY).getAll(clazz);
    }

    /**
     * Get all active providers that implement the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return A collection of the requested configurations
     */
    static @NotNull <T extends AuthenticationConfiguration> Collection<T> getActive(Class<T> clazz)
    {
        return CACHE.get(CACHE_KEY).getActive(clazz);
    }

    /**
     * Get the active configuration with the specified name that implements the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return The requested configuration or null if not found
     */
    public static @Nullable <T extends AuthenticationConfiguration> T getActiveConfiguration(Class<T> clazz, int rowId)
    {
        for (T configuration : getActive(clazz))
            if (configuration.getRowId() == rowId)
                return configuration;

        return null;
    }

    /**
     * Get the configuration (whether active or not) with the specified name that implements the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return The requested configuration or null if not found
     */
    public static @Nullable <T extends AuthenticationConfiguration> T getConfiguration(Class<T> clazz, int rowId)
    {
        for (T configuration : getConfigurations(clazz))
            if (configuration.getRowId() == rowId)
                return configuration;

        return null;
    }

    static void clear()
    {
        CACHE.remove(CACHE_KEY);
    }
}
