package org.labkey.assay.plate.layout;

import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.model.ReformatOptions;

import java.util.Collection;
import java.util.List;

public class LayoutEngine
{
    private final List<? extends PlateType> _allPlateTypes;
    private final LayoutOperation _operation;
    private final ReformatOptions _options;
    private Collection<Long> _sampleIds;
    private List<Plate> _sourcePlates;
    private List<? extends Plate> _targetPlates;
    private List<PlateManager.PlateData> _targetPlateData;
    private PlateType _targetPlateType;
    private Plate _targetTemplate;

    public LayoutEngine(ReformatOptions options, List<? extends PlateType> allPlateTypes)
    {
        _operation = layoutOperationFactory(options);
        _options = options;
        _allPlateTypes = allPlateTypes;
    }

    public List<WellLayout> run(Container container, User user, WellData.Cache wellDataCache) throws ValidationException
    {
        if (_operation.requiresSourcePlates() && _sourcePlates.isEmpty())
            throw new ValidationException("Invalid configuration. Source plates are required to run the layout engine.");
        if (_operation.requiresTargetPlateType() && _targetPlateType == null)
            throw new ValidationException("A target plate type is required for this operation.");
        if (_operation.requiresTargetTemplate() && _targetTemplate == null)
            throw new ValidationException("A target plate template is required for this operation.");
        if (_options.isFillExistingWells() && !_operation.supportsFillExistingWells())
            throw new ValidationException("Filling existing wells is not supported for this operation.");
        if (_options.isFillPlatesOnly() && !_operation.supportsFillPlatesOnly())
            throw new ValidationException("Filling plates only is not supported for this operation.");

        LayoutOperation.ExecutionContext context = new LayoutOperation.ExecutionContext(
            container,
            user,
            _options,
            _allPlateTypes,
            _targetPlateType,
            _sourcePlates,
            _targetTemplate,
            _targetPlates,
            _targetPlateData,
            _sampleIds,
            wellDataCache
        );

        _operation.init(container, user, context);

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

    public void setSampleIds(Collection<Long> sampleIds)
    {
        _sampleIds = sampleIds;
    }

    public void setSourcePlates(List<Plate> sourcePlates)
    {
        _sourcePlates = sourcePlates;
    }

    public void setTargetPlates(List<? extends Plate> targetPlates)
    {
        _targetPlates = targetPlates;
    }

    public void setTargetPlateData(List<PlateManager.PlateData> targetPlateData)
    {
        _targetPlateData = targetPlateData;
    }

    public void setTargetPlateType(PlateType targetPlateType)
    {
        _targetPlateType = targetPlateType;
    }

    public void setTargetTemplate(Plate targetTemplate)
    {
        _targetTemplate = targetTemplate;
    }
}
