package org.labkey.assay.plate.model;

import org.labkey.api.assay.plate.PlateSetType;

import java.util.List;

public class ReformatOptions
{
    public enum FillStrategy
    {
        column,
        quadrant,
        reverseQuadrant,
        row
    }

    public enum ReformatOperation
    {
        compress,
        expand,
        stamp
    }

    public static class ReformatPlateSet
    {
        private Integer _rowId;
        private String _description;
        private String _name;
        private PlateSetType _type;

        public Integer getRowId()
        {
            return _rowId;
        }

        public ReformatPlateSet setRowId(Integer rowId)
        {
            _rowId = rowId;
            return this;
        }

        public String getDescription()
        {
            return _description;
        }

        public ReformatPlateSet setDescription(String description)
        {
            _description = description;
            return this;
        }

        public String getName()
        {
            return _name;
        }

        public ReformatPlateSet setName(String name)
        {
            _name = name;
            return this;
        }

        public PlateSetType getType()
        {
            return _type;
        }

        public ReformatPlateSet setType(PlateSetType type)
        {
            _type = type;
            return this;
        }
    }

    public static class OperationOptions
    {
        private FillStrategy _fillStrategy;
        private Integer _targetPlateTypeId;

        public FillStrategy getFillStrategy()
        {
            return _fillStrategy;
        }

        public OperationOptions setFillStrategy(FillStrategy fillStrategy)
        {
            _fillStrategy = fillStrategy;
            return this;
        }

        public Integer getTargetPlateTypeId()
        {
            return _targetPlateTypeId;
        }

        public OperationOptions setTargetPlateTypeId(Integer targetPlateTypeId)
        {
            _targetPlateTypeId = targetPlateTypeId;
            return this;
        }
    }

    private ReformatOperation _operation;
    private OperationOptions _operationOptions;
    private List<Integer> _plateRowIds;
    private String _plateSelectionKey;
    private Boolean _preview = false;
    private ReformatPlateSet _targetPlateSet;

    public ReformatOperation getOperation()
    {
        return _operation;
    }

    public ReformatOptions setOperation(ReformatOperation operation)
    {
        _operation = operation;
        return this;
    }

    public OperationOptions getOperationOptions()
    {
        return _operationOptions;
    }

    public ReformatOptions setOperationOptions(OperationOptions operationOptions)
    {
        _operationOptions = operationOptions;
        return this;
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

    public ReformatPlateSet getTargetPlateSet()
    {
        return _targetPlateSet;
    }

    public ReformatOptions setTargetPlateSet(ReformatPlateSet targetPlateSet)
    {
        _targetPlateSet = targetPlateSet;
        return this;
    }
}
