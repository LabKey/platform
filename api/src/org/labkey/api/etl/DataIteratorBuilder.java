/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-05-17
 * Time: 1:47 PM
 */

public interface DataIteratorBuilder
{
    void setForImport(boolean forImport);
    DataIterator getDataIterator(BatchValidationException x);


    public static class Wrapper implements DataIteratorBuilder
    {
        final DataIterator di;

        public Wrapper(DataIterator d)
        {
            this.di = d;
        }

        @Override
        public DataIterator getDataIterator(BatchValidationException x)
        {
            return di;
        }

        @Override
        public void setForImport(boolean forImport)
        {
        }
    }
}