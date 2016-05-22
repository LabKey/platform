package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.MultiValueMap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by adam on 5/20/2016.
 */
public class AuthenticationProviderCache
{
    // We have just a single object to cache (a global AuthenticationProviderCollection), but use standard cache (blocking cache wrapping the
    // shared cache) for convenience and to ensure that configuration changes will get propagated once multiple application servers are supported.
    private static final BlockingCache<String, AuthenticationProviderCollection> CACHE = new BlockingStringKeyCache<>(CacheManager.getSharedCache(), (key, argument) -> new AuthenticationProviderCollection());
    private static final String CACHE_KEY = AuthenticationProviderCache.class.getName();

    static
    {
        // Cache AuthenticationProviderCollection forever
        CACHE.setCacheTimeChooser((key, argument) -> CacheManager.YEAR);
    }

    private static class AuthenticationProviderCollection
    {
        private final MultiValueMap<Class<? extends AuthenticationProvider>, AuthenticationProvider> _allMap = new MultiValueMap<Class<? extends AuthenticationProvider>, AuthenticationProvider>(new LinkedHashMap<>()) {
            @Override
            protected Collection<AuthenticationProvider> createValueCollection()
            {
                return new LinkedHashSet<>();
            }
        };

        private final MultiValueMap<Class<? extends AuthenticationProvider>, AuthenticationProvider> _activeMap = new MultiValueMap<Class<? extends AuthenticationProvider>, AuthenticationProvider>(new LinkedHashMap<>()) {
            @Override
            protected Collection<AuthenticationProvider> createValueCollection()
            {
                return new LinkedHashSet<>();
            }
        };

        private AuthenticationProviderCollection()
        {
            List<AuthenticationProvider> allProviders = AuthenticationManager.getAllProviders();
            Set<String> activeNames = AuthenticationManager.getActiveProviderNames();

            // For now, auth providers are always handled in order of registration: LDAP, OpenSSO, DB. TODO: Provide admin with mechanism for ordering
            for (AuthenticationProvider provider : allProviders)
            {
                addToMap(_allMap, provider);

                // Add all permanent & active providers to the active providers list
                if (provider.isPermanent() || activeNames.contains(provider.getName()))
                    addToMap(_activeMap, provider);
            }
        }

        private void addToMap(MultiValueMap<Class<? extends AuthenticationProvider>, AuthenticationProvider> map, AuthenticationProvider provider)
        {
            AuthenticationProvider.ALL_PROVIDER_INTERFACES
                .stream()
                .filter(providerClass -> providerClass.isInstance(provider))
                .forEach(providerClass -> map.put(providerClass, provider));
        }

        private <T extends AuthenticationProvider> Collection<T> getAll(Class<T> clazz)
        {
            Collection<T> providers = (Collection<T>) _allMap.get(clazz);

            return null != providers ? providers : Collections.emptyList();
        }

        private <T extends AuthenticationProvider> Collection<T> getActive(Class<T> clazz)
        {
            Collection<T> providers = (Collection<T>) _activeMap.get(clazz);

            return null != providers ? providers : Collections.emptyList();
        }
    }

    /**
     * Get all registered providers (whether active or not) that implement the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return A collection of the requested providers
     */
    static <T extends AuthenticationProvider> Collection<T> getProviders(Class<T> clazz)
    {
        return CACHE.get(CACHE_KEY).getAll(clazz);
    }

    /**
     * Get the registered provider with the specified name that implements the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return The requested provider or null if not found
     */
    static @Nullable <T extends AuthenticationProvider> T getProvider(Class<T> clazz, String name)
    {
        for (T provider : getProviders(clazz))
            if (provider.getName().equals(name))
                return provider;

        return null;
    }

    /**
     * Get all active providers that implement the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return A collection of the requested providers
     */
    static <T extends AuthenticationProvider> Collection<T> getActiveProviders(Class<T> clazz)
    {
        return CACHE.get(CACHE_KEY).getActive(clazz);
    }

    /**
     * Get the active provider with the specified name that implements the specified interface
     * @param clazz The interface to use as a filter
     * @param <T> The interface type
     * @return The requested provider or null if not found
     */
    static @Nullable <T extends AuthenticationProvider> T getActiveProvider(Class<T> clazz, String name)
    {
        for (T provider : getActiveProviders(clazz))
            if (provider.getName().equals(name))
                return provider;

        return null;
    }

    static void clear()
    {
        CACHE.remove(CACHE_KEY);
    }
}
