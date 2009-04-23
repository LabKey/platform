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
import org.labkey.study.xml.StudyDocument;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 10:00:46 AM
 */
public class ExportContext
{
    private User _user;
    private Container _c;
    private StudyDocument _studyDoc;
    private boolean _locked = false;

    public ExportContext(User user, Container c)
    {
        _user = user;
        _c = c;
        _studyDoc = StudyXmlWriter.getStudyDocument();
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _c;
    }

    // Full study doc -- only interesting to StudyXmlWriter
    public StudyDocument getStudyDocument()
    {
        if (_locked)
            throw new IllegalStateException("Can't access StudyDocument after study.xml has been written");

        return _studyDoc;
    }

    // Study node -- interesting to any top-level writer that needs to set info into study.xml
    public StudyDocument.Study getStudyXml()
    {
        return getStudyDocument().getStudy();
    }

    public void lockStudyDocument()
    {
        _locked = true;
    }
}
