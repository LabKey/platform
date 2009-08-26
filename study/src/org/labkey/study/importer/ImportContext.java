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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.study.importer.StudyImporter.InvalidFileException;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.writer.AbstractContext;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.IOException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:48:59 PM
 */
public class ImportContext extends AbstractContext
{
    private final ActionURL _url;
    private final File _root;

    public ImportContext(User user, Container c, File root, ActionURL url)
    {
        super(user, c, null);  // XStream can't seem to serialize the StudyDocument XMLBean, so we always read the file on demand
        _url = url;
        _root = root;
    }

    @Deprecated
    // TODO: All importers should new up ActionURLs using getContainer() 
    public ActionURL getUrl()
    {
        return _url;
    }

    @Override
    protected synchronized StudyDocument getStudyDocument() throws StudyImportException
    {
        StudyDocument studyDoc = super.getStudyDocument();

        // XStream can't seem to serialize the StudyDocument XMLBean, so we initially set to null and parse the file on demand
        if (null == studyDoc)
        {
            try
            {
                studyDoc = readStudyDocument(_root);
            }
            catch (IOException e)
            {
                throw new StudyImportException("Exception loading study.xml file", e);
            }

            setStudyDocument(studyDoc);
        }

        return studyDoc;
    }


    private static StudyDocument readStudyDocument(File root) throws StudyImportException, IOException
    {
        File file = new File(root, "study.xml");

        if (!file.exists())
            throw new StudyImportException("Study.xml file does not exist.");

        StudyDocument studyDoc;

        try
        {
            studyDoc = StudyDocument.Factory.parse(file);
        }
        catch (XmlException e)
        {
            throw new InvalidFileException(root, file, e);
        }

        return studyDoc;
    }
}
