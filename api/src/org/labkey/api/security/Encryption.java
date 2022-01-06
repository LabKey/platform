/*
 * Copyright (c) 2013-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletContext;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easy to use wrappers for common encryption algorithms. Also includes related helper methods for shared operations
 * such as generating salts & keys, and for retrieving & saving the labkey.xml encryption key and standard salt.
 *
 * WARNING: Do not change the core algorithms or parameters of existing implementations; changes will likely
 * render existing data irrecoverable.
 * User: adam
 * Date: 10/19/13
*/

public class Encryption
{
    private static final String CATEGORY = "Encryption";
    private static final String SALT_KEY = "Salt";
    private static final SecureRandom SR = new SecureRandom();
    private static final String ENCRYPTION_PASS_PHRASE;

    static
    {
        ENCRYPTION_PASS_PHRASE = loadEncryptionPassPhrase();

        WarningService.get().register(new WarningProvider() {
            @Override
            public void addDynamicWarnings(@NotNull Warnings warnings, @NotNull ViewContext context)
            {
                int count = DECRYPTION_EXCEPTIONS.get();

                if (count > 0)
                    warnings.add(HtmlString.of("On " + StringUtilsLabKey.pluralize(count, "attempt") + " the server failed to decrypt encrypted content using the " + ENCRYPTION_KEY_CHANGED +
                        " An administrator should change the encryption key back to the previous value or be prepared to re-enter and re-save all saved credentials."));
            }
        });
    }

    private Encryption()
    {
    }

    // Generate an array of random bytes of the specified length using SecureRandom
    private static byte[] generateRandomBytes(int byteCount)
    {
        byte[] bytes = new byte[byteCount];
        SR.nextBytes(bytes);

        return bytes;
    }


