/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.pipeline.protocol.xml.PipelineProtocolPropsDocument;
import org.labkey.api.util.FileUtil;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Knows how to deserialize protocol definitions that have been persisted on the server (as XML on the file system
 *
 * Created: Oct 7, 2005
 * @author bmaclean
 */
public abstract class PipelineProtocolFactory<T extends PipelineProtocol>
{
    protected static final String _pipelineProtocolDir = "protocols";
    private static final String _archivedProtocolDir = "archived";

    private static final Logger LOG = LogManager.getLogger(PipelineProtocolFactory.class);

    public static FileLike getProtocolRootDir(PipeRoot root)
    {
        FileLike systemDir = root.ensureSystemDirectory();
        return getProtocolRootDir(systemDir);
    }

    public static FileLike getProtocolRootDir(FileLike systemDir)
    {
        return systemDir.resolveChild(_pipelineProtocolDir);
    }

    public abstract String getName();

    public T load(PipeRoot root, String name, boolean archived) throws IOException
    {
        FileLike file = getProtocolFile(root, name, archived);
        try
        {
            Map<String, String> mapNS = new HashMap<>();
            mapNS.put("", PipelineProtocol._xmlNamespace);
            XmlOptions opts = new XmlOptions().setLoadSubstituteNamespaces(mapNS);

            PipelineProtocolPropsDocument doc =
                    PipelineProtocolPropsDocument.Factory.parse(file.openInputStream(), opts);
            PipelineProtocolPropsDocument.PipelineProtocolProps ppp =
                    doc.getPipelineProtocolProps();
            String type = ppp.getType();

            // Recognize very old files
            if (type.startsWith("org.fhcrc.cpas.ms2."))
            {
                type = type.replace("org.fhcrc.cpas.ms2.", "org.labkey.ms2.");
            }
            if (type.startsWith("org.labkey.ms2.protocol."))
            {
                type = type.replace("org.labkey.ms2.protocol.", "org.labkey.ms2.pipeline.");
            }

            PipelineProtocol protocol = (PipelineProtocol) Class.forName(type).getDeclaredConstructor().newInstance();
            PipelineProtocolPropsDocument.PipelineProtocolProps.Property[] props =
                    ppp.getPropertyArray();
            if (ppp.isSetTemplate())
            {
                String template = ppp.getTemplate();
                protocol.setTemplate(template);
            }

            for (PipelineProtocolPropsDocument.PipelineProtocolProps.Property prop : props)
            {
                protocol.setProperty(prop.getName(), prop.getStringValue());
            }

            return (T) protocol;
        }
        catch (Exception e)
        {
            throw new IOException("Failed to load protocol document " + file + ".", e);
        }
    }

    public boolean isValidProtocolName(String name)
    {
        return FileUtil.isLegalName(name);
    }

    public boolean exists(PipeRoot root, String name, boolean archived)
    {
        return getProtocolFile(root, name, archived).exists();
    }

    public FileLike getProtocolDir(PipeRoot root, boolean archived)
    {
        FileLike protocolDir = getProtocolRootDir(root).resolveChild(getName());
        if (archived)
            protocolDir = protocolDir.resolveChild(_archivedProtocolDir);
        return protocolDir;
    }

    public FileLike getProtocolFile(PipeRoot root, String name, boolean archived)
    {
        return getProtocolDir(root, archived).resolveChild(name + ".xml");
    }

    /** @return sorted list of protocol names */
    public String[] getProtocolNames(PipeRoot root, FileLike dirData, boolean archived)
    {
        HashSet<String> setNames = new HashSet<>();

        // Add <protocol-name>.xml files
        List<FileLike> files = getProtocolDir(root, archived).getChildren(f -> f.getName().endsWith(".xml") && !f.isDirectory());
        for (FileLike file : files)
        {
            final String name = file.getName();
            setNames.add(name.substring(0, name.lastIndexOf('.')));
        }

        // Add all directories that already exist in the analysis root.
        if (dirData != null && !archived)
        {
            files = dirData.resolveChild(getName()).getChildren(FileLike::isDirectory);

            for (FileLike file : files)
                setNames.add(file.getName());
        }

        String[] vals = setNames.toArray(new String[0]);
        Arrays.sort(vals, String.CASE_INSENSITIVE_ORDER);
        return vals;
    }

    /**
     *  Move the file for the specified protocol to or from the archived directory
     * @param root pipeline root for the container
     * @param name the protocol name
     * @param moveToArchive true if archiving the protocol; false for unarchiving
     * @return true if the file was successfully moved or does not exist; false on error moving or if the archived directory
     * can't be created
     */
    public boolean changeArchiveStatus(PipeRoot root, String name, boolean moveToArchive) throws IOException
    {
        // Is the file's current location opposite the destination? No sense in moving it if it's already where the caller wants it.
        if (exists(root, name, !moveToArchive))
        {
            if (moveToArchive)
            {
               FileLike archiveDir = getProtocolDir(root, true);
               if (!archiveDir.exists())
               {
                   FileUtil.createDirectories(archiveDir);
               }
               else if (archiveDir.isFile())
               {
                   LOG.error("Unable to create archived directory because a file with that name exists in the protocol directory: {}", getProtocolDir(root, false));
                   return false;
               }
            }

            try
            {
                Files.move(getProtocolFile(root, name, !moveToArchive).toNioPathForWrite(), getProtocolFile(root, name, moveToArchive).toNioPathForWrite());
            }
            catch (IOException e)
            {
                return false;
            }

            return true;
        }
        return true; // We don't care if the file doesn't exist (maybe was already in the destination?)
    }

    /**
     *  Delete the xml file of the specified protocol. Tries to resolve the file in the main folder first.
     *  If the file doesn't exist there, look in the archived folder
     * @param root pipeline root for the container
     * @param name the protocol name
     * @return true if the file was successfully deleted or does not exist
     */
    public boolean deleteProtocolFile(PipeRoot root, String name)
    {
        FileLike protocolFile = getProtocolFile(root, name, false);

        //If it doesn't exist, check archive
        if (!protocolFile.exists())
            protocolFile = getProtocolFile(root, name, true);

        //If it still doesn't exist, move on
        if (!protocolFile.exists())
        {
            return true; // We don't care if the file doesn't exist
        }

        try
        {
            return protocolFile.delete();
        }
        catch (IOException e)
        {
            LOG.debug("Error attempting to delete protocol file {}", protocolFile, e);
            return false;
        }
    }
}
