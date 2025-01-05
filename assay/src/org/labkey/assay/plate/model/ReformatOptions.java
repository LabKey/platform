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
        private Integer _rowId;
        private String _description;
        private String _name;
        private Integer _parentPlateSetId;
        private Boolean _template;
        private PlateSetType _type;

        public Integer getRowId()
        {
            return _rowId;
        }

        public TargetPlateSet setRowId(Integer rowId)
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

        public Integer getParentPlateSetId()
        {
            return _parentPlateSetId;
        }

        public TargetPlateSet setParentPlateSetId(Integer parentPlateSetId)
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

        private Integer _rowId;
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

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
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
    private ReformatOperation _operation;
    private List<PlateManager.PlateData> _plates;
    private List<Integer> _plateRowIds;
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

    public void setPlates(List<PlateManager.PlateData> plates)
    {
        _plates = plates;
    }

    public List<Integer> getPlateRowIds()
    {
        return _plateRowIds;
    }

    public ReformatOptions setPlateRowIds(List<Integer> plateRowIds)
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
