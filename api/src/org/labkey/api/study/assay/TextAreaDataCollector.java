/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;

import java.util.Collections;
import java.util.Map;
import java.io.*;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class TextAreaDataCollector extends AbstractAssayDataCollector
{
    private static final String FORM_ELEMENT_NAME = "TextAreaDataCollector.textArea";

    public String getHTML(AssayRunUploadContext context)
    {
        return "<textarea name=\"" + FORM_ELEMENT_NAME + "\" rows=\"10\" cols=\"80\"></textarea>";
    }

    public String getShortName()
    {
        return "textAreaDataProvider";
    }

    public String getDescription()
    {
        return "Paste in a tab-separated set of values";
    }

    public Map<String, File> createData(AssayRunUploadContext context) throws IOException, ExperimentException
    {
        ExpProtocol protocol = context.getProtocol();
        String data = context.getRequest().getParameter(FORM_ELEMENT_NAME);
        if (data == null)
        {
            throw new IllegalArgumentException("Data not found in request");
        }
        if (data.equals(""))
        {
            throw new ExperimentException("Data file contained zero data rows");
        }

        File dir = ensureUploadDirectory(context.getContainer());
        File file = createFile(protocol, dir, "tsv");
        ByteArrayInputStream bIn = new ByteArrayInputStream(data.getBytes(context.getRequest().getCharacterEncoding()));

        writeFile(bIn, file);
        return Collections.singletonMap(file.getName(), file);
    }

    public boolean isVisible()
    {
        return true;
    }
}
