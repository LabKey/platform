/*
 * Copyright (c) 2009-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    private FieldKey _runFieldKey;
    private FieldKey _resultRowIdFieldKey;
    private FieldKey _specimenDetailParentFieldKey;

    public AssayTableMetadata(FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey)
    {
        _runFieldKey = runFieldKey;
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

    public FieldKey getRunFieldKeyFromResults()
    {
        return _runFieldKey;
    }

    /** @return relative to the assay's results table, the FieldKey that gets to the Run table */
    public FieldKey getRunRowIdFieldKeyFromResults()
    {
        return new FieldKey(_runFieldKey, ExpRunTable.Column.RowId.toString());
    }

    public FieldKey getResultRowIdFieldKey()
    {
        return _resultRowIdFieldKey;
    }

    /** @return relative to the run object */
    public FieldKey getTargetStudyFieldKey()
    {
        return FieldKey.fromParts(AssayService.BATCH_COLUMN_NAME, AssayService.BATCH_PROPERTIES_COLUMN_NAME, AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
    }
}
