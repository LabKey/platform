/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.fhcrc.cpas.exp.xml.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.User;
import org.labkey.api.study.*;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewURLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.FieldKey;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * User: peterhus
 * Date: Oct 11, 2007
 * Time: 11:29:58 PM
 */
public class XarAssayProvider extends AbstractAssayProvider
{
    public static final String PROTOCOL_LSID_NAMESPACE_PREFIX = "XarAssayProtocol";
    public static final String PROTOCOL_LSID_OBJECTID_PREFIX = "FileType.mzXML";
    public static final String TEMPLATE_RESOURCE_DIR = "org/labkey/xarassay/";
    public static final String TEMPLATE_FILE = "DefaultTemplate.xml";
    public static final String NAME = "GenericXarAssay";


    public static final String RUN_LSID_NAMESPACE_PREFIX = "ExperimentRun";
    public static final String RUN_LSID_OBJECT_ID_PREFIX = "MS2PreSearch";
    public static final String DATA_LSID_PREFIX = "Data";
    public static final String SAMPLE_PROPERTY_NAME= "StartingSample";
    protected static String _pipelineMzXMLExt = ".mzXML";

    public XarAssayProvider()
    {
        super(PROTOCOL_LSID_NAMESPACE_PREFIX, RUN_LSID_NAMESPACE_PREFIX, DATA_LSID_PREFIX);
    }


