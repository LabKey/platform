package org.labkey.api.util;

import org.labkey.api.secrets.SecretService;
import org.labkey.api.services.ServiceRegistry;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wrapper for {@link ProcessBuilder} that removes secret-named environment variables
 * from the subprocess environment before the process starts. This prevents secrets from leaking
 * to untrusted child processes.
 *
 * <p>Variables are removed (silently) if their name:
 * <ul>
 *   <li>matches any property name registered with {@link SecretService}, or</li>
 *   <li>contains (case-insensitive) any of "secret", "password", "apikey", "_key", or "token".</li>
 * </ul>
 *
 * <p>Use this class wherever {@link ProcessBuilder} would otherwise be used. An IntelliJ inspection
 * (SSBasedInspection) flags direct instantiation of {@code java.lang.ProcessBuilder} as a reminder.
 */
public class LabKeyProcessBuilder
{
    private final ProcessBuilder _pb;

    public LabKeyProcessBuilder(List<String> command)
    {
        //noinspection SSBasedInspection
        _pb = new ProcessBuilder(command);
        sanitizeEnvironment();
    }

    public LabKeyProcessBuilder(String... command)
    {
        //noinspection SSBasedInspection
        _pb = new ProcessBuilder(command);
        sanitizeEnvironment();
    }

    public LabKeyProcessBuilder(File directory, List<String> command)
    {
        //noinspection SSBasedInspection
        _pb = new ProcessBuilder(command);
        _pb.directory(directory);
        sanitizeEnvironment();
    }

    public List<String> command()
    {
        return _pb.command();
    }

    public Map<String, String> environment()
    {
        return _pb.environment();
    }

    public File directory()
    {
        return _pb.directory();
    }

    public LabKeyProcessBuilder directory(File directory)
    {
        _pb.directory(directory);
        return this;
    }

    public LabKeyProcessBuilder redirectErrorStream(boolean redirectErrorStream)
    {
        _pb.redirectErrorStream(redirectErrorStream);
        return this;
    }

    public Process start() throws IOException
    {
        return _pb.start();
    }

    /** Returns the underlying {@link ProcessBuilder} for APIs that require it directly. */
    public ProcessBuilder processBuilder()
    {
        return _pb;
    }

    private void sanitizeEnvironment()
    {
        _pb.environment().keySet().removeIf(LabKeyProcessBuilder::isSecret);
    }

    /** @return true if the property name is known to be or inferred to be a secret (credential, etc) */
    public static boolean isSecret(String propertyName)
    {
        SecretService secrets = ServiceRegistry.get().getService(SecretService.class);
        String lc = propertyName.toLowerCase(Locale.ROOT);
        return lc.contains("secret") || lc.contains("password") || lc.contains("apikey") || lc.contains("_key") || lc.contains("token") ||
                (secrets != null && secrets.isRegisteredSecret(propertyName));
    }

    public LabKeyProcessBuilder redirectOutput(ProcessBuilder.Redirect redirect)
    {
        _pb.redirectOutput(redirect);
        return this;
    }
}
