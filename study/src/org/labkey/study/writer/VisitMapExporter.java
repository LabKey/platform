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
package org.labkey.study.writer;

import org.labkey.study.model.Study;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:52:38 AM
 */
public class VisitMapExporter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx) throws FileNotFoundException, UnsupportedEncodingException
    {
        DataFaxVisitMapWriter writer = new DataFaxVisitMapWriter();
        writer.write(study.getVisits(), ctx);
    }
}
