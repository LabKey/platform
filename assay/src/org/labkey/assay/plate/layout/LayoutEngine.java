package org.labkey.assay.plate.layout;

import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public class LayoutEngine
{
    private final LayoutOperation _operation;
    private final ReformatOptions _options;
    private final List<Plate> _sourcePlates;
    private final PlateType _targetPlateType;

    public LayoutEngine(ReformatOptions options, List<Plate> sourcePlates, PlateType targetPlateType)
    {
        _operation = layoutOperationFactory(options);
        _options = options;
        _sourcePlates = sourcePlates;
        _targetPlateType = targetPlateType;
    }

    public List<WellLayout> run() throws ValidationException
    {
        if (_sourcePlates.isEmpty())
            throw new ValidationException("Invalid configuration. Source plates are required to run the layout engine.");

        if (_operation.requiresTargetPlateType() && _targetPlateType == null)
            throw new ValidationException("A target plate type is required for this operation.");

        _operation.validate(_options, _sourcePlates, _targetPlateType);

        return _operation.execute(_options, _sourcePlates, _targetPlateType);
    }

    private static LayoutOperation layoutOperationFactory(ReformatOptions reformatOptions)
    {
        return switch (reformatOptions.getOperation())
        {
            case columnCompression -> new ColumnCompressionOperation();
            case quadrant -> new QuadrantOperation();
            case reverseQuadrant -> new ReverseQuadrantOperation();
            case stamp -> new StampOperation();
        };
    }
}
