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

import com.google.common.primitives.Bytes;
import jakarta.servlet.ServletContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider.SiteSettingsAuditEvent;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.EncryptedPropertyStore;
import org.labkey.api.data.NormalPropertyStore;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.TroubleshooterPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easy to use wrappers for common encryption algorithms. Also includes related helper methods for shared operations
 * such as generating salts & keys, and for retrieving & saving the application.properties encryption key and standard salt.
 * WARNING: Do not change the core algorithms or parameters of existing implementations; changes will likely
 * render existing data irrecoverable.
*/

public class Encryption
{
    private static final Logger LOG = LogHelper.getLogger(Encryption.class, "Encryption operations");
    private static final String CATEGORY = "Encryption";
    private static final String SALT_KEY = "Salt";
    public static final SecureRandom SR;
    private static final String ENCRYPTION_PASS_PHRASE;
    private static String KEY_CHANGE_GUIDANCE = "An administrator should change the encryption key back to the previous value, follow the official encryption key change process, or be prepared to re-enter and re-save all saved credentials.";

    static
    {
        ENCRYPTION_PASS_PHRASE = loadEncryptionPassPhrase();

        try
        {
            SR = SecureRandom.getInstanceStrong();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ConfigurationException("Could not initialize SecureRandom", e);
        }

        WarningService.get().register(new WarningProvider() {
            @Override
            public void addDynamicWarnings(@NotNull Warnings warnings, @Nullable ViewContext context, boolean showAllWarnings)
            {
                if (context == null || context.getUser().hasRootPermission(TroubleshooterPermission.class))
                {
                    if (!isEncryptionPassPhraseSpecified() || showAllWarnings)
                        warnings.add(HtmlStringBuilder.of("The encryption key property is not set in " + AppProps.getInstance().getWebappConfigurationFilename() +
                            ". An encryption key is required to save credentials used in various integrations.").append(getEncryptionKeyHelpLink()));

                    int count = DECRYPTION_EXCEPTIONS.get();

                    if (count > 0 || showAllWarnings)
                    {
                        final String who;
                        final HasHtmlString link;
                        if (context != null && context.getUser().hasSiteAdminPermission())
                        {
                            who = "you";
                            link = LinkBuilder.simpleLink("this link", Objects.requireNonNull(PageFlowUtil.urlProvider(AdminUrls.class)).getDeleteEncryptedContentURL());
                        }
                        else
                        {
                            who = "a site administrator";
                            link = HtmlStringBuilder.of("the \"delete encrypted content\" action");
                        }

                        warnings.add(HtmlStringBuilder.of("On " + StringUtilsLabKey.pluralize(count, "attempt") +
                            ", the server failed to decrypt encrypted content using the " +
                            ENCRYPTION_KEY_CHANGED + " " + KEY_CHANGE_GUIDANCE)
                                .append(" If the previous encryption key has been lost, " + who + " can clear all encrypted content via ")
                                .append(link)
                                .append(". ")
                                .append(getEncryptionKeyHelpLink()));
                    }
                }
            }

            private HtmlStringBuilder getEncryptionKeyHelpLink()
            {
                return HtmlStringBuilder.of(" For more information, see ").append(new HelpTopic("labkeyxml", "encrypt").getSimpleLinkHtml("the Encryption Key documentation")).append(".");
            }
        });
    }


    // Used to keep track of the cipher configuration we've used, allowing us to reliably upgrade from one to another
    private static final String ENCRYPTION_CIPHER_CATEGORY = "encryption-cipher";
    private static final String CIPHER_PROPERTY = "cipher";

    // Used to check the key against a testable value stored in the database
    private static final String TEST_ENCRYPTION_CATEGORY = "encryption-test";
    private static final String TEST_BYTES_NAME = "bytes";
    private static final int TEST_BYTES_LENGTH = 64; // Don't change unless you address backward compatibility
    private static final int SHA1_LENGTH = 20; // Don't change hashing algorithm unless you address backward compatibility

