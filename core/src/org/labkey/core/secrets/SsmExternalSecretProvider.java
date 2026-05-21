package org.labkey.core.secrets;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.secrets.ExternalSecretProvider;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * ExternalSecretProvider that delegates to SsmSecretBridge in the Spring Boot (parent)
 * classloader. This allows the webapp to look up runtime secrets from AWS SSM Parameter
 * Store at runtime without a direct dependency on the embedded module or the AWS SDK.
 *
 * <p>The bridge class ({@code org.labkey.embedded.SsmSecretBridge}) lives in the parent
 * classloader and is found by reflection. When running outside embedded mode (standalone
 * Tomcat) or when SSM is not configured, {@link #createIfAvailable()} returns null and
 * no external provider is registered.
 *
 * <p>SSM parameter names are constructed by the bridge as {@code {prefix}{propertyName}},
 * where the prefix defaults to {@code /labkey/} and is configurable via
 * {@code context.awsParameterStore.prefix} in {@code application.properties}.
 */
public class SsmExternalSecretProvider implements ExternalSecretProvider
{
    private static final String BRIDGE_CLASS = "org.labkey.embedded.SsmSecretBridge";

    private final Method _getSecretMethod;

    private SsmExternalSecretProvider(Method getSecretMethod)
    {
        _getSecretMethod = getSecretMethod;
    }

    /**
     * Returns a new instance if the SsmSecretBridge class is reachable via the parent
     * classloader, or null when not running in embedded mode.
     */
    public static @Nullable SsmExternalSecretProvider createIfAvailable()
    {
        try
        {
            ClassLoader parent = Thread.currentThread().getContextClassLoader().getParent();
            if (parent == null)
                return null;
            Class<?> bridgeClass = parent.loadClass(BRIDGE_CLASS);
            Method method = bridgeClass.getMethod("getSecret", String.class);
            return new SsmExternalSecretProvider(method);
        }
        catch (ClassNotFoundException | NoSuchMethodException e)
        {
            return null;
        }
    }

    @Override
    public @Nullable String getSecret(String propertyName)
    {
        try
        {
            return (String) _getSecretMethod.invoke(null, propertyName);
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }

    @Override
    public void refreshAll(Collection<String> propertyNames)
    {
        // SSM is called on each getSecret() call; caching and refresh can be added later
        // when SecretServiceImpl's refresh timer is enabled
    }
}
