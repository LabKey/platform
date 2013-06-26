/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.apache.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 1/9/13
 * Time: 4:46 PM
 */
public class ParserErrors
{
    private List<Pair<String, Level>> _errors = new ArrayList<>();
    private Level _threshold;

    public ParserErrors(Level threshold)
    {
        _threshold = threshold;
    }

    public void addError(String msg)
    {
        addError(msg, Level.ERROR);
    }

    public void addError(String msg, Level level)
    {
        if (level.isGreaterOrEqual(_threshold))
            _errors.add(Pair.of(msg, level));
    }

    public int getErrorCount()
    {
        return _errors.size();
    }

    public void confirmNoErrors() throws BatchValidationException
    {
        BatchValidationException e = getErrors();
        if (e != null)
            throw e;
    }

    public BatchValidationException getErrors()
    {
        BatchValidationException e = new BatchValidationException();
        for (Pair<String, Level> pair : _errors)
        {
            e.addRowError(new ValidationException(pair.second + ": " + pair.first));
        }
        return e.hasErrors() ? e : null;
    }
}
