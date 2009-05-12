package org.labkey.api.study.assay;

import org.labkey.api.query.FieldKey;
import org.labkey.api.study.TimepointType;
import org.labkey.api.exp.query.ExpRunTable;

/**
 * User: jeckels
 * Date: May 11, 2009
 */
public class AssayTableMetadata
{
    private FieldKey _runRowIdFieldKey;
    private FieldKey _resultRowIdFieldKey;
    private FieldKey _specimenDetailParentFieldKey;

    public AssayTableMetadata(FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey)
    {
        _runRowIdFieldKey = new FieldKey(runFieldKey, ExpRunTable.Column.RowId.toString());
        _resultRowIdFieldKey = resultRowIdFieldKey;
        _specimenDetailParentFieldKey = specimenDetailParentFieldKey;
    }

    public FieldKey getSpecimenDetailParentFieldKey()
    {
        return _specimenDetailParentFieldKey;
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);
    }

    public FieldKey getVisitIDFieldKey(TimepointType timepointType)
    {
        if (timepointType == TimepointType.DATE)
        {
            return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.DATE_PROPERTY_NAME);
        }
        else if (timepointType == TimepointType.VISIT)
        {
            return new FieldKey(_specimenDetailParentFieldKey, AbstractAssayProvider.VISITID_PROPERTY_NAME);
        }
        else
        {
            throw new IllegalArgumentException("Unknown timepoint type: " + timepointType);
        }
    }

    /** @return relative to the assay's results table, the FieldKey that gets to the Run table */
    public FieldKey getRunRowIdFieldKeyFromResults()
    {
        return _runRowIdFieldKey;
    }

    public FieldKey getResultRowIdFieldKey()
    {
        return _resultRowIdFieldKey;
    }
}
