/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.LinkedHashMap;
import java.util.Map;

public class DatasetDomainKindProperties implements Cloneable
{
    private String _entityId;
    private Integer _datasetId;
    private String _name;
    private String _description;
    private String _category;
    private Integer _categoryId; // Included only for backwards compatibility. Use _category instead
    private String _categoryName; // Included only for backwards compatibility. Use _category instead
    private String _label;

    private String _visitDatePropertyName;
    private String _keyPropertyName;
    private boolean _keyPropertyManaged = false;
    private boolean _demographicData = false;
    private boolean _demographics = false; // Included only for backwards compatibility. Use _demographicData instead
    private Integer _cohortId = null;
    private String _tag;
    private boolean _showByDefault = true;
    private String _sourceName;
    private String _sourceType;
    private String _sourceUrl;
    private String _dataSharing;
    private boolean _useTimeKeyField = false;
    private boolean _strictFieldValidation = true; // Set as false to skip validation check in DatasetDomainKind.createDomain (used in Rlabkey labkey.domain.createAndLoad)

    private int _domainId;

    // read-only (not changed in the editor)
    private boolean _definitionIsShared = false;
    private boolean _visitMapShared = false;

    public static final String TIME_KEY_FIELD_KEY = "_Special$Time_";
    public static final String TIME_KEY_FIELD_DISPLAY = "Time (from Date/Time)";

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
        _entityId = ds.getEntityId();
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
        _useTimeKeyField = ds.getUseTimeKeyField();

        if (ds.getViewCategory() != null)
        {
            _category = ds.getViewCategory().getLabel();
        }

        Dataset.PublishSource publishSource = ds.getPublishSource();
        if (publishSource != null)
        {
            _sourceName = publishSource.getLabel(ds.getPublishSourceId());
            _sourceType = publishSource.getSourceType();

            ExpObject sourceObject = publishSource.resolvePublishSource(ds.getPublishSourceId());
            if (sourceObject != null)
                _sourceUrl = publishSource.getSourceActionURL(sourceObject, sourceObject.getContainer()).getLocalURIString();
        }

        if (null != ds.getDomain())
        {
            _domainId = ds.getDomain().getTypeId();
        }
    }

    public void setDemographics(boolean demographics)
    {
        _demographics = demographics;
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

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
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

    public void setSourceName(String sourceName)
    {
        _sourceName = sourceName;
    }

    public String getSourceName()
    {
        return _sourceName;
    }

    public void setSourceType(String sourceType)
    {
        _sourceType = sourceType;
    }

    public String getSourceType()
    {
        return _sourceType;
    }

    public void setSourceUrl(String sourceUrl)
    {
        _sourceUrl = sourceUrl;
    }

    public String getSourceUrl()
    {
        return _sourceUrl;
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

    public boolean isUseTimeKeyField()
    {
        return _useTimeKeyField;
    }

    public void setUseTimeKeyField(boolean useTimeKeyField)
    {
        _useTimeKeyField = useTimeKeyField;
    }

    public void setCategoryId(Integer categoryId)
    {
        _categoryId = categoryId;
    }

    public void setCategoryName(String categoryName)
    {
        _categoryName = categoryName;
    }

    public boolean isStrictFieldValidation()
    {
        return _strictFieldValidation;
    }

    public void setStrictFieldValidation(boolean strictFieldValidation)
    {
        _strictFieldValidation = strictFieldValidation;
    }

    public Map<String, Object> getAuditRecordMap()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Name", getName());
        if (!StringUtils.isEmpty(getDescription()))
            map.put("Description", getDescription());
        if (!StringUtils.isEmpty(getCategory()))
            map.put("Category", getCategory());
        if (!StringUtils.isEmpty(getLabel()))
            map.put("Label", getLabel());
        if (!StringUtils.isEmpty(getKeyPropertyName()))
            map.put("KeyPropertyName", getKeyPropertyName());
        if (!StringUtils.isEmpty(getVisitDatePropertyName()))
            map.put("VisitDateColumnName", getVisitDatePropertyName());
        if (!StringUtils.isEmpty(getTag()))
            map.put("Tag", getTag());
        map.put("KeyPropertyManaged", isKeyPropertyManaged());
        map.put("IsDemographicData", isDemographicData());
        map.put("IsUseTimeKeyField", isUseTimeKeyField());
        if (getCohortId() != null)
            map.put("CohortId", getCohortId());

        return map;
    }
}
