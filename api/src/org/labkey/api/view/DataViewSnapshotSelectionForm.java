package org.labkey.api.view;

import org.labkey.api.collections.LongHashSet;
import org.labkey.api.data.DataRegionSelection;

import java.util.Set;

public class DataViewSnapshotSelectionForm extends DataViewSelectionForm
{
    private boolean _useSnapshotSelection;

    public boolean isUseSnapshotSelection()
    {
        return _useSnapshotSelection;
    }

    public void setUseSnapshotSelection(boolean useSnapshotSelection)
    {
        _useSnapshotSelection = useSnapshotSelection;
    }

    @Override
    public Set<Long> getIds(boolean clear)
    {
        if (_rowIds != null) return _rowIds;
        if (_useSnapshotSelection)
            return new LongHashSet(DataRegionSelection.getSnapshotSelectedIntegers(getViewContext(), getDataRegionSelectionKey()));
        else
            return DataRegionSelection.getSelectedIntegers(getViewContext(), getDataRegionSelectionKey(), clear);
    }
}
