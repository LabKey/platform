package org.labkey.core.secrets;

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
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    // Names of all registered SecretProperty instances (for documentation and env filtering)
    private final Set<String> _registeredNames = Collections.synchronizedSet(new HashSet<>());

    private volatile ExternalSecretProvider _externalProvider = null;

    // Refresh timer — used when an ExternalSecretProvider is registered
    private final ScheduledExecutorService _scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SecretService-refresh");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> _refreshFuture = null;

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
                }
            });
    }

    @Override
    public void register(@NotNull SecretProperty property)
    {
        _registeredNames.add(property.getPropertyName());
    }

    @Override
    public @Nullable String getSecret(@NotNull SecretProperty property)
    {
        String name = property.getPropertyName();

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
    public void setExternalProvider(@NotNull ExternalSecretProvider provider)
    {
        _externalProvider = provider;
        scheduleRefresh();
    }

    /** Cancel the refresh timer on server shutdown. */
    public void shutdown()
    {
        if (_refreshFuture != null)
            _refreshFuture.cancel(false);
        _scheduler.shutdown();
    }

    private void scheduleRefresh()
    {
        if (_refreshFuture != null)
            _refreshFuture.cancel(false);

        _refreshFuture = _scheduler.scheduleAtFixedRate(() -> {
            ExternalSecretProvider p = _externalProvider;
            if (p != null)
                p.refreshAll(Collections.unmodifiableSet(_registeredNames));
        }, 5, 5, TimeUnit.MINUTES);
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
            SecretServiceImpl svc = new SecretServiceImpl();
            svc._startupSecrets.put("test.API_KEY", "abc123");

            SecretProperty prop = new SecretProperty("test.API_KEY", "Test API Key");
            svc.register(prop);

            assertEquals("abc123", svc.getSecret(prop));
        }

        @Test
        public void testMissingSecret()
        {
            SecretServiceImpl svc = new SecretServiceImpl();
            SecretProperty prop = new SecretProperty("nonexistent.KEY");
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
            assertEquals("from-external", svc.getSecret(prop));
            svc.shutdown();
        }
    }
}
