/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.experiment.list;

import org.labkey.api.writer.ExportContext;
import org.labkey.api.writer.Writer;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.WriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListDefinition;

import java.util.Map;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class ListWriter implements Writer<Container, ExportContext>
{
    public String getSelectionText()
    {
        return "Lists";
    }

    public void write(Container c, ExportContext ctx, VirtualFile fs) throws Exception
    {
        Map<String, ListDefinition> lists = ListService.get().getLists(c);
        // This is where we write out the lists
    }

    public static class Factory implements WriterFactory<Container, ExportContext>
    {
        public Writer<Container, ExportContext> create()
        {
            return new ListWriter();
        }
    }
}
