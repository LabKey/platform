/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.study.AssaySpecimenConfig;
import org.labkey.api.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an assay/specimen configuration for a study
 */
public class AssaySpecimenConfigImpl extends AbstractStudyEntity<Integer, AssaySpecimenConfigImpl> implements AssaySpecimenConfig
{
    private int _rowId;
    private String _assayName;
    private Integer _dataset;
    private String _description;
    private String _source;
    private Integer _locationId;
    private Integer _primaryTypeId;
    private Integer _derivativeTypeId;
    private String _tubeType;
    private String _lab;
    private String _sampleType;
    private Double _sampleQuantity;
    private String _sampleUnits;
    private List<AssaySpecimenVisitImpl> _assayVisitMap;

    public AssaySpecimenConfigImpl()
    {
    }

    public AssaySpecimenConfigImpl(Container container, String assayName, String description)
    {
        setContainer(container);
        _assayName = assayName;
        _description = description;
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    @Override
    public Integer getPrimaryKey()
    {
        return getRowId();
    }

    @Override
    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    @Override
    public String getAssayName()
    {
        return _assayName;
    }

    public void setAssayName(String assayName)
    {
        _assayName = assayName;
    }

    @Override
    public Integer getDataset()
    {
        return _dataset;
    }

    public void setDataset(Integer dataset)
    {
        _dataset = dataset;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @Override
    public String getSource()
    {
        return _source;
    }

    public void setSource(String source)
    {
        _source = source;
    }

    @Override
    public Integer getLocationId()
    {
        return _locationId;
    }

    public void setLocationId(Integer locationId)
    {
        _locationId = locationId;
    }

    @Override
    public Integer getPrimaryTypeId()
    {
        return _primaryTypeId;
    }

    public void setPrimaryTypeId(Integer primaryTypeId)
    {
        _primaryTypeId = primaryTypeId;
    }

    @Override
    public Integer getDerivativeTypeId()
    {
        return _derivativeTypeId;
    }

    public void setDerivativeTypeId(Integer derivativeTypeId)
    {
        _derivativeTypeId = derivativeTypeId;
    }

    @Override
    public String getTubeType()
    {
        return _tubeType;
    }

    public void setTubeType(String tubeType)
    {
        _tubeType = tubeType;
    }

    public String getLab()
    {
        return _lab;
    }

    public void setLab(String lab)
    {
        _lab = lab;
    }

    public String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(String sampleType)
    {
        _sampleType = sampleType;
    }

    public Double getSampleQuantity()
    {
        return _sampleQuantity;
    }

    public void setSampleQuantity(Double sampleQuantity)
    {
        _sampleQuantity = sampleQuantity;
    }

    public String getSampleUnits()
    {
        return _sampleUnits;
    }

    public void setSampleUnits(String sampleUnits)
    {
        _sampleUnits = sampleUnits;
    }

    public void setAssayVisitMap(List<AssaySpecimenVisitImpl> assayVisitMap)
    {
        _assayVisitMap = assayVisitMap;
    }

    public List<AssaySpecimenVisitImpl> getAssayVisitMap()
    {
        return _assayVisitMap;
    }

    public Map<String, Object> serialize()
    {
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("AssayName", getAssayName());
        props.put("DataSet", getDataset());
        props.put("Description", getDescription());
        props.put("LocationId", getLocationId());
        props.put("Source", getSource());
        props.put("TubeType", getTubeType());
        props.put("PrimaryTypeId", getPrimaryTypeId());
        props.put("DerivativeTypeId", getDerivativeTypeId());
        props.put("Lab", getLab());
        props.put("SampleType", getSampleType());
        props.put("SampleQuantity", getSampleQuantity());
        props.put("SampleUnits", getSampleUnits());
        props.put("Container", getContainer().getId());
        return props;
    }

    public static AssaySpecimenConfigImpl fromJSON(@NotNull JSONObject o, Container container)
    {
        AssaySpecimenConfigImpl assay = new AssaySpecimenConfigImpl(container, o.getString("AssayName"), o.getString("Description"));

        if (o.has("RowId"))
            assay.setRowId(o.getInt("RowId"));
        if (o.has("DataSet") && o.get("DataSet") instanceof Integer && o.getInt("DataSet") > 0)
            assay.setDataset(o.getInt("DataSet"));
        if (o.has("Source") && !StringUtils.isEmpty(o.getString("Source")))
            assay.setSource(o.getString("Source"));
        if (o.has("LocationId") && o.get("LocationId") instanceof Integer && o.getInt("LocationId") > 0)
            assay.setLocationId(o.getInt("LocationId"));
        if (o.has("TubeType") && !StringUtils.isEmpty(o.getString("TubeType")))
            assay.setTubeType(o.getString("TubeType"));
        if (o.has("Lab") && !StringUtils.isEmpty(o.getString("Lab")))
            assay.setLab(o.getString("Lab"));
        if (o.has("SampleType") && !StringUtils.isEmpty(o.getString("SampleType")))
            assay.setSampleType(o.getString("SampleType"));
        if (o.has("SampleQuantity") && (o.get("SampleQuantity") instanceof Integer || o.get("SampleQuantity") instanceof Double) && o.getDouble("SampleQuantity") > 0)
            assay.setSampleQuantity(o.getDouble("SampleQuantity"));
        if (o.has("SampleUnits") && !StringUtils.isEmpty(o.getString("SampleUnits")))
            assay.setSampleUnits(o.getString("SampleUnits"));
        if (o.has("PrimaryTypeId") && o.get("PrimaryTypeId") instanceof Integer)
            assay.setPrimaryTypeId(o.getInt("PrimaryTypeId"));
        if (o.has("DerivativeTypeId") && o.get("DerivativeTypeId") instanceof Integer)
            assay.setDerivativeTypeId(o.getInt("DerivativeTypeId"));

        JSONArray visitMapJSON = o.optJSONArray("VisitMap");
        if (visitMapJSON != null)
        {
            List<AssaySpecimenVisitImpl> assayVisitMap = new ArrayList<>();
            for (JSONObject assaySpecimen : JsonUtil.toJSONObjectList(visitMapJSON))
                assayVisitMap.add(AssaySpecimenVisitImpl.fromJSON(assaySpecimen, container));

            assay.setAssayVisitMap(assayVisitMap);
        }

        return assay;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssaySpecimenConfigImpl that = (AssaySpecimenConfigImpl) o;
        return _rowId == that._rowId && Objects.equals(_assayName, that._assayName) && Objects.equals(_dataset, that._dataset) && Objects.equals(_description, that._description) && Objects.equals(_source, that._source) && Objects.equals(_locationId, that._locationId) && Objects.equals(_primaryTypeId, that._primaryTypeId) && Objects.equals(_derivativeTypeId, that._derivativeTypeId) && Objects.equals(_tubeType, that._tubeType) && Objects.equals(_lab, that._lab) && Objects.equals(_sampleType, that._sampleType) && Objects.equals(_sampleQuantity, that._sampleQuantity) && Objects.equals(_sampleUnits, that._sampleUnits);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_rowId, _assayName, _dataset, _description, _source, _locationId, _primaryTypeId, _derivativeTypeId, _tubeType, _lab, _sampleType, _sampleQuantity, _sampleUnits);
    }
}
