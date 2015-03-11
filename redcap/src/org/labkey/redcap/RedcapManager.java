package org.labkey.redcap;

import org.labkey.api.data.PropertyManager;
import org.labkey.api.writer.ContainerUser;

import java.util.Map;

/**
 * Created by klum on 2/9/2015.
 */
public class RedcapManager
{
    public static final String REDCAP_PROPERTIES = "RedcapConfigurationSettings";

    static void saveRedcapSettings(ContainerUser containerUser, RedcapSettings form)
    {
        PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(containerUser.getContainer(), RedcapManager.REDCAP_PROPERTIES, true);

        map.put(RedcapManager.RedcapSettings.Options.projectname.name(), form.encode(form.getProjectname()));
        map.put(RedcapManager.RedcapSettings.Options.token.name(), form.encode(form.getToken()));
        map.put(RedcapManager.RedcapSettings.Options.metadata.name(), form.getMetadata());

        map.save();
    }

    /**
     * Returns the saved redcap settings for the specified container
     * @param containerUser
     * @return
     */
    static RedcapSettings getRedcapSettings(ContainerUser containerUser)
    {
        RedcapSettings form = new RedcapSettings();
        Map<String, String> map = PropertyManager.getEncryptedStore().getProperties(containerUser.getContainer(), RedcapManager.REDCAP_PROPERTIES);

        if (map.containsKey(RedcapManager.RedcapSettings.Options.projectname.name()))
            form.setProjectname(form.decode(map.get(RedcapManager.RedcapSettings.Options.projectname.name())));
        if (map.containsKey(RedcapManager.RedcapSettings.Options.token.name()))
            form.setToken(form.decode(map.get(RedcapManager.RedcapSettings.Options.token.name())));
        if (map.containsKey(RedcapManager.RedcapSettings.Options.metadata.name()))
            form.setMetadata(map.get(RedcapManager.RedcapSettings.Options.metadata.name()));

        return form;
    }

    /**
     * Represents the serialized redcap settings roundtripped through the configuration UI
     */
    public static class RedcapSettings
    {
        private String[] _projectname;
        private String[] _token;
        private String _metadata;

        public static final String SEPARATOR_CHAR = "|";
        public static final String REGX_SEPARATOR_CHAR = "\\|";

        public enum Options
        {
            projectname,
            token,
            metadata,
        }

        public String[] getProjectname()
        {
            return _projectname;
        }

        public void setProjectname(String[] projectname)
        {
            _projectname = projectname;
        }

        public String[] getToken()
        {
            return _token;
        }

        public void setToken(String[] token)
        {
            _token = token;
        }

        public String getMetadata()
        {
            return _metadata;
        }

        public void setMetadata(String metadata)
        {
            _metadata = metadata;
        }

        public String encode(String[] values)
        {
            StringBuilder encoded = new StringBuilder();
            String separator = "";
            for (String value : values)
            {
                encoded.append(separator);
                encoded.append(value);

                separator = SEPARATOR_CHAR;
            }
            return encoded.toString();

        }

        public String[] decode(String value)
        {
            if (value.contains(SEPARATOR_CHAR))
                return value.split(REGX_SEPARATOR_CHAR);
            else
                return new String[]{value};
        }
    }
}
