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
    private final List<? extends PlateType> _allPlateTypes;

    public LayoutEngine(ReformatOptions options, List<Plate> sourcePlates, PlateType targetPlateType, List<? extends PlateType> allPlateTypes)
    {
        _operation = layoutOperationFactory(options);
        _options = options;
        _sourcePlates = sourcePlates;
        _targetPlateType = targetPlateType;
        _allPlateTypes = allPlateTypes;
    }

    public List<WellLayout> run() throws ValidationException
    {
        if (_sourcePlates.isEmpty())
            throw new ValidationException("Invalid configuration. Source plates are required to run the layout engine.");

        _operation.init(_options, _sourcePlates, _targetPlateType, _allPlateTypes);

        if (_operation.requiresTargetPlateType() && _targetPlateType == null)
            throw new ValidationException("A target plate type is required for this operation.");

        return _operation.execute(_options, _sourcePlates, _targetPlateType);
    }

    public LayoutOperation getOperation()
    {
        return _operation;
    }

    private static LayoutOperation layoutOperationFactory(ReformatOptions reformatOptions)
    {
        return switch (reformatOptions.getOperation())
        {
            case columnCompression -> new CompressionOperation(CompressionOperation.Layout.Column);
            case quadrant -> new QuadrantOperation();
            case reverseQuadrant -> new ReverseQuadrantOperation();
            case rowCompression -> new CompressionOperation(CompressionOperation.Layout.Row);
            case stamp -> new StampOperation();
        };
    }
}