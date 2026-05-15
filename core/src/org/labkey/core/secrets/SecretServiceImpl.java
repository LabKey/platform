package org.labkey.core.secrets;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.secrets.ExternalSecretProvider;
import org.labkey.api.secrets.SecretProperty;
import org.labkey.api.secrets.SecretService;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SecretServiceImpl implements SecretService
{
    private static final Logger LOG = LogHelper.getLogger(SecretServiceImpl.class, "Secret service");
    static final String STARTUP_PROPERTY_SCOPE = "secret";

    // Secrets loaded from startup properties / environment variables
    private final Map<String, String> _startupSecrets = new ConcurrentHashMap<>();
    // Registered SecretProperty instances keyed by property name (for documentation and env filtering)
    private final Map<String, SecretProperty> _registeredSecrets = new ConcurrentHashMap<>();

    private volatile ExternalSecretProvider _externalProvider = null;

//    // Refresh timer — used when an ExternalSecretProvider is registered
//    private final ScheduledExecutorService _scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
//        Thread t = new Thread(r, "SecretService-refresh");
//        t.setDaemon(true);
//        return t;
//    });
//    private volatile ScheduledFuture<?> _refreshFuture = null;

    /**
     * Register a LenientStartupPropertyHandler to capture all "secret.*" entries from
     * startup property files and their corresponding environment variables. Call this
     * from CoreModule.init() after constructing and registering the service.
     */
    public void handleStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(
            new LenientStartupPropertyHandler<>(STARTUP_PROPERTY_SCOPE, _SecretDocProperty.INSTANCE)
            {
                @Override
                public void handle(Collection<StartupPropertyEntry> entries)
                {
                    entries.forEach(entry -> _startupSecrets.put(entry.getName(), entry.getValue()));
                    for (var name : _registeredSecrets.keySet())
                    {
                        var s = System.getenv(name);
                        if (StringUtils.isNotBlank(s))
                            _startupSecrets.put(name,s);
                    }
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

        // External provider (e.g., SSM) has highest priority
        ExternalSecretProvider provider = _externalProvider;
        if (provider != null)
        {
            String value = provider.getSecret(name);
            if (value != null)
            {
                LOG.debug("Secret '{}' resolved from external provider", name);
                return value;
            }
        }

        String value = _startupSecrets.get(name);
        if (value != null)
            LOG.debug("Secret '{}' resolved from startup properties", name);
        return value;
    }

    @Override
    public boolean isRegisteredSecret(@NotNull String name)
    {
        return _registeredSecrets.containsKey(name);
    }

    @Override
    public void setExternalProvider(@NotNull ExternalSecretProvider provider)
    {
        _externalProvider = provider;
//        scheduleRefresh();
    }

    /** Cancel the refresh timer on server shutdown. */
    public void shutdown()
    {
//        if (_refreshFuture != null)
//            _refreshFuture.cancel(false);
//        _scheduler.shutdown();
    }

//    private void scheduleRefresh()
//    {
//        if (_refreshFuture != null)
//            _refreshFuture.cancel(false);
//
//        _refreshFuture = _scheduler.scheduleAtFixedRate(() -> {
//            ExternalSecretProvider p = _externalProvider;
//            if (p != null)
//                p.refreshAll(_registeredSecrets.keySet());
//        }, 5, 5, TimeUnit.MINUTES);
//    }

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
            SecretServiceImpl svc = new SecretServiceImpl();
            svc._startupSecrets.put("test.API_KEY", "abc123");

            SecretProperty prop = new SecretProperty("test.API_KEY", "Test API Key");
            svc.register(prop);

            assertEquals("abc123", svc.getSecret(prop));
        }

        @Test
        public void testUnregisteredSecretReturnsNull()
        {
            // getSecret() requires the same instance passed to register(); a fresh instance with
            // the same name must not be able to retrieve a secret it didn't declare.
            SecretServiceImpl svc = new SecretServiceImpl();
            SecretProperty prop = new SecretProperty("some.KEY");
            svc._startupSecrets.put("some.KEY", "value");
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
            SecretServiceImpl svc = new SecretServiceImpl();
            svc._startupSecrets.put("my.KEY", "from-startup");

            svc.setExternalProvider(new ExternalSecretProvider()
            {
                @Override
                public @Nullable String getSecret(String propertyName)
                {
                    return "my.KEY".equals(propertyName) ? "from-external" : null;
                }

                @Override
                public void refreshAll(Collection<String> propertyNames) {}
            });

            SecretProperty prop = new SecretProperty("my.KEY");
            svc.register(prop);
            assertEquals("from-external", svc.getSecret(prop));
            svc.shutdown();
        }
    }
}
