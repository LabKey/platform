/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.study.actions.AssayRunUploadForm;

/**
 * User: jeckels
 * Date: Aug 3, 2007
 */
public class AssayWarningsDisplayColumn extends DataColumn
{
    private AssayRunUploadForm _form;

    public AssayWarningsDisplayColumn(AssayRunUploadForm form)
    {
        super(createColumnInfo());
        _form = form;
        setInputType("checkbox");
    }

    private static ColumnInfo createColumnInfo()
    {
        ColumnInfo column = new ColumnInfo("ignoreWarnings");
        return column;
    }

    public boolean isEditable()
    {
        return true;
    }
}
