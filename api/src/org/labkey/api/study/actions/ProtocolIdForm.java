package org.labkey.api.study.actions;

import org.labkey.api.view.ViewForm;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:02:45 PM
*/
public class ProtocolIdForm extends ViewForm
{
    private ExpProtocol _protocol;

    private Integer _rowId;
    private String _providerName;

    public String getProviderName()
    {
        return _providerName;
    }

    public void setProviderName(String providerName)
    {
        _providerName = providerName;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public ExpProtocol getProtocol(boolean validateContainer)
    {
        if (_protocol == null)
            _protocol = BaseAssayAction.getProtocol(this, validateContainer);
        return _protocol;
    }

    public ExpProtocol getProtocol()
    {
        return getProtocol(true);
    }

    public AssayProvider getProvider()
    {
        return AssayService.get().getProvider(getProtocol());
    }
}

