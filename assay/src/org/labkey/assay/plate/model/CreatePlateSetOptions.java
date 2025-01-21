package org.labkey.assay.plate.model;

import org.labkey.assay.plate.PlateManager;

import java.util.ArrayList;
import java.util.List;

public class CreatePlateSetOptions extends ReformatOptions.TargetPlateSet
{
    private ReformatOptions.ReformatOperation _operation;
    private List<PlateManager.PlateData> _plates = new ArrayList<>();
    private String _selectionKey;

    public ReformatOptions.ReformatOperation getOperation()
    {
        return _operation;
    }

    public void setOperation(ReformatOptions.ReformatOperation operation)
    {
        _operation = operation;
    }

    public List<PlateManager.PlateData> getPlates()
    {
        return _plates;
    }

    public void setPlates(List<PlateManager.PlateData> plates)
    {
        _plates = plates;
    }

    public String getSelectionKey()
    {
        return _selectionKey;
    }

    public void setSelectionKey(String selectionKey)
    {
        _selectionKey = selectionKey;
    }
}
