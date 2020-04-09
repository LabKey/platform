package org.labkey.study.model;

import org.labkey.api.assay.AssayUrls;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.PageFlowUtil;

import java.util.Map;

public class DatasetDomainKindProperties
{
    protected Integer _datasetId;
    protected String _name;
    protected String _description;
    protected String _category;
    protected Integer _categoryId; // Is it a bad idea to add this here?
    protected String _label;

    protected String _typeURI;
    protected String _visitDatePropertyName;
    protected String _keyPropertyName;
    protected boolean _keyPropertyManaged = false;
    protected boolean _isDemographicData = false;
    protected Integer _cohortId;
    protected String _tag;
    protected boolean _showByDefault = true; // Temp note RP: This is the 'showInOverview' property
    protected String _sourceAssayName;
    protected String _sourceAssayUrl;
    protected String _dataSharing;

    // read-only (not changed in the editor)
    private boolean _definitionIsShared = false;
    private boolean _visitMapShared = false;

    public static final String TIME_KEY_FIELD_KEY = "_Special$Time_";
    public static final String TIME_KEY_FIELD_DISPLAY = "Time (from Date/Time)";

    private Map<String, String> _cohortMap;

    private Map<String, String> _visitDateMap;

    public DatasetDomainKindProperties()
    {
        // This does not give a default typeURI, or dataSharing value, or datasetId, as GWTDataset does in getDataset()
    }

    public DatasetDomainKindProperties(Dataset ds)
    {
        _datasetId = ds.getDatasetId();
        _name = ds.getName();
        _description = ds.getDescription();
        if (ds.getViewCategory() != null)
        {
            _category = ds.getViewCategory().getLabel();
            _categoryId = ds.getViewCategory().getRowId(); // Is this the correct id
        }
        _label = ds.getLabel();
        _typeURI = ds.getTypeURI();
        _keyPropertyName = ds.getKeyPropertyName();
        _isDemographicData = ds.isDemographicData();
        _showByDefault = ds.isShowByDefault();
        _cohortId = ds.getCohortId();
        _visitDatePropertyName = ds.getVisitDatePropertyName();
        _tag = ds.getTag();
        _dataSharing = ds.getDataSharingEnum().name();

        // Might have to verify below
        _keyPropertyManaged = (ds.getKeyManagementType() != Dataset.KeyManagementType.None);
        ExpProtocol protocol = ds.getAssayProtocol();
        if (protocol != null)
        {
            _sourceAssayName = protocol.getName();
            _sourceAssayUrl = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(protocol.getContainer(), protocol).getLocalURIString();
        }
    }

    public Integer getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(Integer datasetId)
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

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
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

    public void setKeyPropertyManaged(boolean keyPropertyManaged)
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

    public Integer getCohortId()
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

    public Integer getCategoryId()
    {
        return _categoryId;
    }
}
