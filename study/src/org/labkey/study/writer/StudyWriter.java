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

import org.apache.log4j.Logger;
import org.labkey.study.model.Study;

import java.util.Arrays;
import java.util.List;

/**
 * User: adam
 * Date: Apr 14, 2009
 * Time: 7:29:32 PM
 */
public class StudyWriter implements Writer<Study>
{
    private final List<Writer<Study>> _writers = Arrays.asList(new VisitMapExporter(), new DataSetWriter(), new ReportWriter(), new QueryWriter());

    public void write(Study study, ExportContext ctx) throws Exception
    {
        for (Writer<Study> writer : _writers)
        {
            writer.write(study, ctx);
        }
    }
}
