/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.xarassay;

import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.*;
import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.study.assay.AssayService;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: phussey
 * Date: Sep 19, 2007
 * Time: 2:50:52 PM
 */
public class XarAssayRunSource extends XarSource
{
    private ExpRun _run;
    private XarAssayForm _form;
    private File _root;
    private File _xmlTemplateFile;
    private ExperimentArchiveDocument _doc;

    public XarAssayRunSource(XarAssayForm form, ExpRun er)
    {
        _form=form;
        _run = er;

    }


    public ExperimentArchiveDocument getDocument() throws XmlException, IOException
    {
        if (null == _doc)
            try
            {
                init();
            }
            catch (ExperimentException e)
            {
                throw new IOException(e.getMessage());
            }

        return _doc;
    }

    public void init() throws IOException, ExperimentException
    {
        Map<String, String> tokenReplacements = new HashMap<String, String>();
        if (null !=_run)
        {
            assert(_form.getProtocol().getLSID().equals(_run.getProtocol().getLSID()));
            tokenReplacements.putAll(XarAssayProvider.getRunTokens(_form));
        }
        ExperimentArchiveDocument doc=null;
        String instanceText = null;

        try
        {
            instanceText = XarAssayProvider.getTemplateInstanceText(getXmlTemplateFile(), tokenReplacements);
            doc = ExperimentArchiveDocument.Factory.parse(instanceText);

            // empty out the protocol definition blocks
            ProtocolBaseType[] pt = new ProtocolBaseType[0];
            ProtocolActionSetType[] pat = new ProtocolActionSetType[0];
            doc.getExperimentArchive().getProtocolActionDefinitions().setProtocolActionSetArray(pat);
            doc.getExperimentArchive().getProtocolDefinitions().setProtocolArray(pt);

            ExperimentRunType er = doc.getExperimentArchive().getExperimentRuns().getExperimentRunArray(0);

            if (null != getRun())
            {
                er.setAbout(getRun().getLSID());
                er.setName(getRun().getName());
                er.setProtocolLSID(getRun().getProtocol().getLSID());
                if (null != getRun().getComments())
                    er.setComments(getRun().getComments());
            }

            applyRunMaterialsandFileOutputs(doc);
        }
        catch (XmlException e)
        {
            throw new ExperimentException(e);
        }
        _doc = doc;
    }


    public File getXmlTemplateFile()  throws IOException
    {
        if (null == _xmlTemplateFile)
        {
            //todo  check-- should I get the AssayProvider from the AssayService and cast?
            ExpProtocol p = _form.getProtocol();
            Container c = _form.getContainer();
            _xmlTemplateFile = ((XarAssayProvider)(AssayService.get().getProvider(p))).getAssayXarFile(c, p);
        }
        return _xmlTemplateFile;
    }

    public XarAssayForm getForm()
    {
        return _form;
    }


    public File getLogFile() throws IOException
    {
        return XarAssayProvider.getLogFileFor(getXmlTemplateFile());
    }


    public ExpRun getRun()
    {
        return _run;
    }

    public boolean shouldIgnoreDataFiles()
    {
        return true;
    }

    public String canonicalizeDataFileURL(String dataFileURL) throws XarFormatException
    {
        if (dataFileURL.startsWith("/") || dataFileURL.startsWith("\\"))
        {
            dataFileURL = dataFileURL.substring(1);
        }

        File xarDirectory = getRoot();
        File dataFile = new File(xarDirectory, dataFileURL);
        try
        {
            return dataFile.getCanonicalFile().toURI().toString();
        }
        catch (IOException e)
        {
            throw new XarFormatException(e);
        }
    }

    public File getRoot()
    {
        if (_root == null)
        {
            try
            {
                _root = PipelineService.get().findPipelineRoot(_form.getContainer()).getRootPath();
            }
            catch (SQLException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return _root;
    }

    private void applyRunMaterialsandFileOutputs(ExperimentArchiveDocument xardoc) throws XmlException, IOException
    {
        ExperimentArchiveType xar = xardoc.getExperimentArchive();
        MaterialBaseType[] inputMaterials = xar.getStartingInputDefinitions().getMaterialArray();
        Map<String, List<String>> roleLSIDs = new HashMap<String, List<String>>();
        for (int i = 0; i < inputMaterials.length; i++)
        {

            assert(i==0);

            String materialLSID;
            String materialName;

            MaterialBaseType inputMaterial = inputMaterials[i];
            String existingLSID = inputMaterial.getAbout();
            String roleName = null;
            if (existingLSID != null && existingLSID.startsWith("${Role:") && existingLSID.endsWith("}"))
            {
                roleName = existingLSID.substring("${Role:".length(), existingLSID.length() - "}".length());
            }
            if ("Material".equals(roleName))
            {
                roleName = null;
            }

            Map<PropertyDescriptor, String>  mRunProps =_form.getRunProperties();
            PropertyDescriptor pdExist=null;
            for (Map.Entry<PropertyDescriptor,String> entry : mRunProps.entrySet())
            {
                PropertyDescriptor pd = entry.getKey();
                if (pd.getName().equals(XarAssayProvider.SAMPLE_PROPERTY_NAME))
                    pdExist=pd;
            }
            String valExist = mRunProps.get(pdExist);
            if (null==pdExist || null == valExist || valExist.length() == 0)
            {
                    throw new RuntimeException("No sample selected");
            }

            Integer materialId = new Integer(valExist);
            ExpMaterial material = ExperimentService.get().getExpMaterial(materialId.intValue());
            materialLSID = material.getLSID();
            materialName = material.getName();

            List<String> existingLSIDs = roleLSIDs.get(roleName);
            if (existingLSIDs == null)
            {
                existingLSIDs = new ArrayList<String>();
                roleLSIDs.put(roleName, existingLSIDs);
            }
            existingLSIDs.add(materialLSID);
            inputMaterials[i].setAbout(materialLSID);
            inputMaterials[i].setName(materialName);

        }

        ExperimentArchiveType.ExperimentRuns runs = xardoc.getExperimentArchive().getExperimentRuns();
        if (runs != null)
        {
            for (ExperimentRunType run : runs.getExperimentRunArray())
            {
                if (run.getExperimentLog() != null && run.getExperimentLog().getExperimentLogEntryArray() != null)
                {
                    for (ExperimentLogEntryType logEntry : run.getExperimentLog().getExperimentLogEntryArray())
                    {
                        if (logEntry.getApplicationInstanceCollection() != null &&
                                logEntry.getApplicationInstanceCollection().getInstanceDetailsArray() != null)
                        {
                            for (InstanceDetailsType details : logEntry.getApplicationInstanceCollection().getInstanceDetailsArray())
                            {
                                if (details.getInstanceInputs() != null && details.getInstanceInputs().getMaterialLSIDArray() != null)
                                {
                                    for (InputOutputRefsType.MaterialLSID materialLSID : details.getInstanceInputs().getMaterialLSIDArray())
                                    {
                                        List<String> lsids = roleLSIDs.get(materialLSID.getRoleName());
                                        if (lsids != null && !lsids.isEmpty())
                                        {
                                            materialLSID.setStringValue(lsids.remove(0));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}