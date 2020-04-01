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
    protected Dataset.KeyManagementType _keyPropertyManaged;
    protected boolean _isDemographicData;
    protected int _cohortId;
    protected String _tag;
    protected boolean _showByDefault = true;
    protected  String _sourceAssayName;
    protected String _sourceAssayUrl;
    protected String _dataSharing; // todo RP: to finalize

    public DatasetDomainKindProperties()
    {
    }

    public DatasetDomainKindProperties(Dataset ds)
    {
        _datasetId = ds.getDatasetId();
        _name = ds.getName();
        _description = ds.getDescription();
        _category = ds.getViewCategory();
        _label = ds.getLabel();
        _typeURI = ds.getTypeURI();
        _visitDatePropertyName = ds.getKeyPropertyName(); // Check if this is correct
        _keyPropertyName = ds.getKeyPropertyName();
        _keyPropertyManaged = ds.getKeyManagementType(); // what is this?
        _isDemographicData = ds.isDemographicData();

        _cohortId = 1;
        _tag = ""; // what is this?
        _showByDefault = true; // make this
        _sourceAssayName = "name";
        _sourceAssayUrl = "url";
        _dataSharing = "whelp";

//        _cohortId = ds.getCohortId();
//        _tag = ds._tag; // what is this?
//        _showByDefault = ds._showByDefault; // make this
//        _sourceAssayName = ds._sourceAssayName;
//        _sourceAssayUrl = ds._sourceAssayUrl;
//        _dataSharing = ds.getData;
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

    public Dataset.KeyManagementType isKeyPropertyManaged()
    {
        return _keyPropertyManaged;
    }

    public void setKeyPropertyManaged(Dataset.KeyManagementType keyPropertyManaged)
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
