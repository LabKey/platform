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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.study.StudyContext;
import org.labkey.study.xml.StudyDocument;
import org.apache.log4j.Logger;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 10:00:46 AM
 */
public abstract class AbstractContext implements StudyContext
{
    private final User _user;
    private final Container _c;
    private final Logger _logger;
    private transient StudyDocument _studyDoc;   // XStream can't seem to serialize this XMLBean... so we load it on demand

    protected AbstractContext(User user, Container c, StudyDocument studyDoc, Logger logger)
    {
        _user = user;
        _c = c;
        _logger = logger;
        setStudyDocument(studyDoc);
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _c;
    }

    // Study node -- interesting to any top-level writer that needs to set info into study.xml
    public StudyDocument.Study getStudyXml() throws StudyImportException
    {
        return getStudyDocument().getStudy();
    }

    public Logger getLogger()
    {
        return _logger;
    }

    protected synchronized StudyDocument getStudyDocument() throws StudyImportException
    {
        return _studyDoc;
    }

    protected final synchronized void setStudyDocument(StudyDocument studyDoc)
    {
        _studyDoc = studyDoc;
    }
}
