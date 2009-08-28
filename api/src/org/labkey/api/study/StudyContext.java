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

package org.labkey.api.study;

import org.labkey.api.writer.ContainerUser;
import org.labkey.study.xml.StudyDocument;
import org.apache.log4j.Logger;

import java.io.File;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 1:17:01 PM
*/
public interface StudyContext extends ContainerUser
{
    public StudyDocument.Study getStudyXml() throws StudyImportException;
    public File getStudyDir(File root, String dirName, String source) throws StudyImportException;
    public Logger getLogger();
}
