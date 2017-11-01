/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.api.dataiterator;

import org.labkey.api.query.BatchValidationException;

import java.util.function.Predicate;

/**
 * DataIterator implementation that skips over selected rows in its incoming data stream, based on either
 * overriding the accept() method, or passing in a Predicate to make the decision.
 * User: matthewb
 * Date: 2011-05-31
 */
public class FilterDataIterator extends WrapperDataIterator
{
    final Predicate<DataIterator> predicate;

    // you can use this method if you override accept()
    protected FilterDataIterator(DataIterator in)
    {
        super(in);
        predicate = (di) -> accept();
    }

    public FilterDataIterator(DataIterator in, Predicate<DataIterator> p)
    {
        super(in);
        this.predicate = p;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        while (super.next())
        {
            if (predicate.test(_delegate))
                return true;
        }
        return false;
    }

    protected boolean accept()
    {
        return true;
    }
}
