/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLManager
{
    private static final ETLManager INSTANCE = new ETLManager();

    private static final Logger LOG = Logger.getLogger(ETLManager.class);

    private ETLManager() {}

    public static ETLManager get()
    {
        return INSTANCE;
    }

    public List<ETLDescriptor> getETLs()
    {
        List<ETLDescriptor> result = new ArrayList<ETLDescriptor>();

        Path etlsDirPath = new Path("etls");

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
            if (etlsDir != null)
            {
                for (Resource etlDir : etlsDir.list())
                {
                    ETLDescriptor descriptor = parseETL(etlDir.find("config.xml"));
                    if (descriptor != null)
                    {
                        result.add(descriptor);
                    }
                }
            }

        }

        return result;
    }

    private ETLDescriptor parseETL(Resource resource)
    {
        if (resource != null && resource.isFile())
        {
            InputStream inputStream = null;
            try
            {
                inputStream = resource.getInputStream();
                if (inputStream != null)
                {
                    XmlOptions options = new XmlOptions();
                    options.setValidateStrict();
                    EtlDocument document = EtlDocument.Factory.parse(inputStream, options);
                    return new ETLDescriptor(document.getEtl());
                }
            }
            catch (IOException e)
            {
                LOG.warn("Unable to parse " + resource, e);
            }
            catch (XmlException e)
            {
                LOG.warn("Unable to parse " + resource, e);
            }
            finally
            {
                if (inputStream != null) { try { inputStream.close(); } catch (IOException ignored) {} }
            }
        }
        return null;
    }

}
