/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:48:59 PM
 */
public class StudyImportContext extends SimpleStudyImportContext
{
    public static final String ALLOW_DOMAIN_UPDATES = "allowDomainUpdates";

    private Path _studyXml;
    private final HashMap<String, String> _props = new HashMap<>();

    // Study design table maps (primarily in Dataspace case) to help map dataset FKs
    private final Map<String, Map<Object, Object>> _tableIdMapMap = new CaseInsensitiveHashMap<>();

    // Required for xstream serialization on Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    private StudyImportContext()
    {
        super(null, null, null, null, null, null);
    }

    @Deprecated //Use Builder
    public StudyImportContext(User user, Container c, Set<String> dataTypes, LoggerGetter logger)
    {
        super(user, c, null, dataTypes, logger, null);
    }

    @Deprecated //Use Builder
    public StudyImportContext(User user, Container c, File studyXml, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        this(user, c, studyXml.toPath(), dataTypes, logger, root);
    }

    @Deprecated //Use Builder
    public StudyImportContext(User user, Container c, Path studyXml, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, null, dataTypes, logger, root);  // XStream can't seem to serialize the StudyDocument XMLBean, so we always read the file on demand
        _studyXml = studyXml;
    }

    @Deprecated //Use Builder
    public StudyImportContext(User user, Container c, StudyDocument studyDoc, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, studyDoc, dataTypes, logger, root);
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

    // Convenience method that either returns a StudyImpl (if it exists) or throws. Gets a fresh study on every call to ensure it's completely up-to-date
    public @NotNull StudyImpl getStudyImpl()
    {
        StudyImpl study = StudyManager.getInstance().getStudy(getContainer());

        if (null == study)
            throw new IllegalStateException("Study does not exist");

        return study;
    }

    // TODO: this should go away once study import fully supports using VirtualFile -  HMMM.  Why doesn't it?
    @Deprecated
    private Path getStudyFile(VirtualFile root, VirtualFile dir, String name) throws ImportException
    {
        Path rootFile = FileUtil.stringToPath(getContainer(), root.getLocation());
        Path dirFile = FileUtil.stringToPath(getContainer(), dir.getLocation());
        Path file = dirFile.resolve(name);
        String source = "study.xml";

        if (!Files.exists(file))
            throw new ImportException(source + " refers to a file that does not exist: " + ImportException.getRelativePath(rootFile, file));

        if (!Files.isRegularFile(file))
            throw new ImportException(source + " refers to " + ImportException.getRelativePath(rootFile, file) + ": expected a file but found a directory");

        return file;
    }

    private StudyDocument readStudyDocument(Path studyXml) throws ImportException, IOException
    {
        if (!Files.exists(studyXml))
            throw new ImportException(studyXml.getFileName() + " file does not exist.");

        StudyDocument studyDoc;

        try (InputStream inputStream = Files.newInputStream(studyXml))
        {
            studyDoc = StudyDocument.Factory.parse(inputStream, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(studyDoc, studyXml.getFileName().toString());
        }
        catch (XmlException | XmlValidationException e)
        {
            throw new InvalidFileException(studyXml.getParent(), studyXml, e);
        }

        return studyDoc;
    }

    public Path getSpecimenArchive(VirtualFile root) throws ImportException
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
        try
        {
            StudyDocument studyDoc = getDocument();
            return studyDoc.getStudy() != null ? studyDoc.getStudy().getArchiveVersion() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public Map<String, Map<Object, Object>> getTableIdMapMap()
    {
        return _tableIdMapMap;
    }

    public Map<Object, Object> getTableIdMap(String key)
    {
        Map<Object, Object> map = _tableIdMapMap.get(key);
        if (null == map)
            map = Collections.emptyMap();
        return map;
    }

    public void setProperties(Map<String, String> props)
    {
        this._props.putAll(props);
    }

    public void setProperties(InputStream is) throws IOException
    {
        if (is == null)
            return;

        try (is)
        {
            Properties props = new Properties();
            props.load(is);
            for (Map.Entry<Object, Object> entry : props.entrySet())
            {
                this._props.put( StringUtils.trimToEmpty((String) entry.getKey()).toLowerCase(),
                        StringUtils.trimToEmpty((String) entry.getValue()));
            }
        }
    }

    public Map<String, String> getProperties()
    {
        return this._props;
    }

    public void addTableIdMap(String key, @NotNull Map<Object, Object> map)
    {
        _tableIdMapMap.put(key, map);
    }

    public static class Builder {
        private final User _user;
        private final Container _container;
        private StudyDocument _studyDocument;
        private Path _studyXml;
        private Set<String> _dataTypes;
        private LoggerGetter _loggerGetter;
        private VirtualFile _root;

        public Builder(User user, Container c)
        {
            _user = user;
            _container = c;
        }

        public Builder(StudyImportContext original) throws ImportException
        {
            _user = original.getUser();
            _container = original.getContainer();
            _studyDocument = original.getDocument();
            _dataTypes = original.getDataTypes();
            _loggerGetter = original.getLoggerGetter();
            _root = original.getRoot();
        }

        public Builder withStudyXml(File studyXml)
        {
            _studyXml = studyXml.toPath();
            return this;
        }

        public Builder withStudyXml(Path studyXml)
        {
            _studyXml = studyXml;
            return this;
        }

        public Builder withDocument(StudyDocument studyDocument)
        {
            _studyDocument = studyDocument;
            return this;
        }

        public Builder withDataTypes(Set<String> dataTypes)
        {
            _dataTypes = dataTypes;
            return this;
        }

        public Builder withLogger(LoggerGetter logger)
        {
            _loggerGetter = logger;
            return this;
        }

        public Builder withRoot(VirtualFile root)
        {
            _root = root;
            return this;
        }

        public StudyImportContext build()
        {
            StudyImportContext ctx = new StudyImportContext(_user, _container, _studyDocument, _dataTypes, _loggerGetter, _root);
            ctx._studyXml = _studyXml;
            return ctx;
        }
    }
}
