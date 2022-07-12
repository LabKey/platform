package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.files.FileContentService;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.logging.LogHelper;

import java.io.File;
import java.util.Arrays;

// Additional properties that are stored in the "SiteSettings" scope but not exposed on the site settings page
public enum RandomStartupProperties implements StartupProperty, SafeToRenderEnum
{
    BLASTBaseURL("BLAST server URL")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.storeStringValue(this, value);
        }
    },
    externalRedirectHostURLs("Allowed external redirect hosts")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setExternalRedirectHosts(Arrays.asList(StringUtils.split(value, AppPropsImpl.EXTERNAL_REDIRECT_HOST_DELIMITER)));
        }
    },
    fileUploadDisabled("Disable file upload")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setFileUploadDisabled(Boolean.parseBoolean(value));
        }
    },
    mailRecorderEnabled("Record email messages sent")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setMailRecorderEnabled(Boolean.parseBoolean(value));
        }
    },
    // TODO: Remove SiteRootStartupProperties.siteRootFile in favor of this property
    siteFileRoot("Site-level file root")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            FileContentService fcs = FileContentService.get();
            if (null != fcs)
            {
                File fileRoot = new File(value);
                fcs.setSiteDefaultRoot(fileRoot, null);
                fcs.setFileRootSetViaStartupProperty(true);
            }
            else
            {
                LOG.warn("FileContentService is not available! Site-level file root can't be set.");
            }
        }
    },
    userFileRoot("Enable personal folders for users")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setUserFilesRoot(value);
        }
    },
    webfilesEnabled("Alternative webfiles root")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setWebfilesEnabled(Boolean.parseBoolean(value));
        }
    };

    private final static Logger LOG = LogHelper.getLogger(RandomStartupProperties.class, "Warnings about property settings");

    private final String _description;

    RandomStartupProperties(String description)
    {
        _description = description;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public abstract void setValue(WriteableAppProps writeable, String value);
}