    // Generates an encryption key having the specified bit length from a pass phrase, using PKCS #5 v2.0. This algorithm
    // uses the lower 8-bits of each character to generate the key, which is appropriate for ASCII pass phrases.
    private static byte[] generateSecretKeyFromPassPhrase(String passPhrase, byte[] salt, int keyLength, int iterationCount) throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount, keyLength);
        SecretKey skey = factory.generateSecret(spec);

        return skey.getEncoded();
    }


    // Generates an encryption key having the specified bit length from a pass phrase, using PKCS #5 v2.0. Uses standard salt and iteration count of 65,536.
    private static byte[] generateSecretKeyFromPassPhrase(String passPhrase, int keyLength) throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        return generateSecretKeyFromPassPhrase(passPhrase, getStandardSalt(), keyLength, 65536);
    }


    // Returns a standard 16-byte random salt, generated once and unique to this database.
    private static byte[] getStandardSalt()
    {
        PropertyStore store = PropertyManager.getNormalStore();
        Map<String, String> props = store.getProperties(CATEGORY);
        String salt = props.get(SALT_KEY);

        if (null != salt)
            return Base64.decodeBase64(salt);

        PropertyMap map = store.getWritableProperties(CATEGORY, true);
        byte[] bytes = generateRandomBytes(16);
        map.put(SALT_KEY, Base64.encodeBase64String(bytes));

        // Seems very unlikely that we'd attempt to read encrypted data before we'd write any encrypted data on a production
        // server, but it could happen during development. Allow this mutating ensure operation during a GET.
        try (var ignored = SpringActionController.ignoreSqlUpdates())
        {
            map.save();
        }

        return bytes;
    }


    private static final String ENCRYPTION_KEY_PARAMETER_NAME = "EncryptionKey";
    private static final String OLD_ENCRYPTION_KEY_PARAMETER_NAME = "MasterEncryptionKey";

    private static @Nullable String loadEncryptionPassPhrase()
    {
        ServletContext context = ModuleLoader.getServletContext();

        if (null == context)
            throw new IllegalStateException("ServletContext is null");

        String encryptionKey = context.getInitParameter(ENCRYPTION_KEY_PARAMETER_NAME);

        // Backward compatibility -- look for old parameter name if new one is missing
        if (null == encryptionKey)
            encryptionKey = context.getInitParameter(OLD_ENCRYPTION_KEY_PARAMETER_NAME);

        // Return the encryption key if it's there (not null, not blank, not whitespace, not default value), otherwise return null
        if (!StringUtils.isBlank(encryptionKey) && !encryptionKey.trim().equals("@@masterEncryptionKey@@") && !encryptionKey.trim().equals("@@encryptionKey@@"))
            return encryptionKey;
        else
            return null;
    }

    public static @Nullable String getEncryptionPassPhrase()
    {
        return ENCRYPTION_PASS_PHRASE;
    }

    public static boolean isEncryptionPassPhraseSpecified()
    {
        return null != getEncryptionPassPhrase();
    }


    public interface Algorithm
    {
        @NotNull byte[] encrypt(@NotNull String plainText);
        @NotNull String decrypt(@NotNull byte[] cipherText);
    }


    /*
        Wrapper class that makes it easier to encrypt/decrypt using AES and a pass phrase.

        Encryption: AES
        Mode of operation: CBC
        Padding: PKCS #5
        Initialization vector: random 16-byte IV, generated for each encryption

        Key generation: PKCS #5 v2.0
        Salt: Standard server salt
        Iteration count: 65,536
        Key length: specified in constructor parameter
     */
    public static class AES implements Algorithm
    {
        private final @Nullable SecretKeySpec _keySpec;
        private final String _keySource;

        public AES(String passPhrase, int keyLength, String keySource)
        {
            _keySource = keySource;
            if (null == passPhrase)
                throw new IllegalStateException("Pass phrase cannot be null");

            // Turn pass phrase into a keyLength-bit key using PKCS #5 v2.0, a standard salt and 65,536 iterations
            try
            {
                byte[] key = generateSecretKeyFromPassPhrase(passPhrase, keyLength);
                _keySpec = new SecretKeySpec(key, "AES");
            }
            catch (NoSuchAlgorithmException | InvalidKeySpecException e)
            {
                throw new RuntimeException(e);
            }
        }

        @NotNull
        @Override
        public byte[] encrypt(@NotNull String plainText)
        {
            try
            {
                // Generate a random, 16-byte initialization vector (IV) for use with this one encryption
                byte[] iv = generateRandomBytes(16);

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, _keySpec, new IvParameterSpec(iv));

                // First 16 bytes is the iv, remainder is the encrypted bytes
                return ArrayUtils.addAll(iv, cipher.doFinal(plainText.getBytes(StringUtilsLabKey.DEFAULT_CHARSET)));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @NotNull
        @Override
        public String decrypt(@NotNull byte[] cipherText)
        {
            try
            {
                // Initialization vector (IV) is the first 16 bytes
                byte[] iv = ArrayUtils.subarray(cipherText, 0, 16);
                byte[] encrypted = ArrayUtils.subarray(cipherText, 16, cipherText.length);
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, _keySpec, new IvParameterSpec(iv));
                return new String(cipher.doFinal(encrypted), StringUtilsLabKey.DEFAULT_CHARSET);
            }
            catch (BadPaddingException e)
            {
                // For now, assume that BadPaddingException means the key has been changed and all other
                // exceptions are coding issues. That might change in the future...

                // Track all decryption exceptions that aren't caused by TestCase (below)
                if (ENCRYPTION_KEY_CHANGED.equals(_keySource))
                    DECRYPTION_EXCEPTIONS.incrementAndGet();

                throw new DecryptionException("Could not decrypt this content using the " + _keySource, e);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private static final String ENCRYPTION_KEY_CHANGED = "currently configured EncryptionKey; has the key changed in " + AppProps.getInstance().getWebappConfigurationFilename() + "?";
    private static final AtomicInteger DECRYPTION_EXCEPTIONS = new AtomicInteger(0);

    public static class DecryptionException extends ConfigurationException
    {
        public DecryptionException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }


    /*
        Return an encryption algorithm that uses AES and generates a 128-bit key from the labkey.xml encryption key.
        All other encryption parameters are documented in AES().
     */
    public static Algorithm getAES128()
    {
        if (isEncryptionPassPhraseSpecified())
            return new AES(getEncryptionPassPhrase(), 128, ENCRYPTION_KEY_CHANGED);
        else
            throw new IllegalStateException("EncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename() + "; this method should not be called");
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testEncryptionAlgorithms() throws NoSuchAlgorithmException
        {
            String passPhrase = "Here's my super secret pass phrase";

            Algorithm aesPassPhrase = new AES(passPhrase, 128, "test pass phrase");

            test(aesPassPhrase);

            if (isEncryptionPassPhraseSpecified())
            {
                Algorithm aes = getAES128();
                test(aes);

                // Test that static factory method matches this configuration
                Algorithm aes2 = new AES(getEncryptionPassPhrase(), 128, "test pass phrase");

                test(aes, aes2);
                test(aes2, aes);
            }

            if (Cipher.getMaxAllowedKeyLength("AES") >= 256)
            {
                test(new AES(passPhrase, 256, "test pass phrase"));
            }
        }

        @Test(expected = DecryptionException.class)
        public void testBadKeyException()
        {
            String textToEncrypt = "this is some text I want to encrypt";
            String passPhrase = "Here's my super secret pass phrase";
            String wrongPassPhrase = passPhrase + " not";

            // Our AES implementation can usually detect a bad pass phrase (based on padding anomalies), but this is not 100% guaranteed.
            // Give the test three tries... by my calculations, this will fail once in every 2.6 million runs, which we can live with.
            for (int i = 0; i < 3; i++)
            {
                Algorithm aesPassPhrase = new AES(passPhrase, 128, "test pass phrase");
                byte[] encrypted = aesPassPhrase.encrypt(textToEncrypt);

                Algorithm aesWrongPassPhrase = new AES(wrongPassPhrase, 128, "test pass phrase");
                aesWrongPassPhrase.decrypt(encrypted);
            }
        }

        private void test(Algorithm algorithm)
        {
            test(algorithm, algorithm);
        }

        private void test(Algorithm encryptAlgorithm, Algorithm decryptAlgorithm)
        {
            for (String test : new String[]{"foo", "bar", "this is some text I want to encrypt"})
                assertEquals(test, decryptAlgorithm.decrypt(encryptAlgorithm.encrypt(test)));
        }
    }
}
