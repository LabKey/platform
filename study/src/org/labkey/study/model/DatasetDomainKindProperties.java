package org.labkey.study.model;

import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.study.Dataset;

public class DatasetDomainKindProperties
{
    protected int _datasetId;
    protected String _name;
    protected String _description;
    protected ViewCategory _category;
    protected String _label;

    protected String _typeURI;
    protected String _visitDatePropertyName;
    protected String _keyPropertyName;
    protected Boolean _keyPropertyManaged;
    protected boolean _isDemographicData;
    protected int _cohortId;
    protected String _tag;
    protected boolean _showByDefault = true;
    protected  String _sourceAssayName;
    protected String _sourceAssayUrl;
    protected String _dataSharing; // todo RP: to finalize

    // read-only
    public static final String TIME_KEY_FIELD_KEY = "_Special$Time_";

    public DatasetDomainKindProperties()
    {
    }

    // wait. Do I need this at all?
    public DatasetDomainKindProperties(Dataset ds)
    {
        _datasetId = ds.getDatasetId();
        _name = ds.getName();
        _description = ds.getDescription();
        _category = ds.getViewCategory();
        _label = ds.getLabel();
        _typeURI = ds.getTypeURI();
        _keyPropertyName = ds.getKeyPropertyName();
        _isDemographicData = ds.isDemographicData();
        _cohortId = ds.getCohortId();

        // RP TODO: Have to figure out these, because they don't come from ds. Pending understanding of what this constructor is for
        _visitDatePropertyName = "";
        _keyPropertyManaged = false;
        _tag = "";
        _showByDefault = true;
        _sourceAssayName = "";
        _sourceAssayUrl = "";
        _dataSharing = "";
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        _datasetId = datasetId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public ViewCategory getCategory()
    {
        return _category;
    }

    public void setCategory(ViewCategory category)
    {
        _category = category;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getTypeURI()
    {
        return _typeURI;
    }

    public void setTypeURI(String typeURI)
    {
        _typeURI = typeURI;
    }

    public String getVisitDatePropertyName()
    {
        return _visitDatePropertyName;
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        _visitDatePropertyName = visitDatePropertyName;
    }

    public String getKeyPropertyName()
    {
        return _keyPropertyName;
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        _keyPropertyName = keyPropertyName;
    }

    public boolean isKeyPropertyManaged()
    {
        return _keyPropertyManaged;
    }

    public void setKeyPropertyManaged(Boolean keyPropertyManaged)
    {
        _keyPropertyManaged = keyPropertyManaged;
    }

    public boolean isDemographicData()
    {
        return _isDemographicData;
    }

    public void setDemographicData(boolean demographicData)
    {
        _isDemographicData = demographicData;
    }

    public int getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(int cohortId)
    {
        _cohortId = cohortId;
    }

    public String getTag()
    {
        return _tag;
    }

    public void setTag(String tag)
    {
        _tag = tag;
    }

    public boolean isShowByDefault()
    {
        return _showByDefault;
    }

    public void setShowByDefault(boolean showByDefault)
    {
        _showByDefault = showByDefault;
    }

    public String getSourceAssayName()
    {
        return _sourceAssayName;
    }

    public void setSourceAssayName(String sourceAssayName)
    {
        _sourceAssayName = sourceAssayName;
    }

    public String getSourceAssayUrl()
    {
        return _sourceAssayUrl;
    }

    public void setSourceAssayUrl(String sourceAssayUrl)
    {
        _sourceAssayUrl = sourceAssayUrl;
    }

    public String getDataSharing()
    {
        return _dataSharing;
    }

    public void setDataSharing(String dataSharing)
    {
        _dataSharing = dataSharing;
    }
}