    public XarAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, String dataLSIDPrefix)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataLSIDPrefix);
    }

    public String getName()
    {
        return NAME;
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles)
    {
        List<AssayDataCollector> result = new ArrayList<AssayDataCollector>();
        if (uploadedFiles == null)
            uploadedFiles = Collections.<String, File>emptyMap();

        result.add(new XarAssayDataCollector(uploadedFiles));
        return result;
    }

    public boolean shouldShowDataDescription(ExpProtocol protocol)
    {
        return false;
    }


    public ExpData getDataForDataRow(Object dataRowId)
    {
        return null;

    }

    public ViewURLHelper getUploadWizardURL(Container container, ExpProtocol protocol)
    {
        ViewURLHelper url = new ViewURLHelper("XarAssay", "xarAssayUpload.view", container);
        url.addParameter("rowId", protocol.getRowId());

        return url;
    }

    protected void registerLsidHandler()
    {
        LsidManager.get().registerHandler(_runLSIDPrefix, new LsidManager.LsidHandler()
        {
            public ExpRun getObject(Lsid lsid)
            {
                return ExperimentService.get().getExpRun(lsid.toString());
            }

            public String getDisplayURL(Lsid lsid)
            {
                ExpRun run = ExperimentService.get().getExpRun(lsid.toString());
                if (run == null)
                    return null;
                ExpProtocol protocol = run.getProtocol();
                if (protocol == null)
                    return null;
                ViewURLHelper dataURL = new ViewURLHelper("Experiment","showRunGraph",run.getContainer());
                dataURL.addParameter("rowId", run.getRowId());
                return dataURL.getLocalURIString();
            }
        });
    }


    public Set<FieldKey> getParticipantIDDataKeys()
    {
        return null;
    }

    public Set<FieldKey> getVisitIDDataKeys()
    {
        return null;
    }


    public FieldKey getRunIdFieldKeyFromDataRow()
    {
        return null;
    }

    public FieldKey getDataRowIdFieldKey()
    {
        return null;
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return null;
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return null;
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return null;
    }

    public ViewURLHelper publish(User user, ExpProtocol protocol, Container study, Set<AssayPublishKey> dataKeys, List<String> errors)
    {
        throw new UnsupportedOperationException("Publish not implemented for assay type " + getName());
    }

    protected Domain createRunDomain(Container c, User user)
    {
        ExpSampleSet ms;
        try
        {
            ms = ExperimentService.get().ensureActiveSampleSet(c);
            if (ms.getLSID().equals(ExperimentService.get().ensureDefaultSampleSet().getLSID())
                    )
                ms = null;
        }
        catch (SQLException e)
        {
            ms=null;
        }

        Domain runDomain = super.createRunDomain(c, user);
        DomainProperty startingSampleProperty = addProperty(runDomain, getSamplePropertyName(), PropertyType.STRING);
        startingSampleProperty.setRequired(true);
        if(null!=ms)
            startingSampleProperty.setLookup(new Lookup(ms.getContainer(), "Samples", ms.getName()));

        return runDomain;
    }


    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = super.createDefaultDomains(c, user);

        // remove data properties since we don't same vthem
        String lsidName = "Data Properties";
        for (Domain d : result)
        {
            if (d.getName().equals(lsidName))
            {
                result.remove(d);
                break;
            }
        }

        return result;
    }

    public ExpRun saveExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
        Container c = context.getContainer();

        XarAssayForm form = (XarAssayForm)context;
        if ((null==form.getNumFilesRemaining()) || (form.getNumFilesRemaining()==0))
            throw new ExperimentException("No more files left to describe");

        ExpRun run = createExperimentRunFromXar(form);
        DbScope scope = ExperimentService.get().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        XarAssayRunSource src;
        try
        {
            Map<String, File> mapFiles ;
            mapFiles = context.getUploadedData();
            File f = mapFiles.get(form.getCurrentFileName());


            ViewBackgroundInfo info = new ViewBackgroundInfo(c, context.getUser(), new ViewURLHelper("Project","begin", c));
            XarAssayPipelineJob pj = new XarAssayPipelineJob(info, getLogFileFor(getAssayXarFile(c, form.getProtocol())));

            if (transactionOwner)
                scope.beginTransaction();

            src = new XarAssayRunSource(form, run);

            ExperimentService.get().loadExperiment(src, pj, false);

            savePropertyObject(context.getProtocol(), run.getLSID(), context.getRunProperties(), context.getContainer());
            savePropertyObject(context.getProtocol(), run.getLSID(), context.getUploadSetProperties(), context.getContainer());

            ExpRun rNew = ExperimentService.get().getExpRun(run.getLSID());
            if (transactionOwner)
                scope.commitTransaction();
            return rNew;
        }
        catch (Exception e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            if (transactionOwner)
                scope.closeConnection();
        }
    }


    protected ExpRun createExperimentRunFromXar(XarAssayForm context) throws ExperimentException
    {
        String name = context.getName();
        if (name==null)
            name = "Sample prep run for "  + context.getCurrentFileName();

        ExpRun run = ExperimentService.get().createExperimentRun(context.getContainer(), name);

        run.setProtocol(context.getProtocol());
        String entityId = GUID.makeGUID();
        Lsid lsid = new Lsid(getRunLsidNamespacePrefix(), "Folder-" + context.getContainer().getRowId(),
                getRunLsidObjectIdPrefix()+ "." + entityId);
        run.setLSID(lsid.toString());
        run.setComments(context.getComments());

        return run;
    }

    public ExpProtocol createAssayDefinition(User user, Container container, String name, String description, int maxMaterials) throws ExperimentException, SQLException
    {
        PipeRoot pr=null;
        try
        {
            pr = PipelineService.get().findPipelineRoot(container);
        }
        catch (SQLException e){
            e.printStackTrace();
        }
        if (null == pr )
            throw new RuntimeException("You must set a pipeline root in this folder or project before you save an Assay definition.");

        ExpSampleSet ms=null;
        try {ms = ExperimentService.get().ensureActiveSampleSet(container);}
        catch (SQLException e){}
        if ((null==ms)
                ||   ms.getLSID().equals(ExperimentService.get().ensureDefaultSampleSet().getLSID())
            //   || !ms.getContainer().equals(container.getId())
                )
            throw new ExperimentException("You must upload a Sample Set into this folder before you save this Assay definition.");

        String protocolLsid = new Lsid(getProtocolLsidNamespacePrefix(),"Folder-" + container.getRowId(),getProtocolLsidObjectidPrefix()+ "." + name ).toString();
        if (ExperimentService.get().getExpProtocol(protocolLsid) != null)
        {
            //    throw new ExperimentException("An assay with that name already exists");
        }

        ExpProtocol protocol = ExperimentService.get().createExpProtocol(container, name, ExpProtocol.ApplicationType.ExperimentRun);
        protocol.setProtocolDescription(description);
        protocol.setLSID(protocolLsid);
        protocol.setMaxInputMaterialPerInstance(maxMaterials);
        protocol.setMaxInputDataPerInstance(1);

        ViewBackgroundInfo info = new ViewBackgroundInfo(container, user, new ViewURLHelper("XarAssay","chooseAssay", container));
        XarSource xs = new XarAssayProtocolSource(container, protocol);
        XarAssayPipelineJob pj = null;
        try
        {
            pj = new XarAssayPipelineJob(info, getLogFileFor(getAssayXarFile(container, protocol)));
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        Integer expid = ExperimentService.get().loadExperiment(xs, pj, false);
        ExpProtocol px = ExperimentService.get().getExpProtocol(protocolLsid);
        px.retrieveProtocolParameters();
        return px;

    }

    // next three are workaround to unmoifiable map exception,   can't re pro reliably
/*
    public PropertyDescriptor[] getRunInputPropertyColumns(ExpProtocol protocol)
    {
        List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>(Arrays.asList(super.getRunInputPropertyColumns(protocol)));
        return result.toArray(new PropertyDescriptor[result.size()]);
    }


    public PropertyDescriptor[] getUploadSetColumns(ExpProtocol protocol)
    {
        List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>(Arrays.asList(super.getUploadSetColumns(protocol)));
        return result.toArray(new PropertyDescriptor[result.size()]);
    }


    public PropertyDescriptor[] getRunPropertyColumns(ExpProtocol protocol)
    {
        List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>(Arrays.asList(super.getRunPropertyColumns(protocol)));
              return result.toArray(new PropertyDescriptor[result.size()]);
    }
  */
    // todo:  should these all be static
    public String getProtocolLsidNamespacePrefix()
    {
        return PROTOCOL_LSID_NAMESPACE_PREFIX;
    }

    public String getProtocolLsidObjectidPrefix()
    {
        return PROTOCOL_LSID_OBJECTID_PREFIX;
    }

    public String getRunLsidNamespacePrefix()
    {
        return RUN_LSID_NAMESPACE_PREFIX;
    }

    public String getRunLsidObjectIdPrefix()
    {
        return RUN_LSID_OBJECT_ID_PREFIX;
    }

    public String getSamplePropertyName()
    {
        return SAMPLE_PROPERTY_NAME;
    }

    public String getTemplateResource()
    {
        return TEMPLATE_RESOURCE_DIR + TEMPLATE_FILE;
    }

    public String getTemplateDir()
    {
        return "protocols/" + getName();
    }

    public String getTemplateFileName()
    {
        return TEMPLATE_FILE;
    }


    public TableInfo createDataTable(QuerySchema schema, String alias, ExpProtocol protocol)
    {
        return null;
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return null;
    }

    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        return null;
    }

    protected File getAssayXarFile(Container container, ExpProtocol assayProtocol) throws IOException
    {
        File pipeRoot = null;

        try
        {
            pipeRoot = PipelineService.get().findPipelineRoot(container).getRootPath();
        }
        catch (SQLException e)
        {
            throw new IOException(e.getMessage());
        }
        File templateDir = new File(pipeRoot, getTemplateDir());
        if (!templateDir.exists())
            templateDir.mkdirs();

        File assayXarFile = new File(templateDir, "Folder-" + container.getRowId() + "-" + assayProtocol.getName() + ".xar.xml") ;

        if (!assayXarFile.exists())
        {
            copyDefaultTemplateXar(templateDir, assayXarFile, assayProtocol);
        }
        return assayXarFile;

    }

    protected void copyDefaultTemplateXar(File templateDir, File destXarFile, ExpProtocol assayProtocol) throws IOException
    {
        InputStream in;
        File templateXar = new File(templateDir, getTemplateFileName());
        if (!templateXar.exists())
        {
            in = getClass().getClassLoader().getResourceAsStream(getTemplateResource());

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            FileOutputStream fOut = new FileOutputStream(templateXar);
            PrintWriter writer = new PrintWriter(fOut);
            String line;
            try
            {
                while ((line = reader.readLine()) != null)
                {
                    writer.println(line);
                }
            }
            finally
            {
                reader.close();
                writer.close();
                fOut.close();
                in.close();
            }
        }


        in = new FileInputStream(templateXar);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        try
        {
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
                sb.append("\n");
            }
        }
        finally
        {
            reader.close();
            in.close();
        }

        Map<String,String> tokenMap = getProtocolTokenMap(assayProtocol);
        String xarXml = tokenReplace(sb.toString(), tokenMap);

        // Write the XAR to disk so that it can be loaded

        FileOutputStream fOut = new FileOutputStream(destXarFile);
        PrintWriter writer = new PrintWriter(fOut);
        try
        {
            writer.write(xarXml);
        }
        finally
        {
            writer.close();
            fOut.close();
        }
    }

    public static File getLogFileFor(File f) throws IOException
    {
        File xarDirectory = f.getParentFile();
        if (!xarDirectory.exists())
        {
            throw new IOException("Xar file parent directory does not exist");
        }
        String xarShortName = f.getName();
        int index = xarShortName.toLowerCase().lastIndexOf(".xml");
        if (index != -1)
        {
            xarShortName = xarShortName.substring(0, index);
        }
        return new File(xarDirectory, xarShortName + "log");

    }

    /**
     * Replaces tokens surrounded by @@TOKEN_NAME@@ in an input stream
     */
    protected static String tokenReplace(String s, Map<String, String> tokens)
    {
        StringBuilder sb = new StringBuilder(s);

        for (Map.Entry<String, String> entry :
                tokens.entrySet())
        {
            String replacement = PageFlowUtil.filter(entry.getValue(), false, false);
            replaceString(sb, entry.getKey(), replacement);
        }

        return sb.toString();
    }

    static void replaceString(StringBuilder sb, String oldString, String newString)
    {
        oldString = "@@" + oldString + "@@";
        int index = sb.indexOf(oldString);
        while (index != -1)
        {
            sb.replace(index, index + oldString.length(), newString);
            index = sb.indexOf(oldString);
        }
    }

    /*
    these string subistitutions are made at assay deinfintion time and saved in a copy of the xar file
     */
    protected static Map<String, String> getProtocolTokenMap(ExpProtocol p)
    {
        Map<String, String> tokenMap = new HashMap<String, String>();
        tokenMap.put("PROTOCOL_LSID", PageFlowUtil.filter(p.getLSID()));
        tokenMap.put("PROTOCOL_NAME", PageFlowUtil.filter(p.getName()));
        if(null != p.getProtocolDescription())
            tokenMap.put("PROTOCOL_DESCRIPTION", PageFlowUtil.filter(p.getProtocolDescription()));
        else
            tokenMap.put("PROTOCOL_DESCRIPTION", PageFlowUtil.filter(""));

        tokenMap.put("PROTOCOL_CONTAINER", "Folder-"+ p.getContainer().getRowId());
        tokenMap.put("PROTOCOL_PROJECT", "Project-"+ p.getContainer().getRowId());
        tokenMap.put("LSID_AUTHORITY", AppProps.getInstance().getDefaultLsidAuthority());
        tokenMap.put("PROTOCOL_LSID_NAMESPACE_PREFIX", PROTOCOL_LSID_NAMESPACE_PREFIX);
        tokenMap.put("PROVIDER_NAME", NAME);

        return tokenMap;
    }

    /*
    these substitutions are made at assay run creation time
     */
    protected static Map<String, String> getRunTokens(XarAssayForm form) throws ExperimentException, IOException
    {
        Map<String,String> tokenMap = new HashMap<String,String>();
        Map<String,File> dataFiles;
        PipeRoot pr = null;
        File curFile;
        try
        {
            pr = PipelineService.get().findPipelineRoot(form.getContainer());
            dataFiles = form.getUploadedData();
            curFile = dataFiles.get(form.getCurrentFileName());

            if (null != curFile)
            {
                String rPath = pr.relativePath(curFile);
                tokenMap.put("RUN_FILEPATH", PageFlowUtil.filter(rPath));
                String dirPath = pr.relativePath(curFile.getParentFile());
                tokenMap.put("RUN_FILEDIR", PageFlowUtil.filter(dirPath));
            }

            tokenMap.put("RUN_CONTAINER", "Folder-" + form.getContainer().getRowId());
        }


        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
        return tokenMap;
    }
    public static String getTemplateText(File template)  throws IOException
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(template));

            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
                sb.append("\n");
            }
            return sb.toString();
        }

        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException eio)
                {
                }
            }
        }

    }

    static int getActionSequence(ExperimentArchiveType xar, ProtocolBaseType protocol)
    {
        ExperimentArchiveType.ProtocolActionDefinitions defs = xar.getProtocolActionDefinitions();
        ProtocolActionSetType[] pas = defs.getProtocolActionSetArray();

        for (ProtocolActionSetType setType : pas)
        {
            for (ProtocolActionType actionType : setType.getProtocolActionArray())
            {
                if (actionType.getChildProtocolLSID().equals(protocol.getAbout()))
                    return actionType.getActionSequence();
            }
        }

        return -1;
    }

    public static String getTemplateInstanceText(File template, Map<String, String> tokenMap) throws IOException
    {
        String instanceText = getTemplateText(template);
        instanceText = tokenReplace(instanceText, tokenMap);
        return instanceText;
    }

    static ExperimentLogEntryType getLogEntry(ExperimentArchiveType xar, ProtocolBaseType protocol)
    {
        int seq = getActionSequence(xar, protocol);
        for (ExperimentLogEntryType entry : xar.getExperimentRuns().getExperimentRunArray(0).getExperimentLog().getExperimentLogEntryArray())
        {
            if (entry.getActionSequenceRef() == seq)
                return entry;
        }

        return null;
    }

    protected static Map<String, XarAssayProvider> getXarAssayProviders()
    {
        List<AssayProvider> ap = AssayService.get().getAssayProviders();
        Map<String, XarAssayProvider>  map = new HashMap<String, XarAssayProvider> ();
        for (AssayProvider ax : ap)
        {
            if (ax instanceof XarAssayProvider)
            {
                XarAssayProvider xa = (XarAssayProvider)ax;
                map.put(xa.getProtocolLsidNamespacePrefix(), xa);
            }
        }
        return map;
    }

    public static boolean isMzXMLFile(File file)
    {
        return file.getName().endsWith(_pipelineMzXMLExt);
    }

    public static class AnalyzeFileFilter extends PipelineProvider.FileEntryFilter
    {
        public boolean accept(File file)
        {
            // Show all mzXML files.
            if (isMzXMLFile(file))
                return true;

            return false;
        }
    }
}
