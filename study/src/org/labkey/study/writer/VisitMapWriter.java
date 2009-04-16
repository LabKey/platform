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
import org.labkey.study.model.Visit;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:52:38 AM
 */
public abstract class VisitMapWriter
{
    private Study _study;
    private File _file;

    protected VisitMapWriter(Study study, File file)
    {
        _study = study;
        _file = file;
    }

    void write() throws FileNotFoundException
    {
        PrintWriter out = null;

        try
        {
            out = new PrintWriter(_file);
            write(_study.getVisits(),  out);
        }
        finally
        {
            if (null != out)
                out.close();
        }
    }

    abstract protected void write(Visit[] visits, PrintWriter out);
}
