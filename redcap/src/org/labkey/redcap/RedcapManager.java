/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
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

        map.put(RedcapSettings.Options.projectname.name(), form.encode(form.getProjectname()));
        map.put(RedcapSettings.Options.token.name(), form.encode(form.getToken()));
        map.put(RedcapSettings.Options.metadata.name(), form.getMetadata());

        map.put(RedcapSettings.Options.enableReload.name(), String.valueOf(form.isEnableReload()));
        if (form.getReloadInterval() > 0)
            map.put(RedcapSettings.Options.reloadInterval.name(), String.valueOf(form.getReloadInterval()));
        map.put(RedcapSettings.Options.reloadUser.name(), String.valueOf(containerUser.getUser().getUserId()));
        if (form.getReloadDate() != null)
            map.put(RedcapSettings.Options.reloadDate.name(), form.getReloadDate());

        if (form.isEnableReload())
            RedcapMaintenanceTask.addContainer(containerUser.getContainer().getId());
        else
            RedcapMaintenanceTask.removeContainer(containerUser.getContainer().getId());

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

        if (map.containsKey(RedcapSettings.Options.projectname.name()))
            form.setProjectname(form.decode(map.get(RedcapSettings.Options.projectname.name())));
        if (map.containsKey(RedcapSettings.Options.token.name()))
            form.setToken(form.decode(map.get(RedcapSettings.Options.token.name())));
        if (map.containsKey(RedcapSettings.Options.metadata.name()))
            form.setMetadata(map.get(RedcapSettings.Options.metadata.name()));
        if (map.containsKey(RedcapSettings.Options.enableReload.name()))
            form.setEnableReload(Boolean.parseBoolean(map.get(RedcapSettings.Options.enableReload.name())));
        if (map.containsKey(RedcapSettings.Options.reloadInterval.name()))
            form.setReloadInterval(Integer.parseInt(map.get(RedcapSettings.Options.reloadInterval.name())));
        if (map.containsKey(RedcapSettings.Options.reloadDate.name()))
            form.setReloadDate(map.get(RedcapSettings.Options.reloadDate.name()));

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
        private boolean _enableReload;
        private String _reloadDate;
        private int _reloadUser;
        private int _reloadInterval;

        public static final String SEPARATOR_CHAR = "|";
        public static final String REGX_SEPARATOR_CHAR = "\\|";

        public enum Options
        {
            projectname,
            token,
            metadata,
            reloadInterval,
            enableReload,
            reloadDate,
            reloadUser
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

        public boolean isEnableReload()
        {
            return _enableReload;
        }

        public void setEnableReload(boolean enableReload)
        {
            _enableReload = enableReload;
        }

        public String getReloadDate()
        {
            return _reloadDate;
        }

        public void setReloadDate(String reloadDate)
        {
            _reloadDate = reloadDate;
        }

        public int getReloadUser()
        {
            return _reloadUser;
        }

        public void setReloadUser(int reloadUser)
        {
            _reloadUser = reloadUser;
        }

        public int getReloadInterval()
        {
            return _reloadInterval;
        }

        public void setReloadInterval(int reloadInterval)
        {
            _reloadInterval = reloadInterval;
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
