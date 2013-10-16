package org.labkey.api.data;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ConfigurationException;

import javax.servlet.ServletContext;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/11/13
 * Time: 4:34 PM
 */
public class EncryptedPropertyStore extends AbstractPropertyStore
{
    private static final String MASTER_ENCRYPTION_KEY_PARAMETER_NAME = "MasterEncryptionKey";

    private final String _masterEncryptionKey;
    private final Encryption _preferredEncryption;

    public EncryptedPropertyStore()
    {
        super("Encrypted Properties");

        ServletContext context = ModuleLoader.getServletContext();

        if (null != context)
        {
            String masterEncryptionKey = context.getInitParameter(MASTER_ENCRYPTION_KEY_PARAMETER_NAME);

            // If master key is there (not null, not blank, not whitespace)
            if (!StringUtils.isBlank(masterEncryptionKey) && !masterEncryptionKey.trim().equals("@@put master encryption key here@@"))
            {
                _masterEncryptionKey = masterEncryptionKey;
                // TODO: Add strong encryption and default to it here
                _preferredEncryption = Encryption.Test;
                return;
            }
        }

        _masterEncryptionKey = null;
        _preferredEncryption = Encryption.NoKey;
    }

    @Override
    protected void validateStore()
    {
        if (null == _masterEncryptionKey)
            throw new ConfigurationException("Attempting to use the encrypted property store, but MasterEncryptionKey has not been specified in labkey.xml.",
                "Edit labkey.xml and provide a suitable encryption key. See the server configuration documentation on labkey.org.");
    }

    @Override
    protected boolean isValidPropertyMap(PropertyMap props)
    {
        return props.getEncryption() != Encryption.None;
    }

    @Override
    protected String getSaveValue(PropertyMap props, @Nullable String value)
    {
        if (null == value)
            return null;

        return Base64.encodeBase64String(props.getEncryption().encrypt(value));
    }

    @Override
    protected void fillValueMap(TableSelector selector, final PropertyMap props)
    {
        final Encryption encryption = props.getEncryption();

        selector.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String encryptedValue = rs.getString(2);
                String value = null == encryptedValue ? null : encryption.decrypt(Base64.decodeBase64(encryptedValue));
                props.put(rs.getString(1), value);
            }
        });
    }

    // TODO: Filter reads, writes, deletes in each store

    @Override
    protected Encryption getPreferredEncryption()
    {
        return _preferredEncryption;
    }
}
