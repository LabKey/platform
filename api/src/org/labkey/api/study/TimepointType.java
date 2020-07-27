/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.SimpleHasHtmlString;

/**
 * List of ways that a study can group events based on their time.
 * User: markigra
 * Date: Oct 31, 2007
 */
public enum TimepointType implements SimpleHasHtmlString
{
    /** Events should be explicitly assigned a visit number */
    VISIT(true),
    /** Events will be tracked based on their date, but will still be grouped into buckets based on their offset from the subject's start date */
    DATE(false),
    /** Events will be tracked based on their date, but no effort will be made to group them or maintain date offsets from a start date */
    CONTINUOUS(false);

    private final boolean _visitBased;

    TimepointType(boolean visitBased)
    {
        _visitBased = visitBased;
    }

    public boolean isVisitBased()
    {
        return _visitBased;
    }

    /** @throws org.labkey.api.query.ValidationException if a study of this type can't be switched to the other type */
    public void validateTransition(@NotNull TimepointType target) throws ValidationException
    {
        // Visit-based studies can't be shifted, but date and continuous can flip back and forth to each other
        if (isVisitBased() != target.isVisitBased())
            throw new ValidationException("Cannot create study type " + target + " from a " + target + " study");
    }
}
