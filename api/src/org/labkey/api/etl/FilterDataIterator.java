/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

package org.labkey.api.etl;

import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.Set;

/**
 * User: matthewb
 * Date: 2011-05-31
 * Time: 5:18 PM
 */
public abstract class FilterDataIterator extends WrapperDataIterator
{
    protected FilterDataIterator(DataIterator in)
    {
        super(in);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        while (super.next())
        {
            if (accept())
                return true;
        }
        return false;
    }

    protected abstract boolean accept();
}
