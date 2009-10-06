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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.InvalidFileException;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
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
    private final File _studyXml;

    public ImportContext(User user, Container c, File studyXml, Logger logger)
    {
        super(user, c, null, logger);  // XStream can't seem to serialize the StudyDocument XMLBean, so we always read the file on demand
        _studyXml = studyXml;
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
                studyDoc = readStudyDocument(_studyXml);
            }
            catch (IOException e)
            {
                throw new StudyImportException("Exception loading study.xml file", e);
            }

            setStudyDocument(studyDoc);
        }

        return studyDoc;
    }

    // Assume file was referenced in study.xml file   // TODO: Context should hold onto the root -- shouldn't have to pass it in
    public File getStudyFile(File root, File dir, String name) throws StudyImportException
    {
        return getStudyFile(root, dir, name, _studyXml.getName());
    }

    public File getStudyFile(File root, File dir, String name, String source) throws StudyImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new StudyImportException(source + " refers to a file that does not exist: " + StudyImportException.getRelativePath(root, file));

        if (!file.isFile())
            throw new StudyImportException(source + " refers to " + StudyImportException.getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }

    public File getStudyDir(File root, String dirName) throws StudyImportException
    {
        File dir = null != dirName ? new File(root, dirName) : root;

        if (!dir.exists())
            throw new StudyImportException(_studyXml.getName() + " refers to a directory that does not exist: " + StudyImportException.getRelativePath(root, dir));

        if (!dir.isDirectory())
            throw new StudyImportException(_studyXml.getName() + " refers to " + StudyImportException.getRelativePath(root, dir) + ": expected a directory but found a file");

        return dir;
    }

    private StudyDocument readStudyDocument(File studyXml) throws StudyImportException, IOException
    {
        if (!studyXml.exists())
            throw new StudyImportException(studyXml.getName() + " file does not exist.");

        StudyDocument studyDoc;

        try
        {
            studyDoc = StudyDocument.Factory.parse(studyXml);
            XmlBeansUtil.validateXmlDocument(studyDoc, getLogger());
        }
        catch (XmlException e)
        {
            throw new InvalidFileException(studyXml.getParentFile(), studyXml, e);
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(studyXml.getParentFile(), studyXml, "File does not conform to study.xsd");
        }

        return studyDoc;
    }
}
