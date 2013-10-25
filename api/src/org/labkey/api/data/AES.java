package org.labkey.api.data;

import org.labkey.api.security.Encryption;

/**
* User: adam
* Date: 10/25/13
* Time: 12:18 AM
*/

/*
    First reference to PropertyManager constructs the stores which causes initialization of the PropertyEncryption enum.
    The AES128 enum requires the property manager (we store the standard salt in properties), so we use a holder pattern
    (instead of normal static initialization) to implement thread-safe lazy initialization, breaking the loop.
*/
class AES
{
    private static class AESHolder
    {
        static final AES INSTANCE = new AES();
    }

    private final Encryption.Algorithm _aes;

    AES()
    {
        _aes = Encryption.getAES128();
    }

    Encryption.Algorithm getAES()
    {
        return _aes;
    }

    static Encryption.Algorithm get()
    {
        return AESHolder.INSTANCE.getAES();
    }
}
