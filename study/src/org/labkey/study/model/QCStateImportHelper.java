/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Map;
import java.util.concurrent.Callable;

public class QCStateImportHelper
{
    private final User _user;
    private final DatasetDefinition _datasetDefinition;
    private final DataState _defaultQCState;
    private final Map<String, DataState> _qcLabels;
    private final boolean _autoCreate;

    public QCStateImportHelper(User user, DatasetDefinition datasetDefinition, boolean autoCreate, DataState defaultQCState)
    {
        _user = user;
        _datasetDefinition = datasetDefinition;
        _autoCreate = autoCreate;
        _defaultQCState = defaultQCState;
        _qcLabels = new CaseInsensitiveHashMap<>();
        for (DataState state : QCStateManager.getInstance().getStates(_datasetDefinition.getContainer()))
            _qcLabels.put(state.getLabel(), state);
    }

    public Callable<Object> getCallable(
            @NotNull final DataIterator it,
            @Nullable final Integer indexInputQCState
    )
    {
        return () -> {

            Object currentStateObj = indexInputQCState < 1 ? null : it.get(indexInputQCState);
            String currentStateLabel = null == currentStateObj ? null : currentStateObj.toString();

            return translateQCState(currentStateLabel);
        };
    }

    public Long translateQCState(@Nullable String currentStateLabel) throws ValidationException
    {
        if (currentStateLabel != null)
        {
            DataState state = _qcLabels.get(currentStateLabel);
            if (null == state)
            {
                if (!_autoCreate)
                {
                    throw new ValidationException("QC State not found: " + currentStateLabel);
                }
                else
                {

                    DataState newState = new DataState();
                    // default to public data:
                    newState.setPublicData(true);
                    newState.setLabel(currentStateLabel);
                    newState.setContainer(_datasetDefinition.getContainer());
                    newState = StudyManager.getInstance().insertQCState(_user, newState);
                    _qcLabels.put(newState.getLabel(), newState);
                    return newState.getRowId();
                }
            }
            return state.getRowId();
        }
        else if (_defaultQCState != null)
        {
            return _defaultQCState.getRowId();
        }
        return null;
    }
}