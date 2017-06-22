/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package gwt.client.org.labkey.study.dataset.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.StringProperty;

import java.util.Map;

/**
 * User: matthewb
 * Date: Apr 30, 2007
 * Time: 9:51:51 AM
 */
public class GWTDataset implements IsSerializable
{
    private IntegerProperty _datasetId = new IntegerProperty(0);
    private StringProperty _name = new StringProperty();
    private StringProperty _typeURI = new StringProperty();
    private StringProperty _category = new StringProperty();
    private StringProperty _visitDatePropertyName = new StringProperty();
    private StringProperty _keyPropertyName = new StringProperty();
    private BooleanProperty _keyPropertyManaged = new BooleanProperty();
    private BooleanProperty _isDemographicData = new BooleanProperty();
    private StringProperty _label = new StringProperty();
    private IntegerProperty _cohortId = new IntegerProperty(0);
    private StringProperty _tag = new StringProperty();
    private BooleanProperty _showByDefault = new BooleanProperty();
    private StringProperty _description = new StringProperty();
    private StringProperty _sourceAssayName = new StringProperty();
    private StringProperty _sourceAssayURL = new StringProperty();
    private StringProperty _dataSharing = new StringProperty();

    // read-only (not changed in the editor)
    private boolean _definitionIsShared = false;
    private boolean _visitMapShared = false;

    public static final String TIME_KEY_FIELD_KEY = "_Special$Time_";
    public static final String TIME_KEY_FIELD_DISPLAY = "Time (from Date/Time)";

    private Map<String, String> _cohortMap;

    private Map<String, String> _visitDateMap;


    public GWTDataset()
    {
    }

    public String getKeyPropertyName()
    {
        return _keyPropertyName.getString();
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        this._keyPropertyName.set(keyPropertyName);
    }

    public boolean getKeyPropertyManaged()
    {
        return _keyPropertyManaged.getBool();
    }

    public void setKeyPropertyManaged(boolean keyPropertyManaged)
    {
        _keyPropertyManaged.setBool(keyPropertyManaged);
    }

    public int getDatasetId()
    {
        return _datasetId.getInt();
    }

    public void setDatasetId(int datasetId)
    {
        this._datasetId.setInt(datasetId);
    }

    public String getName()
    {
        return _name.getString();
    }

    public void setName(String name)
    {
        this._name.set(name);
    }

    public String getTypeURI()
    {
        return _typeURI.getString();
    }

    public void setTypeURI(String typeURI)
    {
        this._typeURI.set(typeURI);
    }

    public String getCategory()
    {
        return _category.getString();
    }

    public void setCategory(String category)
    {
        this._category.set(category);
    }

    public String getVisitDatePropertyName()
    {
        return _visitDatePropertyName.getString();
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        this._visitDatePropertyName.set(visitDatePropertyName);
    }

    public boolean getDemographicData()
    {
        return _isDemographicData.getBool();
    }

    public void setDemographicData(boolean demographicData)
    {
        _isDemographicData.setBool(demographicData);
    }

    public String getLabel()
    {
        return _label.getString();
    }

    public void setLabel(String label)
    {
        _label.set(label);
    }

    public Integer getCohortId()
    {
        return _cohortId.getInteger();
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId.set(cohortId);
    }

    public String getTag()
    {
        return _tag.getString();
    }

    public void setTag(String tag)
    {
        _tag.set(tag);
    }

    public boolean getShowByDefault()
    {
        return _showByDefault.getBool();
    }

    public void setShowByDefault(boolean showByDefault)
    {
        _showByDefault.setBool(showByDefault);
    }

    public String getDescription()
    {
        return _description.getString();
    }

    public void setDescription(String description)
    {
        _description.set(description);
    }

    public Map<String, String> getCohortMap()
    {
        return _cohortMap;
    }

    public void setCohortMap(Map<String, String> cohortMap)
    {
        _cohortMap = cohortMap;
    }

    public Map<String, String> getVisitDateMap()
    {
        return _visitDateMap;
    }

    public void setVisitDateMap(Map<String, String> visitDateMap)
    {
        _visitDateMap = visitDateMap;
    }

    public String getSourceAssayName()
    {
        return _sourceAssayName.getString();
    }

    public void setSourceAssayName(String assayName)
    {
        _sourceAssayName.set(assayName);
    }

    public String getSourceAssayURL()
    {
        return _sourceAssayURL.getString();
    }

    public void setSourceAssayURL(String assayURL)
    {
        _sourceAssayURL.set(assayURL);
    }

    public String getDataSharing()
    {
        return _dataSharing.getString();
    }

    public void setDataSharing(String sharing)
    {
        _dataSharing.set(sharing);
    }

    public StringProperty getDataSharingProperty()
    {
        return _dataSharing;
    }

    public boolean isDefinitionShared()
    {
        return _definitionIsShared;
    }

    public void setDefinitionShared(boolean b)
    {
        _definitionIsShared = b;
    }

    public boolean isVisitMapShared()
    {
        return _visitMapShared;
    }

    public void setVisitMapShared(boolean visitMapIsShared)
    {
        _visitMapShared = visitMapIsShared;
    }
}
