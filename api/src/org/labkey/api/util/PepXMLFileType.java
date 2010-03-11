/*
 * Copyright (c) 2010 LabKey Corporation
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
 * pepXML FileType  class
 * for .pep.xml, .pep.xml.gz, .pepxml, etc
 * <p/>
 * Created: Feb 1, 2010
 *
 * @author bpratt
 */
public class PepXMLFileType extends FileType
{
    /**
     * the normal constructor, gets you pep.xml, pep.xml.gz, etc
     */
    public PepXMLFileType()
    {
        this(false); // don't accept .xml as an extension
    }

    /**
     * optionally allows .xml, .pepxml, to support older Out2XML, Mascot2XML etc
     * @param supportAncientConverters
     */
    public PepXMLFileType(boolean supportAncientConverters)
    {
        super(".pep.xml",gzSupportLevel.SUPPORT_GZ);
        addSuffix(".pepxml"); // old skool
        if (supportAncientConverters)
        {
            addSuffix(".xml"); // old skool
            // worry about the fact that the old style .xml extension could pick up .pep-prot.xml
            addAntiFileType(new ProtXMLFileType());
        }
    }
}