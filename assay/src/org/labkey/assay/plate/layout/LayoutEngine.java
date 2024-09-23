package org.labkey.assay.plate.layout;

import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.List;

public class LayoutEngine
{
    private final List<? extends PlateType> _allPlateTypes;
    private final LayoutOperation _operation;
    private final ReformatOptions _options;
    private final List<Plate> _sourcePlates;
    private PlateType _targetPlateType;
    private Plate _targetTemplate;
    private List<WellData> _targetTemplateWellData;

    public LayoutEngine(ReformatOptions options, List<Plate> sourcePlates, List<? extends PlateType> allPlateTypes)
    {
        _operation = layoutOperationFactory(options);
        _options = options;
        _sourcePlates = sourcePlates;
        _allPlateTypes = allPlateTypes;
    }

    public List<WellLayout> run(Container container, User user) throws ValidationException
    {
        if (_sourcePlates.isEmpty())
            throw new ValidationException("Invalid configuration. Source plates are required to run the layout engine.");

        if (_operation.requiresTargetPlateType() && _targetPlateType == null)
            throw new ValidationException("A target plate type is required for this operation.");
        if (_operation.requiresTargetTemplate() && _targetTemplate == null)
            throw new ValidationException("A target plate template is required for this operation.");

        LayoutOperation.ExecutionContext context = new LayoutOperation.ExecutionContext(
            _options,
            _sourcePlates,
            _targetPlateType,
            _targetTemplate,
            _targetTemplateWellData
        );

        _operation.init(container, user, context, _allPlateTypes);

        return _operation.execute(context);
    }

    public LayoutOperation getOperation()
    {
        return _operation;
    }

    private static LayoutOperation layoutOperationFactory(ReformatOptions reformatOptions)
    {
        return switch (reformatOptions.getOperation())
        {
            case arrayByColumn -> new ArrayOperation(ArrayOperation.Layout.Column);
            case arrayByRow -> new ArrayOperation(ArrayOperation.Layout.Row);
            case arrayFromTemplate -> new ArrayOperation(ArrayOperation.Layout.Template);
            case columnCompression -> new CompressionOperation(CompressionOperation.Layout.Column);
            case quadrant -> new QuadrantOperation();
            case reverseQuadrant -> new ReverseQuadrantOperation();
            case rowCompression -> new CompressionOperation(CompressionOperation.Layout.Row);
            case stamp -> new StampOperation();
        };
    }

    public void setTargetPlateType(PlateType targetPlateType)
    {
        _targetPlateType = targetPlateType;
    }

    public void setTargetTemplate(Plate targetTemplate, List<WellData> targetTemplateWellData)
    {
        _targetTemplate = targetTemplate;
        _targetTemplateWellData = targetTemplateWellData;
    }
}
