/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.fhcrc.cpas.pipeline.protocol.xml.PipelineProtocolPropsDocument;
import org.apache.xmlbeans.XmlOptions;
import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.util.*;

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
    protected static String _pipelineTemplateDir = "templates";

    public static File getProtocolRootDir(URI uriRoot)
    {
        File systemDir = PipelineService.get().ensureSystemDirectory(uriRoot);
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

    public T load(URI uriRoot, String name) throws IOException
    {
        return load(getProtocolFile(uriRoot, name));
    }

    public T loadInstance(File file) throws IOException
    {
        return load(file);
    }

    protected T load(File file) throws IOException
    {
        try
        {
            Map mapNS = new HashMap();
            mapNS.put("", PipelineProtocol._xmlNamespace);
            XmlOptions opts = new XmlOptions().setLoadSubstituteNamespaces(mapNS);

            PipelineProtocolPropsDocument doc =
                    PipelineProtocolPropsDocument.Factory.parse(file, opts);
            PipelineProtocolPropsDocument.PipelineProtocolProps ppp =
                    doc.getPipelineProtocolProps();
            String type = ppp.getType();

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

    public boolean exists(URI uriRoot, String name)
    {
        return getProtocolFile(uriRoot, name).exists();
    }

    public File getProtocolDir(URI uriRoot)
    {
        return new File(getProtocolRootDir(uriRoot), getName());
    }

    public File getTemplateDir(URI uriRoot)
    {
        return new File(getProtocolDir(uriRoot), _pipelineTemplateDir);
    }

    public File getProtocolFile(URI uriRoot, String name)
    {
        return new File(getProtocolDir(uriRoot), name + ".xml");
    }

    public File getTemplateFile(URI uriRoot, String name)
    {
        File templateDir = getTemplateDir(uriRoot);
        File file = new File(templateDir, name + ".xml");
        if (!file.exists())
        {
            File xarFile =  new File (templateDir, name + ".xar.xml");
            if (xarFile.exists())
                file = xarFile;
        }

        return file;
    }

    /**
     * @param uriRoot
     * @return Names of all the templates stored in the template directory
     */
    public List<String> getTemplateNames(URI uriRoot)
    {
        File[] files = getTemplateDir(uriRoot).listFiles(new FileFilter()
        {
            public boolean accept(File f)
            {
                return f.getName().endsWith(".xml") && !f.isDirectory();
            }
        });
        ArrayList<String> listNames = new ArrayList<String>();
        if (files != null)
        {
            for (File file : files)
            {
                final String name = file.getName();
                if (name.endsWith(".xar.xml"))
                    listNames.add(name.substring(0, name.length() - ".xar.xml".length()));
                else
                    listNames.add(name.substring(0, name.lastIndexOf('.')));
            }
        }

        return listNames;
    }

    public String[] getProtocolNames(URI uriRoot)
    {
        return getProtocolNames(uriRoot, null); 
    }

    /** @return sorted list of protocol names */
    public String[] getProtocolNames(URI uriRoot, File dirData)
    {
        HashSet<String> setNames = new HashSet<String>();

        // Add <protocol-name>.xml files
        File[] files = getProtocolDir(uriRoot).listFiles(new FileFilter()
        {
            public boolean accept(File f)
            {
                return f.getName().endsWith(".xml") && !f.isDirectory();
            }
        });
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
            files = new File(dirData, getName()).listFiles(new FileFilter() {
                public boolean accept(File f)
                {
                    return f.isDirectory();
                }
            });
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
