/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.io.IOException;

/**
* User: jeckels
* Date: May 15, 2012
*/
public abstract class DataInputColumn extends PublishResultsQueryView.InputColumn
{
    protected ColumnInfo _requiredColumn;

    public DataInputColumn(String caption, String formElementName, boolean editable, String completionBase, PublishResultsQueryView.ResolverHelper resolverHelper,
                           ColumnInfo requiredColumn)
    {
        super(caption, editable, formElementName, completionBase, resolverHelper);
        _requiredColumn = requiredColumn;
    }

    protected abstract Object calculateValue(RenderContext ctx) throws IOException;

    public Object getValue(RenderContext ctx)
    {
        try
        {
            return calculateValue(ctx);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
//            if (_requiredColumn == null)
//                return null;
//            return ctx.getRow().get(_requiredColumn.getAlias());
    }
}
