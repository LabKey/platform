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

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public PlateSetType getType()
        {
            return _type;
        }

        public void setType(PlateSetType type)
        {
            _type = type;
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

        public void setFillStrategy(FillStrategy fillStrategy)
        {
            _fillStrategy = fillStrategy;
        }

        public Integer getTargetPlateTypeId()
        {
            return _targetPlateTypeId;
        }

        public void setTargetPlateTypeId(Integer targetPlateTypeId)
        {
            _targetPlateTypeId = targetPlateTypeId;
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

    public void setOperation(ReformatOperation operation)
    {
        _operation = operation;
    }

    public OperationOptions getOperationOptions()
    {
        return _operationOptions;
    }

    public void setOperationOptions(OperationOptions operationOptions)
    {
        _operationOptions = operationOptions;
    }

    public List<Integer> getPlateRowIds()
    {
        return _plateRowIds;
    }

    public void setPlateRowIds(List<Integer> plateRowIds)
    {
        _plateRowIds = plateRowIds;
    }

    public String getPlateSelectionKey()
    {
        return _plateSelectionKey;
    }

    public void setPlateSelectionKey(String plateSelectionKey)
    {
        _plateSelectionKey = plateSelectionKey;
    }

    public Boolean isPreview()
    {
        return _preview;
    }

    public void setPreview(Boolean preview)
    {
        _preview = preview;
    }

    public ReformatPlateSet getTargetPlateSet()
    {
        return _targetPlateSet;
    }

    public void setTargetPlateSet(ReformatPlateSet targetPlateSet)
    {
        _targetPlateSet = targetPlateSet;
    }
}
