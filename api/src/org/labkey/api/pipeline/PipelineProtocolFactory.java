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
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

    public static Path getProtocolRootDir(PipeRoot root)
    {
        Path systemDir = root.ensureSystemDirectoryPath();
        return systemDir.resolve(_pipelineProtocolDir);
    }

    public static File locateProtocolRootDir(File rootDir, File systemDir)
    {
        File protocolRootDir = new File(systemDir, _pipelineProtocolDir);
        File protocolRootDirLegacy = new File(rootDir, _pipelineProtocolDir);
        if (NetworkDrive.exists(protocolRootDirLegacy))
            protocolRootDirLegacy.renameTo(protocolRootDir);
        return protocolRootDir;
    }

    public abstract String getName();

    public T load(PipeRoot root, String name, boolean archived) throws IOException
    {
        return load(getProtocolFile(root, name, archived));
    }

    public T loadInstance(File file) throws IOException
    {
        return load(file);
    }

    protected T load(File file) throws IOException
    {
        return load(file.toPath());
    }

    protected T load(Path file) throws IOException
    {
        try
        {
            Map<String, String> mapNS = new HashMap<>();
            mapNS.put("", PipelineProtocol._xmlNamespace);
            XmlOptions opts = new XmlOptions().setLoadSubstituteNamespaces(mapNS);

            PipelineProtocolPropsDocument doc =
                    PipelineProtocolPropsDocument.Factory.parse(Files.newInputStream(file), opts);
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

            PipelineProtocol protocol = (PipelineProtocol) Class.forName(type).newInstance();
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
            throw (IOException)new IOException("Failed to load protocol document " + file.toAbsolutePath() + ".").initCause(e);
        }
    }

    public boolean isValidProtocolName(String name)
    {
        return FileUtil.isLegalName(name);
    }

    public boolean exists(PipeRoot root, String name, boolean archived)
    {
        return Files.exists(getProtocolFile(root, name, archived));
    }

    public Path getProtocolDir(PipeRoot root, boolean archived)
    {
        Path protocolDir = getProtocolRootDir(root).resolve(getName());
        if (archived)
            protocolDir = protocolDir.resolve(_archivedProtocolDir);
        return protocolDir;
    }

    public Path getProtocolFile(PipeRoot root, String name, boolean archived)
    {
        return getProtocolDir(root, archived).resolve(name + ".xml");
    }

    @Deprecated //Prefer the Path version
    public String[] getProtocolNames(PipeRoot root, File dirData, boolean archived)
    {
        return getProtocolNames(root, dirData.toPath(), archived);
    }

    /** @return sorted list of protocol names */
    public String[] getProtocolNames(PipeRoot root, Path dirData, boolean archived)
    {
        HashSet<String> setNames = new HashSet<>();

        // Add <protocol-name>.xml files
        File[] files = getProtocolDir(root, archived).toFile().listFiles(f -> f.getName().endsWith(".xml") && !f.isDirectory());
        if (files != null)
        {
            for (File file : files)
            {
                final String name = file.getName();
                setNames.add(name.substring(0, name.lastIndexOf('.')));
            }
        }

        // Add all directories that already exist in the analysis root.
        if (dirData != null && !archived)
        {
            files = dirData.resolve(getName()).toFile().listFiles(File::isDirectory);

            if (files != null)
            {
                for (File file : files)
                    setNames.add(file.getName());
            }
        }

        String[] vals = setNames.toArray(new String[setNames.size()]);
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
               Path archiveDir = getProtocolDir(root, true);
               if (!Files.exists(archiveDir))
               {
                   Files.createDirectories(archiveDir);
               }
               else if (Files.isRegularFile(archiveDir))
               {
                   LOG.error("Unable to create archived directory because a file with that name exists in the protocol directory: "
                           + getProtocolDir(root, false).toAbsolutePath());
                   return false;
               }
            }

            try
            {
                Files.move(getProtocolFile(root, name, !moveToArchive), getProtocolFile(root, name, moveToArchive));
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
        Path protocolFile = getProtocolFile(root, name, false);

        //If it doesn't exist, check archive
        if (!Files.exists(protocolFile))
            protocolFile = getProtocolFile(root, name, true);

        //If it still doesn't exist, move on
        if (!Files.exists(protocolFile))
        {
            return true; // We don't care if the file doesn't exist
        }

        try
        {
            return Files.deleteIfExists(protocolFile);
        }
        catch (IOException e)
        {
            LogManager.getLogger(PipelineProtocolFactory.class).debug("Error attempting to delete protocol file " + protocolFile, e);
            return false;
        }
    }
}