    public static void initEncryptionKeyTest()
    {
        // Run test on a background thread... no need to block startup on a test that might take a few seconds
        JobRunner.getDefault().execute(Encryption::testEncryptionKey);
    }

    // Proactive test of the encryption key. On first run, encrypt, encode, and store a randomly generated byte string
    // plus a SHA1 hash. On subsequent server startups, retrieve, decode, and verify contents are as expected. Any
    // decryption failure causes the warning provider (above) to display an admin warning.
    private static void testEncryptionKey()
    {
        if (isEncryptionPassPhraseSpecified())
        {
            testEncryptionKey(getEncryptionPassPhrase(), AESConfig.current, "configured encryption key", true);
        }
    }

    // Test encryption key with specific passphrase and AES config. Used for testing old keys before migration.
    private static void testEncryptionKey(String passPhrase, AESConfig config, String keyDescription, boolean createIfNotSet)
    {
        if (passPhrase != null)
        {
            LOG.info("Attempting to test the integrity of the {}", keyDescription);

            try
            {
                // Create a temporary encrypted property store using the specified passphrase and config
                Algorithm testAlgorithm = new AES(passPhrase, 128, keyDescription, config);

                // On trial deployments, encryption key can change between initial bootstrap and the "new install"
                // startup, so always delete if "newinstall" file is present. See Issue 48346.
                // Use low-level deleteSetDirectly() method to skip decryption attempt and resulting admin warnings.
                if (ModuleLoader.getInstance().isNewInstall())
                    PropertyManager.deleteSetDirectly(PropertyManager.SHARED_USER, ContainerManager.getRoot().getId(), TEST_ENCRYPTION_CATEGORY, (EncryptedPropertyStore)PropertyManager.getEncryptedStore());

                // Get the raw values from the store so we can attempt decrypt with the old setup
                PropertyManager.PropertyMap rawMap = new NormalPropertyStore()
                {
                    @Override
                    protected boolean isValidPropertyMap(PropertyManager.PropertyMap props)
                    {
                        return true;
                    }
                }.getProperties(TEST_ENCRYPTION_CATEGORY);


                if (rawMap.isEmpty())
                {
                    if (createIfNotSet)
                    {
                        WritablePropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(TEST_ENCRYPTION_CATEGORY, true);
                        byte[] randomBytes = generateRandomBytes(TEST_BYTES_LENGTH);
                        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
                        byte[] hash = sha1.digest(randomBytes);
                        assert hash.length == SHA1_LENGTH;
                        byte[] combined = Bytes.concat(randomBytes, hash);
                        String base64 = Base64.encodeBase64String(combined);
                        map.put(TEST_BYTES_NAME, base64);
                        map.save();
                        LOG.info("Encryption key test property was generated, encrypted, and stored. It will be tested on subsequent server startups.");
                    }
                }
                else
                {
                    String rawEncryptedValue = rawMap.get(TEST_BYTES_NAME);
                    String decrypted = testAlgorithm.decrypt(Base64.decodeBase64(rawEncryptedValue));

                    byte[] combined = Base64.decodeBase64(decrypted); // This doesn't seem to throw, no matter what garbage you throw at it
                    if (combined.length != TEST_BYTES_LENGTH + SHA1_LENGTH)
                    {
                        // Base64 decoding problem -- log it and treat as a decryption failure
                        LOG.error("Encryption key test failed: Base64 decoding failed");
                        logFailureGuidance();
                        DECRYPTION_EXCEPTIONS.incrementAndGet();
                    }
                    else
                    {
                        byte[] storedHash = Arrays.copyOfRange(combined, TEST_BYTES_LENGTH, TEST_BYTES_LENGTH + SHA1_LENGTH);
                        byte[] randomBytes = Arrays.copyOf(combined, TEST_BYTES_LENGTH);
                        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
                        byte[] hash = sha1.digest(randomBytes);
                        if (Arrays.equals(storedHash, hash))
                        {
                            LOG.info("Encryption key test succeeded");
                        }
                        else
                        {
                            // Hashes didn't match -- log it and treat as a decryption failure
                            LOG.error("Encryption key test failed: SHA1 hashes did not match");
                            logFailureGuidance();
                            DECRYPTION_EXCEPTIONS.incrementAndGet();
                        }
                    }
                }
            }
            catch (DecryptionException de)
            {
                LOG.error("Encryption key test failed: decryption of test property failed", de);
                DECRYPTION_EXCEPTIONS.incrementAndGet();
                logFailureGuidance();
            }
            catch (NoSuchAlgorithmException ae)
            {
                // All unexpected exceptions are handled by the JobRunner
                throw new RuntimeException(ae);
            }
        }
    }

