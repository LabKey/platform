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
import org.fhcrc.cpas.exp.xml.DataBaseType;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.fhcrc.cpas.exp.xml.ExperimentRunType;
import org.fhcrc.cpas.exp.xml.MaterialBaseType;
import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.study.assay.AssayService;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


/**
 * User: phussey
 * Date: Sep 17, 2007
 * Time: 12:30:55 AM
 */
public class XarAssayProtocolSource extends XarSource

{
    private ExpProtocol _p;
    private Container _c;
    private File _root;
    private File _log;
    private ExperimentArchiveDocument _doc;


    public XarAssayProtocolSource(Container c, ExpProtocol p)
    {
        _p = p;
        _c = c;
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
                _root = PipelineService.get().findPipelineRoot(_c).getRootPath();
            }
            catch (SQLException e)
            {
                e.printStackTrace();  
            }
        }
        return _root;
    }

    public File getLogFile() throws IOException
    {
        if (null == _log )
        {
            try
            {
                //todo check if this is the right way to get the correct log file
                File template = ((XarAssayProvider)(AssayService.get().getProvider(_p))).getAssayXarFile(_c, _p);
                _log = XarAssayProvider.getLogFileFor(template);

            }
            catch (Exception e)
            {
                throw new IOException(e.getMessage());
            }
        }
        return _log;
    }



    public void init() throws IOException, ExperimentException
    {
        Map<String,String> tokenMap = new HashMap<String,String>();
        String lsidPrefix = new Lsid(_p.getLSID()).getNamespacePrefix();
        XarAssayProvider xap = XarAssayProvider.getXarAssayProviders().get(lsidPrefix) ;
        File template = xap.getAssayXarFile(_c, _p);
        String instanceText = XarAssayProvider.getTemplateInstanceText(template, tokenMap);
        _log = XarAssayProvider.getLogFileFor(template);

        ExperimentArchiveDocument doc = null;
        try
        {
            doc = ExperimentArchiveDocument.Factory.parse(instanceText);
            // clear out the parts of the template that we don't want loaded at protocol definition time
            doc.getExperimentArchive().getExperimentRuns().setExperimentRunArray(new ExperimentRunType[0]);
            doc.getExperimentArchive().getStartingInputDefinitions().setMaterialArray(new MaterialBaseType[0]);
            doc.getExperimentArchive().getStartingInputDefinitions().setDataArray(new DataBaseType[0]);
        }
        catch (XmlException e)
        {
            throw new ExperimentException(e);
        }
        _doc=doc;



    }

}
