package org.labkey.api.security;

import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.AbstractSetValuedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

            // Add all the configurations listed in the core.AuthenticationConfigurations table
            new TableSelector(CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), null, new Sort("SortOrder"))
                .forEachMap(m -> {
                    String providerName = (String) m.get("Provider");
                    PrimaryAuthenticationProvider provider = AuthenticationProviderCache.getProvider(PrimaryAuthenticationProvider.class, providerName);
                    addConfiguration(provider, m, acceptOnlyFicamProviders);
                });

            // Add all the "permanent" configurations -- this should be just a single configuration for Database authentication
            AuthenticationManager.getAllPrimaryProviders().stream()
                .filter(AuthenticationProvider::isPermanent)
                .forEach(p->addConfiguration(p, null, acceptOnlyFicamProviders));
        }

        private void addConfiguration(PrimaryAuthenticationProvider provider, Map<String, Object> map, boolean acceptOnlyFicamProviders)
        {
            if (null != provider && (!acceptOnlyFicamProviders || provider.isFicamApproved()))
            {
                AuthenticationConfiguration configuration = provider.getAuthenticationConfiguration(map);

                addToMap(_allMap, configuration);

                if (configuration.isEnabled())
                    addToMap(_activeMap, configuration);
            }
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
    public static @NotNull <T extends AuthenticationConfiguration> Collection<T> getConfigurations(Class<T> clazz)
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
            if (configuration.getRowId().equals(rowId))
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
            if (configuration.getRowId().equals(rowId))
                return configuration;

        return null;
    }

    static void clear()
    {
        CACHE.remove(CACHE_KEY);
    }
}