    private static void logFailureGuidance()
    {
        LOG.error("{} For more information, see {}.", KEY_CHANGE_GUIDANCE, new HelpTopic("labkeyxml", "encrypt").getHelpTopicHref());
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

        WritablePropertyMap map = store.getWritableProperties(CATEGORY, true);
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
    private static final String DEPRECATED_ENCRYPTION_KEY_PARAMETER_NAME = "MasterEncryptionKey";
    private static final String OLD_ENCRYPTION_KEY_PARAMETER_NAME = "OldEncryptionKey";

    private static @Nullable String loadEncryptionProperty(String... propertyNames)
    {
        ServletContext context = ModuleLoader.getServletContext();

        if (null == context)
            throw new IllegalStateException("ServletContext is null");

        String propertyValue = null;

        for (String name : propertyNames)
        {
            propertyValue = context.getInitParameter(name);
            if (null != propertyValue)
                break;
        }

        return propertyValue;
    }

    private static @Nullable String loadEncryptionPassPhrase()
    {
        String encryptionKey = loadEncryptionProperty(ENCRYPTION_KEY_PARAMETER_NAME, DEPRECATED_ENCRYPTION_KEY_PARAMETER_NAME);

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

    public static @Nullable String getOldEncryptionPassPhrase()
    {
        return loadEncryptionProperty(OLD_ENCRYPTION_KEY_PARAMETER_NAME);
    }

    public static boolean isEncryptionPassPhraseSpecified()
    {
        return null != getEncryptionPassPhrase();
    }

    public interface Algorithm
    {
        byte @NotNull[] encrypt(@NotNull String plainText);
        @NotNull String decrypt(byte @NotNull[] cipherText);
    }

    public enum AESConfig
    {
        /** Our preferred config */
        current(12, "AES/GCM/NoPadding")
                {
                    @Override
                    protected AlgorithmParameterSpec createIvSpec(byte[] iv)
                    {
                        return new GCMParameterSpec(128, iv);
                    }
                },
        /** Old config needed for upgrading existing values */
        legacy(16, "AES/CBC/PKCS5Padding")
                {
                    @Override
                    protected AlgorithmParameterSpec createIvSpec(byte[] iv)
                    {
                        return new IvParameterSpec(iv);
                    }
                };

        private final int _ivLength;
        private final String _cipherName;

        AESConfig(int ivLength, String cipherName)
        {
            _ivLength = ivLength;
            _cipherName = cipherName;
        }

        public int getIvLength()
        {
            return _ivLength;
        }

        public String getCipherName()
        {
            return _cipherName;
        }

        protected abstract AlgorithmParameterSpec createIvSpec(byte[] iv);
    }

    /*
        Wrapper class that makes it easier to encrypt/decrypt using AES and a pass phrase.
        Salt: Standard server salt
        Iteration count: 65,536
        Key length: specified in constructor parameter
     */
    public static class AES implements Algorithm
    {
        private final @Nullable SecretKeySpec _keySpec;
        private final String _keySource;
        private final AESConfig _config;

        public AES(String passPhrase, int keyLength, String keySource)
        {
            this(passPhrase, keyLength, keySource, AESConfig.current);
        }

        public AES(String passPhrase, int keyLength, String keySource, AESConfig config)
        {
            _keySource = keySource;
            _config = config;
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

        @Override
        public byte @NotNull[] encrypt(@NotNull String plainText)
        {
            try
            {
                // Generate a random initialization vector (IV) for use with this one encryption
                byte[] iv = generateRandomBytes(_config.getIvLength());

                Cipher cipher = Cipher.getInstance(_config.getCipherName());
                cipher.init(Cipher.ENCRYPT_MODE, _keySpec, _config.createIvSpec(iv));

                // First bytes is the iv, remainder is the encrypted bytes
                return ArrayUtils.addAll(iv, cipher.doFinal(plainText.getBytes(StringUtilsLabKey.DEFAULT_CHARSET)));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @NotNull
        @Override
        public String decrypt(byte @NotNull[] cipherText)
        {
            try
            {
                // Initialization vector (IV) is the first bytes according to config
                int ivLength = _config.getIvLength();
                byte[] iv = ArrayUtils.subarray(cipherText, 0, ivLength);
                byte[] encrypted = ArrayUtils.subarray(cipherText, ivLength, cipherText.length);
                Cipher cipher = Cipher.getInstance(_config.getCipherName());
                cipher.init(Cipher.DECRYPT_MODE, _keySpec, _config.createIvSpec(iv));
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

    /**
     * Return standard AES encryption algorithm. Generates a 128-bit key from the application.properties encryption key.
     * All other encryption parameters are documented in AES(). Pass in a registered EncryptionMigrationHandler to prove
     * that you can migrate your encrypted content.
     */
    public static Algorithm getAES128(EncryptionMigrationHandler handler)
    {
        // Ensure that every user of AES128 has registered an EncryptionMigrationHandler
        assert null != handler && (EncryptionMigrationHandler.HANDLERS.contains(handler) || handler == TEST_HANDLER);

        if (isEncryptionPassPhraseSpecified())
            return new AES(getEncryptionPassPhrase(), 128, ENCRYPTION_KEY_CHANGED);
        else
            throw new IllegalStateException("EncryptionKey has not been specified in " + AppProps.getInstance().getWebappConfigurationFilename() + "; this method should not be called");
    }

    /**
     * Same as above, but caller specifies the pass phrase and AES configuration. Used for migrating encrypted content 
     * from one AES configuration to another.
     */
    public static Algorithm getAES128(String encryptionPassPhrase, String keySource, AESConfig config)
    {
        return new AES(encryptionPassPhrase, 128, keySource, config);
    }

    public interface EncryptionMigrationHandler
    {
        Set<EncryptionMigrationHandler> HANDLERS = new ConcurrentHashSet<>();

        static void registerHandler(EncryptionMigrationHandler handler)
        {
            HANDLERS.add(handler);
        }

        String getDescription();

        void migrateEncryptedContent(String oldPassPhrase, String keySource, AESConfig oldConfig);

        void deleteEncryptedContent();
    }

    public static void checkMigration()
    {
        String oldPassPhrase = getOldEncryptionPassPhrase();
        AESConfig oldConfig = AESConfig.current;

        if (isEncryptionPassPhraseSpecified() && ModuleLoader.getInstance().shouldInsertData())
        {
            boolean migrationNeeded = false;
            String keySource = null;

            if (null != oldPassPhrase)
            {
                keySource = "OldEncryptionKey specified in " + AppProps.getInstance().getWebappConfigurationFilename();
                LOG.info("{}. Attempting to migrate existing encrypted content from OldEncryptionKey to EncryptionKey.", keySource);
                migrationNeeded = true;
                KEY_CHANGE_GUIDANCE = KEY_CHANGE_GUIDANCE + " Note that if a migration has already been performed, the OldEncryptionKey value should be removed.";
            }

            PropertyManager.WritablePropertyMap cipherProps = PropertyManager.getNormalStore().getWritableProperties(ENCRYPTION_CIPHER_CATEGORY, true);
            String cipher = cipherProps.get(CIPHER_PROPERTY);
            if (cipher == null)
            {
                migrationNeeded = true;
                oldConfig = AESConfig.legacy;
                LOG.info("Migrating existing encrypted content from legacy AES configuration to current AES configuration.");
            }
            else if (!cipher.equals(AESConfig.current.getCipherName()))
            {
                LOG.error("Unexpected cipher configuration: {}", cipher);
            }

            if (migrationNeeded)
            {
                // Reset to zero to ignore problems that might have been encountered early in startup, prior to
                // starting the migration process
                DECRYPTION_EXCEPTIONS.set(0);

                final AESConfig migrationConfig = oldConfig;
                final String message = keySource;
                final String passPhrase = oldPassPhrase != null ? oldPassPhrase : getEncryptionPassPhrase();

                // Test the old encryption key/algorithm before attempting migration
                String testDescription = (keySource != null) ? "old encryption key" : "legacy AES algorithm";
                // Test but don't create a validation value if it doesn't already exist
                testEncryptionKey(passPhrase, migrationConfig, testDescription, false);
                if (DECRYPTION_EXCEPTIONS.get() == 0)
                {
                    Encryption.EncryptionMigrationHandler.HANDLERS
                        .forEach(handler -> handler.migrateEncryptedContent(passPhrase, message, migrationConfig));

                    CacheManager.clearAllKnownCaches();
                }
                // Test to validate conversion and create a validation value if needed
                testEncryptionKey();
            }

            if (DECRYPTION_EXCEPTIONS.get() == 0)
            {
                if (oldPassPhrase != null)
                {
                    LOG.info("Migration of all existing encrypted content from OldEncryptionKey to EncryptionKey is complete");
                    LOG.info("IMPORTANT: Since migration is complete you should now remove the {}", keySource);
                }
                if (cipher == null)
                {
                    cipherProps.put(CIPHER_PROPERTY, AESConfig.current.getCipherName());
                    cipherProps.save();
                    LOG.info("Migration from existing encrypted content from legacy AES configuration to current AES configuration is complete.");
                }
            }
        }
    }

    public static void deleteEncryptedContent(User user)
    {
        LOG.info("Deleting all encrypted content at the request of {}", user.getDisplayName(user));
        List<String> descriptions = new LinkedList<>();
        EncryptionMigrationHandler.HANDLERS
            .forEach(encryptionMigrationHandler -> {
                try
                {
                    encryptionMigrationHandler.deleteEncryptedContent();
                    descriptions.add(encryptionMigrationHandler.getDescription().toLowerCase());
                }
                catch (Exception e)
                {
                    LOG.warn("Error while deleting encrypted content from {}", encryptionMigrationHandler.getDescription(), e);
                }
            });
        SiteSettingsAuditEvent event = new SiteSettingsAuditEvent(ContainerManager.getRoot(), "All encrypted content was deleted");
        final String changes;
        if (!descriptions.isEmpty())
            changes = "Deleted content: " + StringUtilsLabKey.joinWithConjunction(descriptions, "and");
        else
            changes = "All deletes failed";
        event.setChanges(changes);
        AuditLogService.get().addEvent(user, event);
        CacheManager.clearAllKnownCaches();
        // Reset the counter and clear the warnings
        DECRYPTION_EXCEPTIONS.set(0);
        WarningService.get().clearStaticWarnings();
        LOG.info("Finished deleting all encrypted content");
    }

    private static final EncryptionMigrationHandler TEST_HANDLER = new EncryptionMigrationHandler()
    {
        @Override
        public String getDescription()
        {
            return "Test";
        }

        @Override
        public void migrateEncryptedContent(String oldPassPhrase, String keySource, AESConfig oldConfig)
        {
        }

        @Override
        public void deleteEncryptedContent()
        {
        }
    };

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
                Algorithm aes = getAES128(TEST_HANDLER);
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

        @Test
        public void testDeleteEncryptedContent()
        {
            // Simple test that ensures no exceptions are thrown and checks only that encrypted property sets are gone.
            // Changes are not committed, so content is not actually deleted.
            try (Transaction _ = DbScope.getLabKeyScope().ensureTransaction())
            {
                deleteEncryptedContent(TestContext.get().getUser());
                assertEquals(0, new EncryptedPropertyStore().getEncryptedPropertySetCount());
            }
        }
    }
}
