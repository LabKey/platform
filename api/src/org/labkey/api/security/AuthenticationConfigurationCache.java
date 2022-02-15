package org.labkey.api.security;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.AbstractSetValuedMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class AuthenticationConfigurationCache
{
    private static final Logger LOG = LogManager.getLogger(AuthenticationConfigurationCache.class);

    // We have just a single object to cache (a global AuthenticationConfigurationCollections), but use standard cache (blocking cache wrapping the
    // shared cache) for convenience and to ensure that configuration changes will get propagated once multiple application servers are supported.
    private static final BlockingCache<String, AuthenticationConfigurationCollections> CACHE = new BlockingCache<>(CacheManager.getSharedCache(), (key, argument) -> new AuthenticationConfigurationCollections());
    private static final String CACHE_KEY = AuthenticationConfigurationCache.class.getName();

    static
    {
        // Cache AuthenticationConfigurationCollections forever
        CACHE.setCacheTimeChooser((key, argument) -> CacheManager.YEAR);
    }

    private static class AuthenticationConfigurationCollections
    {
        private final SetValuedMap<Class<? extends AuthenticationConfiguration>, AuthenticationConfiguration<?>> _allMap = new AbstractSetValuedMap<>(new LinkedHashMap<>())
        {
            @Override
            protected Set<AuthenticationConfiguration<?>> createCollection()
            {
                return new LinkedHashSet<>();
            }
        };

        private final SetValuedMap<Class<? extends AuthenticationConfiguration>, AuthenticationConfiguration<?>> _activeMap = new AbstractSetValuedMap<>(new LinkedHashMap<>())
        {
            @Override
            protected Set<AuthenticationConfiguration<?>> createCollection()
            {
                return new LinkedHashSet<>();
            }
        };

        private final MultiValuedMap<String, AuthenticationConfiguration<?>> _activeDomainMap;
        private final Collection<String> _activeDomains;

        private AuthenticationConfigurationCollections()
        {
            boolean acceptOnlyFicamProviders = AuthenticationManager.isAcceptOnlyFicamProviders();

            // Select the configurations stored in the core.AuthenticationConfigurations table, add the database
            // authentication configuration, map each to the appropriate AuthenticationConfiguration, and add to the maps.

            Stream<Map<String, Object>> configs = Stream.concat(
                new TableSelector(CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), null, new Sort("SortOrder, RowId")).mapStream(),

                // Gather the "permanent" configurations -- this should be just a single configuration for Database authentication
                AuthenticationManager.getAllPrimaryProviders().stream()
                    .filter(AuthenticationProvider::isPermanent)
                    .map(p->Map.of("Provider", p.getName()))
            );

            configs
                .map(this::getAuthenticationConfiguration)
                .filter(Objects::nonNull)
                .filter(c->!acceptOnlyFicamProviders || c.getAuthenticationProvider().isFicamApproved())
                .forEach(this::addConfiguration);

            // MultiValuedMap of domains to AuthenticationConfigurations that claim them
            _activeDomainMap = getActive(PrimaryAuthenticationConfiguration.class).stream()
                .filter(config -> null != config.getDomain())
                .filter(config -> !AuthenticationManager.ALL_DOMAINS.equals(config.getDomain()))
                .collect(LabKeyCollectors.toMultiValuedMap(AuthenticationConfiguration::getDomain, config -> config));

            List<String> activeDomains = new ArrayList<>(_activeDomainMap.keySet());
            Collections.sort(activeDomains);
            _activeDomains = Collections.unmodifiableCollection(activeDomains);
        }

        // Little helper method simplifies the stream handling above
        private @Nullable AuthenticationConfiguration<?> getAuthenticationConfiguration(Map<String, Object> map)
        {
            String providerName = (String)map.get("Provider");
            AuthenticationProvider provider = AuthenticationProviderCache.getProvider(AuthenticationProvider.class, providerName);
            if (null == provider)
            {
                String description = (String)map.get("Description");
                LOG.warn("A saved authentication configuration requires the \"" + providerName + "\" authentication provider, but that provider is not present in this deployment. Authentication via " + (null != description ? "\"" + description + "\"" : "this mechanism") + " will not be available.");
                return null;
            }

            if (!(provider instanceof AuthenticationConfigurationFactory))
                throw new IllegalStateException("AuthenticationProvider does not implement AuthenticationConfigurationFactory: " + provider.getClass().getName());

            return ((AuthenticationConfigurationFactory<?>)provider).getAuthenticationConfiguration(new ConfigurationSettings(map));
        }

        private void addConfiguration(AuthenticationConfiguration<?> configuration)
        {
            addToMap(_allMap, configuration);

            if (configuration.isEnabled())
                addToMap(_activeMap, configuration);
        }

        private void addToMap(SetValuedMap<Class<? extends AuthenticationConfiguration>, AuthenticationConfiguration<?>> map, AuthenticationConfiguration<?> configuration)
        {
            map.put(configuration.getClass(), configuration);
            AuthenticationConfiguration.ALL_CONFIGURATION_INTERFACES
                .stream()
                .filter(providerClass -> providerClass.isInstance(configuration))
                .forEach(providerClass -> map.put(providerClass, configuration));
        }

        private @NotNull <T extends AuthenticationConfiguration<?>> Collection<T> getAll(Class<T> clazz)
        {
            Collection<T> configurations = (Collection<T>) _allMap.get(clazz);

            return null != configurations ? configurations : Collections.emptyList();
        }

        private @NotNull <T extends AuthenticationConfiguration<?>> Collection<T> getActive(Class<T> clazz)
        {
            Collection<T> configurations = (Collection<T>) _activeMap.get(clazz);

            return null != configurations ? configurations : Collections.emptyList();
        }

        private @NotNull Collection<AuthenticationConfiguration> getActiveConfigurationsForDomain(String domain)
        {
            return new ArrayList<>(_activeDomainMap.get(domain));
        }

        private @NotNull Collection<String> getActiveDomains()
        {
            return _activeDomains;
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
    static @NotNull <T extends AuthenticationConfiguration<?>> Collection<T> getActive(Class<T> clazz)
    {
        return CACHE.get(CACHE_KEY).getActive(clazz);
    }

    /**
     * Get the active configuration with the specified name that implements the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return The requested configuration or null if not found
     */
    public static @Nullable <T extends AuthenticationConfiguration<?>> T getActiveConfiguration(Class<T> clazz, int rowId)
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
    public static @Nullable <T extends AuthenticationConfiguration<?>> T getConfiguration(Class<T> clazz, int rowId)
    {
        for (T configuration : getConfigurations(clazz))
            if (configuration.getRowId() == rowId)
                return configuration;

        return null;
    }

    public static void clear()
    {
        CACHE.remove(CACHE_KEY);
    }

    /**
     * Return a collection of authentication configurations that claim the specified domain
     */
    public static @NotNull Collection<AuthenticationConfiguration> getActiveConfigurationsForDomain(String domain)
    {
        return CACHE.get(CACHE_KEY).getActiveConfigurationsForDomain(domain);
    }

    /**
     * Return a collection of all email domains (not including "*" or null) associated with active authentication configurations
     */
    public static @NotNull Collection<String> getActiveDomains()
    {
        return CACHE.get(CACHE_KEY).getActiveDomains();
    }
}
