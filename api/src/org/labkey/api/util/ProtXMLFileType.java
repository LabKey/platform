/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

package org.labkey.api.util;

/**
 * protXML FileType  class
 * for .prot.xml, .prot.xml.gz, .protxml, etc
 * <p/>
 * Created: Feb 1, 2010
 *
 * @author bpratt
 */
public class ProtXMLFileType extends FileType
{
    /**
     * the normal constructor, gets you prot.xml, prot.xml.gz, etc
     */
    public ProtXMLFileType()
    {
        super(".prot.xml",FileType.gzSupportLevel.SUPPORT_GZ);
        addSuffix(".protxml"); // old skool
        addSuffix(".pep-prot.xml"); // old skool
    }

}