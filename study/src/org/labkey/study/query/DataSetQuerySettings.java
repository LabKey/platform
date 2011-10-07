package org.labkey.study.query;

import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.query.QuerySettings;
import org.labkey.study.model.QCStateSet;
import org.springframework.beans.PropertyValues;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 30, 2011
 * Time: 4:48:11 PM
 */
public class DataSetQuerySettings extends QuerySettings
{
    private boolean _showSourceLinks;
    private boolean _showEditLinks = true;
    private boolean _useQCSet = true;

    public boolean isUseQCSet()
    {
        return _useQCSet;
    }

    public void setUseQCSet(boolean useQCSet)
    {
        _useQCSet = useQCSet;
    }

    public DataSetQuerySettings(String dataRegionName)
    {
        super(dataRegionName);
    }

    public DataSetQuerySettings(PropertyValues pvs, String dataRegionName)
    {
        super(pvs, dataRegionName);
    }

    public DataSetQuerySettings(QuerySettings settings)
    {
        super(settings.getDataRegionName());

        try {
            PropertyUtils.copyProperties(this, settings);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(PropertyValues params)
    {
        super.init(params);
        _showSourceLinks = _getParameter(param("showSourceLinks")) != null;
        _showEditLinks = _getParameter(param("showEditLinks")) != null;
    }

    public boolean isShowSourceLinks()
    {
        return _showSourceLinks;
    }

    public void setShowSourceLinks(boolean showSourceLinks)
    {
        _showSourceLinks = showSourceLinks;
    }

    public boolean isShowEditLinks()
    {
        return _showEditLinks;
    }

    public void setShowEditLinks(boolean showEditLinks)
    {
        _showEditLinks = showEditLinks;
    }
}
