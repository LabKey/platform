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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.assay.plate.PlateManager;

import java.util.List;

public class ReformatOptions
{
    public enum ReformatOperation
    {
        arrayByColumn,
        arrayByRow,
        arrayFromTemplate,
        columnCompression,
        quadrant,
        reverseQuadrant,
        rowCompression,
        stamp
    }

    public static class TargetPlateSet
    {
        private Long _rowId;
        private String _description;
        private String _name;
        private Long _parentPlateSetId;
        private Boolean _template;
        private PlateSetType _type;

        public Long getRowId()
        {
            return _rowId;
        }

        public TargetPlateSet setRowId(Long rowId)
        {
            _rowId = rowId;
            return this;
        }

        public String getDescription()
        {
            return _description;
        }

        public TargetPlateSet setDescription(String description)
        {
            _description = description;
            return this;
        }

        public String getName()
        {
            return _name;
        }

        public TargetPlateSet setName(String name)
        {
            _name = name;
            return this;
        }

        public PlateSetType getType()
        {
            return _type;
        }

        public TargetPlateSet setType(PlateSetType type)
        {
            _type = type;
            return this;
        }

        public Long getParentPlateSetId()
        {
            return _parentPlateSetId;
        }

        public TargetPlateSet setParentPlateSetId(Long parentPlateSetId)
        {
            _parentPlateSetId = parentPlateSetId;
            return this;
        }

        public Boolean isTemplate()
        {
            return _template;
        }

        public TargetPlateSet setTemplate(Boolean template)
        {
            _template = template;
            return this;
        }
    }

    public static class TargetPlateSource
    {
        public enum SourceType
        {
            template,
            type
        }

        private Long _rowId;
        private SourceType _sourceType;

        public TargetPlateSource()
        {
        }

        public TargetPlateSource(@NotNull PlateType plateType)
        {
            _sourceType = SourceType.type;
            _rowId = plateType.getRowId();
        }

        public TargetPlateSource(@NotNull Plate template)
        {
            _sourceType = SourceType.template;
            _rowId = template.getRowId();
        }

        public Long getRowId()
        {
            return _rowId;
        }

        public void setRowId(Long rowId)
        {
            _rowId = rowId;
        }

        public SourceType getSourceType()
        {
            return _sourceType;
        }

        public void setSourceType(SourceType sourceType)
        {
            _sourceType = sourceType;
        }
    }

    private Boolean _fillExistingWells = false;
    private Boolean _fillPlatesOnly = false;
    private ReformatOperation _operation;
    private List<PlateManager.PlateData> _plates;
    private List<Long> _plateRowIds;
    private String _plateSelectionKey;
    private Boolean _preview = false;
    private Boolean _previewData = true;
    private String _sampleSelectionKey;
    private TargetPlateSet _targetPlateSet;
    private TargetPlateSource _targetPlateSource;

    public Boolean isFillExistingWells()
    {
        return _fillExistingWells;
    }

    public ReformatOptions setFillExistingWells(Boolean fillExistingWells)
    {
        _fillExistingWells = fillExistingWells;
        return this;
    }

    public Boolean isFillPlatesOnly()
    {
        return _fillPlatesOnly;
    }

    public ReformatOptions setFillPlatesOnly(Boolean fillPlatesOnly)
    {
        _fillPlatesOnly = fillPlatesOnly;
        return this;
    }

    public ReformatOperation getOperation()
    {
        return _operation;
    }

    public ReformatOptions setOperation(ReformatOperation operation)
    {
        _operation = operation;
        return this;
    }

    public List<PlateManager.PlateData> getPlates()
    {
        return _plates;
    }

    public ReformatOptions setPlates(List<PlateManager.PlateData> plates)
    {
        _plates = plates;
        return this;
    }

    public List<Long> getPlateRowIds()
    {
        return _plateRowIds;
    }

    public ReformatOptions setPlateRowIds(List<Long> plateRowIds)
    {
        _plateRowIds = plateRowIds;
        return this;
    }

    public String getPlateSelectionKey()
    {
        return _plateSelectionKey;
    }

    public ReformatOptions setPlateSelectionKey(String plateSelectionKey)
    {
        _plateSelectionKey = plateSelectionKey;
        return this;
    }

    public Boolean isPreview()
    {
        return _preview;
    }

    public ReformatOptions setPreview(Boolean preview)
    {
        _preview = preview;
        return this;
    }

    public Boolean isPreviewData()
    {
        return _previewData;
    }

    public void setPreviewData(Boolean previewData)
    {
        _previewData = previewData;
    }

    public String getSampleSelectionKey()
    {
        return _sampleSelectionKey;
    }

    public ReformatOptions setSampleSelectionKey(String sampleSelectionKey)
    {
        _sampleSelectionKey = sampleSelectionKey;
        return this;
    }

    public TargetPlateSet getTargetPlateSet()
    {
        return _targetPlateSet;
    }

    public ReformatOptions setTargetPlateSet(TargetPlateSet targetPlateSet)
    {
        _targetPlateSet = targetPlateSet;
        return this;
    }

    public TargetPlateSource getTargetPlateSource()
    {
        return _targetPlateSource;
    }

    public ReformatOptions setTargetPlateSource(TargetPlateSource targetPlateSource)
    {
        _targetPlateSource = targetPlateSource;
        return this;
    }
}
