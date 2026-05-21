package org.labkey.core.secrets;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.secrets.SecretProperty;
import org.labkey.api.secrets.SecretProvider;
import org.labkey.api.secrets.SecretService;
import org.labkey.api.secrets.SecretStatus;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SecretServiceImpl implements SecretService
{
    private static final Logger LOG = LogHelper.getLogger(SecretServiceImpl.class, "Secret service");
    static final String STARTUP_PROPERTY_SCOPE = "secret";

    // Registered SecretProperty instances keyed by property name
    private final Map<String, SecretProperty> _registeredSecrets = new ConcurrentHashMap<>();

    // Providers consulted in priority order: external (highest) → env vars → startup properties
    private volatile SecretProvider _externalProvider = null;
    private final SecretProvider _envProvider = new EnvironmentVariableSecretProvider();
    private final StartupPropertySecretProvider _startupProvider;

    public SecretServiceImpl()
    {
        this(new StartupPropertySecretProvider());
    }

    private SecretServiceImpl(StartupPropertySecretProvider startupProvider)
    {
        _startupProvider = startupProvider;
    }

    /**
     * Register a LenientStartupPropertyHandler to capture all "secret.*" entries from
     * startup property files. Call this from CoreModule.init() after constructing and
     * registering the service.
     */
    public void handleStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(
            new LenientStartupPropertyHandler<>(STARTUP_PROPERTY_SCOPE, _SecretDocProperty.INSTANCE)
            {
                @Override
                public void handle(Collection<StartupPropertyEntry> entries)
                {
                    _startupProvider.load(entries);
                }
            });
    }

    @Override
    public void register(@NotNull SecretProperty property)
    {
        var prev = _registeredSecrets.put(property.getPropertyName(), property);
        if (null != prev)
            throw new ConfigurationException("Duplicate secret registered: " + property.getPropertyName());
    }

    @Override
    public @Nullable String getSecret(@NotNull SecretProperty property)
    {
        String name = property.getPropertyName();
        var registered = _registeredSecrets.get(name);
        if (registered != property)
            return null;

        for (SecretProvider provider : activeProviders())
        {
            String value = provider.getSecret(name);
            if (value != null)
            {
                LOG.debug("Secret '{}' resolved from {}", name, provider.getDescription());
                return value;
            }
        }
        return null;
    }

    @Override
    public boolean isRegisteredSecret(@NotNull String name)
    {
        return _registeredSecrets.containsKey(name);
    }

    @Override
    public void setExternalProvider(@NotNull SecretProvider provider)
    {
        _externalProvider = provider;
    }

    @Override
    public @NotNull List<SecretStatus> getSecretStatuses()
    {
        return _registeredSecrets.values().stream()
            .filter(s -> !Objects.isNull(s.getPropertyName()))
            .sorted(Comparator.comparing(SecretProperty::getPropertyName))
            .map(prop -> {
                String name = prop.getPropertyName();
                String source = activeProviders().stream()
                    .filter(p -> p.getSecret(name) != null)
                    .map(SecretProvider::getDescription)
                    .findFirst()
                    .orElse(null);
                return new SecretStatus(name, prop.getDescription(), source);
            })
            .toList();
    }

    @Override
    public @Nullable String getExternalProviderDescription()
    {
        SecretProvider provider = _externalProvider;
        return provider != null ? provider.getDescription() : null;
    }

    public void shutdown() {}

    private List<SecretProvider> activeProviders()
    {
        SecretProvider external = _externalProvider;
        return external != null
            ? List.of(external, _envProvider, _startupProvider)
            : List.of(_envProvider, _startupProvider);
    }

    // Singleton SecretProperty used as the documentation entry for the "secret" scope on the
    // Startup Properties admin page. The scope name is "secret" and the property name is "*"
    // to indicate that any property name is accepted under this scope.
    private static class _SecretDocProperty extends SecretProperty
    {
        static final _SecretDocProperty INSTANCE = new _SecretDocProperty();

        private _SecretDocProperty()
        {
            super(STARTUP_PROPERTY_SCOPE, "Any secret registered via SecretService.register(). " +
                "Provide as: secret.<propertyName>=<value>");
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testStartupPropertyLoading()
        {
            SecretServiceImpl svc = new SecretServiceImpl(startupProviderWith("test.API_KEY", "abc123"));

            SecretProperty prop = new SecretProperty("test.API_KEY", "Test API Key");
            svc.register(prop);

            assertEquals("abc123", svc.getSecret(prop));
        }

        @Test
        public void testUnregisteredSecretReturnsNull()
        {
            // getSecret() requires the same instance passed to register(); a fresh instance with
            // the same name must not be able to retrieve a secret it didn't declare.
            SecretServiceImpl svc = new SecretServiceImpl(startupProviderWith("some.KEY", "value"));
            SecretProperty prop = new SecretProperty("some.KEY");
            assertNull("unregistered property must not retrieve a secret", svc.getSecret(prop));
        }

        @Test
        public void testRegisteredSecretWithNoValueReturnsNull()
        {
            SecretServiceImpl svc = new SecretServiceImpl();
            SecretProperty prop = new SecretProperty("nonexistent.KEY");
            svc.register(prop);
            assertNull(svc.getSecret(prop));
        }

        @Test
        public void testExternalProviderPriority()
        {
            SecretServiceImpl svc = new SecretServiceImpl(startupProviderWith("my.KEY", "from-startup"));

            svc.setExternalProvider(new SecretProvider()
            {
                @Override
                public @Nullable String getSecret(String propertyName)
                {
                    return "my.KEY".equals(propertyName) ? "from-external" : null;
                }

                @Override
                public @NotNull String getDescription()
                {
                    return "Test provider";
                }
            });

            SecretProperty prop = new SecretProperty("my.KEY");
            svc.register(prop);
            assertEquals("from-external", svc.getSecret(prop));
        }

        private StartupPropertySecretProvider startupProviderWith(String name, String value)
        {
            StartupPropertySecretProvider provider = new StartupPropertySecretProvider();
            provider.loadDirect(name, value);
            return provider;
        }
    }
}
