/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.writer.AbstractContext;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:48:59 PM
 */
public class StudyImportContext extends AbstractContext
{
    private File _studyXml;

    // Study design table maps (used only in Dataspace case) to help map dataset FKs
    private Map<String, Map<Object, Object>> _dataspaceTableIdMapMap = new CaseInsensitiveHashMap<>();

    // Required for xstream serialization on Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    private StudyImportContext()
    {
        //noinspection NullableProblems
        super(null, null, null, null, null);
    }

    public StudyImportContext(User user, Container c, LoggerGetter logger)
    {
        super(user, c, null, logger, null);
    }

    public StudyImportContext(User user, Container c, File studyXml, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, null, logger, root);  // XStream can't seem to serialize the StudyDocument XMLBean, so we always read the file on demand
        _studyXml = studyXml;
    }

    public StudyImportContext(User user, Container c, StudyDocument studyDoc, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, studyDoc, logger, root); 
    }

    @Override
    public synchronized StudyDocument getDocument() throws ImportException
    {
        StudyDocument studyDoc = super.getDocument();

        // XStream can't seem to serialize the StudyDocument XMLBean, so we initially set to null and parse the file on demand
        if (null == studyDoc)
        {
            try
            {
                studyDoc = readStudyDocument(_studyXml);
            }
            catch (IOException e)
            {
                throw new ImportException("Exception loading study.xml file", e);
            }

            setDocument(studyDoc);
        }

        return studyDoc;
    }

    // TODO: this should go away once study import fully supports using VirtualFile
    public File getStudyFile(VirtualFile root, VirtualFile dir, String name) throws ImportException
    {
        return getStudyFile(new File(root.getLocation()), new File(dir.getLocation()), name, "study.xml");
    }

    // Assume file was referenced in study.xml file   // TODO: Context should hold onto the root -- shouldn't have to pass it in
    public File getStudyFile(File root, File dir, String name) throws ImportException
    {
        return getStudyFile(root, dir, name, _studyXml.getName());
    }

    public File getStudyFile(File root, File dir, String name, String source) throws ImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new ImportException(source + " refers to a file that does not exist: " + ImportException.getRelativePath(root, file));

        if (!file.isFile())
            throw new ImportException(source + " refers to " + ImportException.getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }

    private StudyDocument readStudyDocument(File studyXml) throws ImportException, IOException
    {
        if (!studyXml.exists())
            throw new ImportException(studyXml.getName() + " file does not exist.");

        StudyDocument studyDoc;

        try
        {
            studyDoc = StudyDocument.Factory.parse(studyXml, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(studyDoc, studyXml.getName());
        }
        catch (XmlException e)
        {
            throw new InvalidFileException(studyXml.getParentFile(), studyXml, e);
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(studyXml.getParentFile(), studyXml, e);
        }

        return studyDoc;
    }

    public File getSpecimenArchive(VirtualFile root) throws ImportException
    {
        StudyDocument.Study.Specimens specimens = getXml().getSpecimens();

        if (null != specimens && null != specimens.getDir())
        {
            VirtualFile specimenDir = root.getDir(specimens.getDir());

            if (null != specimenDir && null != specimens.getFile())
                return getStudyFile(root, specimenDir, specimens.getFile());
        }

        return null;
    }

    @Override
    public Double getArchiveVersion()
    {
        try {
            StudyDocument studyDoc = getDocument();
            return studyDoc.getStudy() != null ? studyDoc.getStudy().getArchiveVersion() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public Map<Object, Object> getDataspaceTableIdMap(String key)
    {
        Map<Object, Object> map = _dataspaceTableIdMapMap.get(key);
        if (null == map)
            map = Collections.emptyMap();
        return map;
    }

    public void addDataspaceTableIdMap(String key, @NotNull Map<Object, Object> map)
    {
        _dataspaceTableIdMapMap.put(key, map);
    }
}
