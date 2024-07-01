package org.labkey.assay.plate.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public class LayoutEngine
{
    private final LayoutOperation _operation;
    private final ReformatOptions.OperationOptions _operationOptions;
    private final List<Plate> _sourcePlates;
    private final PlateType _targetPlateType;

    public LayoutEngine(
        @NotNull LayoutOperation operation,
        @NotNull ReformatOptions.OperationOptions operationOptions,
        @NotNull List<Plate> sourcePlates,
        @Nullable PlateType targetPlateType
    )
    {
        _operation = operation;
        _operationOptions = operationOptions;
        _sourcePlates = sourcePlates;
        _targetPlateType = targetPlateType;
    }

    public List<WellLayout> run() throws ValidationException
    {
        if (_sourcePlates.isEmpty())
            throw new ValidationException("Invalid configuration. Source plates are required to run the layout engine.");

        if (_operation.requiresTargetPlateType() && _targetPlateType == null)
            throw new ValidationException("A target plate type is required for this operation.");

        _operation.validateOptions(_operationOptions, _sourcePlates, _targetPlateType);

        return _operation.execute(_operationOptions, _sourcePlates, _targetPlateType);
    }

    public static LayoutOperation layoutOperationFactory(ReformatOptions reformatOptions)
    {
        return switch (reformatOptions.getOperation())
        {
            case compress -> new CompressOperation();
            case expand -> new ExpandOperation();
            case stamp -> new StampOperation();
        };
    }
}
