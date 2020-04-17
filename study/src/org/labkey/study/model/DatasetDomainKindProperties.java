package org.labkey.study.model;

import org.labkey.api.assay.AssayUrls;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;

public class DatasetDomainKindProperties implements Cloneable
{
    protected Integer _datasetId;
    protected String _name;
    protected String _description;
    protected String _category;
    protected String _label;

    protected String _visitDatePropertyName;
    protected String _keyPropertyName;
    protected boolean _keyPropertyManaged = false;
    protected boolean _demographicData = false;
    protected Integer _cohortId = null;
    protected String _tag;
    protected boolean _showByDefault = true;
    protected String _sourceAssayName;
    protected String _sourceAssayUrl;
    protected String _dataSharing;

    protected int _domainId;

    // read-only (not changed in the editor)
    private boolean _definitionIsShared = false;
    private boolean _visitMapShared = false;

    public static final String TIME_KEY_FIELD_KEY = "_Special$Time_";

    // default constructor needed for jackson
    public DatasetDomainKindProperties()
    {
    }

    public DatasetDomainKindProperties(Container container)
    {
        Study study = StudyService.get().getStudy(container);
        if (container.isProject() && study.isDataspaceStudy())
        {
            setDefinitionIsShared(study.getShareDatasetDefinitions());
            setVisitMapShared(study.getShareVisitDefinitions());
        }
    }

    public DatasetDomainKindProperties(Dataset ds)
    {
        this(ds.getContainer());
        _datasetId = ds.getDatasetId();
        _name = ds.getName();
        _description = ds.getDescription();
        _label = ds.getLabel();
        _keyPropertyName = ds.getKeyPropertyName();
        _demographicData = ds.isDemographicData();
        _showByDefault = ds.isShowByDefault();
        _cohortId = ds.getCohortId();
        _visitDatePropertyName = ds.getVisitDatePropertyName();
        _tag = ds.getTag();
        _dataSharing = ds.getDataSharingEnum().name();
        _keyPropertyManaged = (ds.getKeyManagementType() != Dataset.KeyManagementType.None);

        if (ds.getViewCategory() != null)
        {
            _category = ds.getViewCategory().getLabel();
        }

        ExpProtocol protocol = ds.getAssayProtocol();
        if (protocol != null)
        {
            _sourceAssayName = protocol.getName();
            _sourceAssayUrl = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(protocol.getContainer(), protocol).getLocalURIString();
        }

        if (null != ds.getDomain())
        {
            _domainId = ds.getDomain().getTypeId();
        }
    }

    public boolean isVisitMapShared()
    {
        return _visitMapShared;
    }

    public void setVisitMapShared(boolean visitMapShared)
    {
        _visitMapShared = visitMapShared;
    }

    public boolean isDefinitionIsShared()
    {
        return _definitionIsShared;
    }

    public void setDefinitionIsShared(boolean definitionIsShared)
    {
        _definitionIsShared = definitionIsShared;
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
        return _demographicData;
    }

    public void setDemographicData(boolean demographicData)
    {
        _demographicData = demographicData;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
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

    public int getDomainId()
    {
        return _domainId;
    }

    public void setDomainId(int domainId)
    {
        _domainId = domainId;
    }
}
