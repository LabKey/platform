/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.model;

import org.labkey.api.data.Entity;
import org.labkey.assay.plate.PlateImpl;

/**
 * Serializes a row in the assay.plate table.
 */
public class PlateBean extends Entity
{
    private Boolean _archived;
    private Long _rowId;
    private String _lsid;
    private String _name;
    private Boolean _template;
    private String _dataFileId;
    private String _assayType;
    private Long _plateSet;
    private Long _plateType;
    private String _plateId;
    private String _description;
    private String _barcode;

    public static PlateBean from(PlateImpl plate, boolean includeEntityProperties)
    {
        PlateBean bean = new PlateBean();

        bean.setRowId(plate.getRowId());
        bean.setArchived(plate.isArchived());
        bean.setLsid(plate.getLSID());
        bean.setName(plate.getName());
        bean.setTemplate(plate.isTemplate());
        bean.setDataFileId(plate.getDataFileId());
        bean.setAssayType(plate.getAssayType());
        bean.setPlateSet(plate.getPlateSet() != null ? plate.getPlateSet().getRowId() : null);
        bean.setPlateType(plate.getPlateType().getRowId());
        bean.setPlateId(plate.getPlateId());
        bean.setDescription(plate.getDescription());
        bean.setBarcode(plate.getBarcode());

        if (includeEntityProperties)
        {
            if (plate.getCreated() != null)
                bean.setCreated(plate.getCreated());

            var createdBy = plate.getCreatedByUser();
            if (createdBy != null)
                bean.setCreatedBy(createdBy.getUserId());

            if (plate.getModified() != null)
                bean.setModified(plate.getModified());

            var modifiedBy = plate.getModifiedByUser();
            if (modifiedBy != null)
                bean.setModifiedBy(modifiedBy.getUserId());

            var container = plate.getContainer();
            if (container != null)
                bean.setContainerId(container.getId());

            var entityId = plate.getEntityId();
            if (entityId != null)
                bean.setEntityId(entityId);
        }

        return bean;
    }

    public Boolean getArchived()
    {
        return _archived;
    }

    public void setArchived(Boolean archived)
    {
        _archived = archived;
    }

    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Boolean getTemplate()
    {
        return _template;
    }

    public void setTemplate(Boolean template)
    {
        _template = template;
    }

    public String getDataFileId()
    {
        return _dataFileId;
    }

    public void setDataFileId(String dataFileId)
    {
        _dataFileId = dataFileId;
    }

    public String getAssayType()
    {
        return _assayType;
    }

    public void setAssayType(String assayType)
    {
        _assayType = assayType;
    }

    public Long getPlateSet()
    {
        return _plateSet;
    }

    public void setPlateSet(Long plateSet)
    {
        _plateSet = plateSet;
    }

    public Long getPlateType()
    {
        return _plateType;
    }

    public void setPlateType(Long plateType)
    {
        _plateType = plateType;
    }

    public String getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(String plateId)
    {
        _plateId = plateId;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getBarcode()
    {
        return _barcode;
    }

    public void setBarcode(String barcode)
    {
        _barcode = barcode;
    }
}
