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

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.study.xml.StudyDocument;

import java.util.Set;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:43:07 PM
 */
public class ExportContext extends AbstractContext
{
    private final boolean _oldFormats;
    private final Set<String> _dataTypes;

    private boolean _locked = false;

    public ExportContext(User user, Container c, boolean oldFormats, Set<String> dataTypes)
    {
        super(user, c, StudyXmlWriter.getStudyDocument());
        _oldFormats = oldFormats;
        _dataTypes = dataTypes;
    }

    public void lockStudyDocument()
    {
        _locked = true;
    }

    @Override
    // Full study doc -- only interesting to StudyXmlWriter
    public StudyDocument getStudyDocument()
    {
        if (_locked)
            throw new IllegalStateException("Can't access StudyDocument after study.xml has been written");

        return super.getStudyDocument();
    }

    public boolean useOldFormats()
    {
        return _oldFormats;
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }
}
