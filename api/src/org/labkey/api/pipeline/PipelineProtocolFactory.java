/*
 * Copyright (c) 2005-2015 LabKey Corporation
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

import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.pipeline.protocol.xml.PipelineProtocolPropsDocument;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * PipelineProtocolType class
 * <p/>
 * Created: Oct 7, 2005
 *
 * @author bmaclean
 */
public abstract class PipelineProtocolFactory<T extends PipelineProtocol>
{
    protected static String _pipelineProtocolDir = "protocols";

    public static File getProtocolRootDir(PipeRoot root)
    {
        File systemDir = root.ensureSystemDirectory();
        return new File(systemDir, _pipelineProtocolDir);        
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

    public T load(PipeRoot root, String name) throws IOException
    {
        return load(getProtocolFile(root, name));
    }

    public T loadInstance(File file) throws IOException
    {
        return load(file);
    }

    protected T load(File file) throws IOException
    {
        try
        {
            Map<String, String> mapNS = new HashMap<>();
            mapNS.put("", PipelineProtocol._xmlNamespace);
            XmlOptions opts = new XmlOptions().setLoadSubstituteNamespaces(mapNS);

            PipelineProtocolPropsDocument doc =
                    PipelineProtocolPropsDocument.Factory.parse(file, opts);
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
            throw (IOException)new IOException("Failed to load protocol document " + file.getAbsolutePath() + ".").initCause(e);
        }
    }

    public boolean isValidProtocolName(String name)
    {
        for (int i = 0; i < name.length(); i++)
        {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != ' ')
                return false;
        }
        return true;
    }

    public boolean exists(PipeRoot root, String name)
    {
        return getProtocolFile(root, name).exists();
    }

    public File getProtocolDir(PipeRoot root)
    {
        return new File(getProtocolRootDir(root), getName());
    }

    public File getProtocolFile(PipeRoot root, String name)
    {
        return new File(getProtocolDir(root), name + ".xml");
    }

    /** @return sorted list of protocol names */
    public String[] getProtocolNames(PipeRoot root, File dirData)
    {
        HashSet<String> setNames = new HashSet<>();

        // Add <protocol-name>.xml files
        File[] files = getProtocolDir(root).listFiles(f -> f.getName().endsWith(".xml") && !f.isDirectory());
        if (files != null)
        {
            for (File file : files)
            {
                final String name = file.getName();
                setNames.add(name.substring(0, name.lastIndexOf('.')));
            }
        }

        // Add all directories that already exist in the analysis root.
        if (dirData != null)
        {
            files = new File(dirData, getName()).listFiles(File::isDirectory);

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
}
