/*
 * Copyright (c) 2007-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.files;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.ActionOptions;
import org.labkey.data.xml.PipelineOptionsDocument;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Feb 2, 2010
 * Time: 9:35:36 AM
 */
public class FilesAdminOptions
{
    private boolean _importDataEnabled = true;
    private List<PipelineActionConfig> _pipelineConfig = new ArrayList<PipelineActionConfig>();
    private Container _container;

    public FilesAdminOptions(Container c, String xml)
    {
        _container = c;
        if (xml != null)
            _init(xml);
    }

    private void _init(String xml)
    {
        try {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();

            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.parse(xml, options);
            XmlBeansUtil.validateXmlDocument(doc);

            PipelineOptionsDocument.PipelineOptions pipeOptions = doc.getPipelineOptions();
            if (pipeOptions != null)
            {
                _importDataEnabled = pipeOptions.getImportEnabled();
                ActionOptions actionOptions = pipeOptions.getActionConfig();
                if (actionOptions != null)
                {
                    for (ActionOptions.DisplayOption o : actionOptions.getDisplayOptionArray())
                    {
                        _pipelineConfig.add(new PipelineActionConfig(o.getId(), o.getState()));
                    }
                }
            }
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }
        catch (XmlException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isImportDataEnabled()
    {
        return _importDataEnabled;
    }

    public void setImportDataEnabled(boolean importDataEnabled)
    {
        _importDataEnabled = importDataEnabled;
    }

    public List<PipelineActionConfig> getPipelineConfig()
    {
        return _pipelineConfig;
    }

    public void setPipelineConfig(List<PipelineActionConfig> pipelineConfig)
    {
        _pipelineConfig = pipelineConfig;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String serialize()
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PipelineOptionsDocument doc = PipelineOptionsDocument.Factory.newInstance();
            PipelineOptionsDocument.PipelineOptions pipelineOptions = doc.addNewPipelineOptions();

            pipelineOptions.setImportEnabled(isImportDataEnabled());
            
            if (!_pipelineConfig.isEmpty())
            {
                ActionOptions actionOptions = pipelineOptions.addNewActionConfig();

                for (PipelineActionConfig ac : _pipelineConfig)
                {
                    ActionOptions.DisplayOption displayOption = actionOptions.addNewDisplayOption();

                    displayOption.setId(ac.getId());
                    displayOption.setState(ac.getState().name());
                }
            }
            XmlBeansUtil.validateXmlDocument(doc);
            doc.save(output, XmlBeansUtil.getDefaultSaveOptions());

            return output.toString();
        }
        catch (Exception e)
        {
            // This is likely a code problem -- propagate it up so we log to mothership
            throw new RuntimeException(e);
        }
        finally
        {
            IOUtils.closeQuietly(output);
        }
    }
}
