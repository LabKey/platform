package org.labkey.api.security;

import org.labkey.api.security.Encryption.Algorithm;

import static org.labkey.api.security.AuthenticationManager.ENCRYPTION_MIGRATION_HANDLER;

// This is a separate class to help break initialization ordering issues. Previous loop, for example: CoreModule.init()
// invokes AuthenticationManager which initializes AES which attempts to register a WarningProvider with a service that
// hasn't been initialized yet.
class AES
{
    private static final Algorithm AES = Encryption.getAES128(ENCRYPTION_MIGRATION_HANDLER);

    static Algorithm get()
    {
        return AES;
    }
}
