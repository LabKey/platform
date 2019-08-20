/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.api.study.actions;

/**
 * User: kevink
 * Date: Dec 12, 2008
 */
public class DataDetailsForm extends ProtocolIdForm
{
    private Object _dataRowId;

    public Object getDataRowId()
    {
        return _dataRowId;
    }

    public void setDataRowId(Object dataRowId)
    {
        _dataRowId = dataRowId;
    }
}
