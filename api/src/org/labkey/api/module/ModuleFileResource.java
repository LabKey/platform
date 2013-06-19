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
package org.labkey.api.module;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.util.Pair;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/*
* User: Dave
* Date: Jan 16, 2009
* Time: 11:08:35 AM
*/

/**
 * Base-class for file-based resources within a module. This class provides
 * some basic functionality, such as tracking if this instance is stale compared
 * to the resource file(s), and returning the contents of files either as
 * a string or as a parsed XML Document
 */
public class ModuleFileResource
{
    protected Logger _log = Logger.getLogger(ModuleFileResource.class);
    
    private File _primaryFile;
    private long _primaryLastMod = 0;
    private List<Pair<File,Long>> _assocFiles;

    public ModuleFileResource(File primaryFile)
    {
        _primaryFile = primaryFile;
        _primaryLastMod = primaryFile.lastModified();
    }

    public File getPrimaryFile()
    {
        return _primaryFile;
    }

    public void addAssociatedFile(File file)
    {
        if(null == _assocFiles)
            _assocFiles = new ArrayList<>();

        _assocFiles.add(new Pair<>(file, file.lastModified()));
    }

    public List<Pair<File,Long>> getAssociatedFiles()
    {
        return _assocFiles;
    }

    public boolean isStale()
    {
        if(getPrimaryFile().lastModified() != _primaryLastMod)
            return true;

        if(null != getAssociatedFiles())
        {
            for(Pair<File,Long> file : getAssociatedFiles())
            {
                if(file.first.lastModified() != file.second.longValue())
                    return true;
            }
        }

        return false;
    }

    protected String getFileContents() throws IOException
    {
        return getFileContents(getPrimaryFile());
    }

    protected String getFileContents(Pair<File,Long> assocFile) throws IOException
    {
        return getFileContents(assocFile.first);
    }

    protected String getFileContents(File file) throws IOException
    {
        if(file.exists())
            return IOUtils.toString(new FileReader(file));
        else
            return null;
    }

    protected Document parseFile() throws ParserConfigurationException, IOException, SAXException
    {
        return parseFile(getPrimaryFile());
    }

    protected Document parseFile(Pair<File,Long> assocFile) throws ParserConfigurationException, IOException, SAXException
    {
        return parseFile(assocFile.first);
    }

    protected Document parseFile(File file) throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        DocumentBuilder db = dbf.newDocumentBuilder();

        return db.parse(file);
    }
}