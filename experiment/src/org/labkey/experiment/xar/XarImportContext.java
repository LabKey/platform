package org.labkey.experiment.xar;

import org.labkey.api.admin.FolderImportContext;

public class XarImportContext extends FolderImportContext
{
    private boolean _strictValidateExistingSampleType = true;

    public boolean isStrictValidateExistingSampleType()
    {
        return _strictValidateExistingSampleType;
    }

    public void setStrictValidateExistingSampleType(boolean strictValidateExistingSampleType)
    {
        _strictValidateExistingSampleType = strictValidateExistingSampleType;
    }
}
