/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.data;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:16 PM
 */
public class DbSequence
{
    private final Container _c;
    private final int _rowId;

    DbSequence(Container c, int rowId)
    {
        _c = c;
        _rowId = rowId;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public Container getContainer()
    {
        return _c;
    }

    public int current()
    {
        return DbSequenceManager.current(this);
    }

    public int next()
    {
        return DbSequenceManager.next(this);
    }

    public void ensureMinimum(int minimum)
    {
        DbSequenceManager.ensureMinimum(this, minimum);
    }

    @Override
    public String toString()
    {
        return _c.toString() + ": " + _rowId;
    }
}