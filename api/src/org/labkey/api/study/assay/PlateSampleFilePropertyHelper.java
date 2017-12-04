/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.nab.NabUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.PlateUploadForm;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.InsertView;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User: brittp
 * Date: Aug 16, 2010 4:59:46 PM
 */
public class PlateSampleFilePropertyHelper extends PlateSamplePropertyHelper
{
    private static final String SAMPLE_FILE_INPUT_NAME = "__sampleMetadataFile__";
    private static final int BUFFER_SIZE = 2048;
    public static final String SAMPLE_WELLGROUP_COLUMN = "SampleWellGroup";
    public static final String PLATELOCATION_COLUMN = "PlateLocation";

    protected ExpProtocol _protocol;
    protected Map<String, Map<DomainProperty, String>> _sampleProperties;
    protected String _metadataNoun = "Sample";
    protected String _wellGroupColumnName = SAMPLE_WELLGROUP_COLUMN;
    private Container _container;
    private File _metadataFile;
    private SampleMetadataInputFormat _metadataInputFormat;

    public PlateSampleFilePropertyHelper(Container container, ExpProtocol protocol, List<? extends DomainProperty> domainProperties, PlateTemplate template, SampleMetadataInputFormat inputFormat)
    {
        super(domainProperties, template);
        _container = container;
        _protocol = protocol;
        _metadataInputFormat = inputFormat;
    }

    private MultipartFile getMetadataFile(HttpServletRequest request)
    {
        if (request instanceof MultipartHttpServletRequest)
        {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            String entryKeyToFind;
            if (_metadataInputFormat == SampleMetadataInputFormat.FILE_BASED)
            {
                entryKeyToFind = SAMPLE_FILE_INPUT_NAME;
            }
            else if (_metadataInputFormat == SampleMetadataInputFormat.COMBINED)
            {
                entryKeyToFind = AssayDataCollector.PRIMARY_FILE;
            }
            else
                return null;

            for (Map.Entry<String, MultipartFile> entry : multipartRequest.getFileMap().entrySet())
            {
                if (entryKeyToFind.equals(entry.getKey()))
                {
                    MultipartFile multipartFile = entry.getValue();
                    if (!multipartFile.isEmpty())
                        return multipartFile;
                }
            }
        }
        return null;
    }

    // Returns a map from sample wellgroup name to the properties of that sample.
    public Map<String, Map<DomainProperty, String>> getPostedPropertyValues(HttpServletRequest request) throws ExperimentException
    {
        Map<String, Map<DomainProperty, String>> result = new HashMap<>();
        Map<String, Map<DomainProperty, String>> sampleProperties = getSampleProperties(request);
        if (sampleProperties == null || sampleProperties.isEmpty())
            throw new ExperimentException(_metadataNoun + " metadata must be provided.");
        for (Map.Entry<String, Map<DomainProperty, String>> entry : sampleProperties.entrySet())
            result.put(entry.getKey(), entry.getValue());
        return result;
    }

    protected File getSampleMetadata(HttpServletRequest request) throws ExperimentException
    {
        if (_metadataFile != null)
            return _metadataFile;

        if (METADATA_PROVIDER_OPTION_PREVUPLOAD.equals(request.getParameter(METADATA_PROVIDER_INPUT_NAME)))
        {
            String relativePath = request.getParameter(METADATA_PREVUPLOAD_LOCATION);
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(_container);
            _metadataFile = pipelineRoot.resolvePath(relativePath);
            return _metadataFile;
        }

        if (_metadataInputFormat == SampleMetadataInputFormat.COMBINED &&
                PRIMARY_PREV_UPLOAD.equals(request.getParameter(DATA_COLLECTOR_NAME)))
        {
            String relativePath = request.getParameter(PRIMARY_PREV_UPLOAD_PATH);
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(_container);
            _metadataFile = pipelineRoot.resolvePath(relativePath);
            return _metadataFile;
        }

        // Assume that this is a new upload.
        MultipartFile metadata = getMetadataFile(request);
        if (metadata == null)
            return null;
        BufferedOutputStream fos = null;
        InputStream is = null;
        try
        {
            File uploadDirectory = AssayFileWriter.ensureUploadDirectory(_container);
            _metadataFile = AssayFileWriter.findUniqueFileName(metadata.getOriginalFilename(), uploadDirectory);
            fos = new BufferedOutputStream(new FileOutputStream(_metadataFile));
            is = metadata.getInputStream();
            byte data[] = new byte[BUFFER_SIZE];
            int bytes;
            while ((bytes = is.read(data, 0, BUFFER_SIZE)) != -1)
                fos.write(data, 0, bytes);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            try { if (fos != null) fos.close(); } catch (IOException ignored) {}
            try { if (is != null) is.close(); } catch (IOException ignored) {}
        }
        return _metadataFile;
    }

    private boolean domainPropertiesEqual(List<? extends DomainProperty> first, List<? extends DomainProperty> second)
    {
        if (first == second)
            return true;
        if (first == null || second == null)
            return false;
        if (first.size() != second.size())
            return false;
        Set<DomainProperty> firstSet = new HashSet<>();
        firstSet.addAll(first);
        Set<DomainProperty> secondSet = new HashSet<>();
        secondSet.addAll(second);
        return firstSet.equals(secondSet);
    }

    @Override
    public void setDomainProperties(List<? extends DomainProperty> domainProperties)
    {
        // short-circuit if the new properties are equal to the old properties; helpful for high-throughput NAb since
        // there are many samples and property lookup can be expensive.
        if (!domainPropertiesEqual(_domainProperties, domainProperties))
        {
            super.setDomainProperties(domainProperties);
            // Force recalculation of sample properties if our set of domain properties changes:
            _sampleProperties = null;
        }
    }

    @Override
    public Map<String, Map<DomainProperty, String>> getSampleProperties(HttpServletRequest request) throws ExperimentException
    {
        if (_sampleProperties != null)
            return _sampleProperties;

        File metadataFile = getSampleMetadata(request);
        if (metadataFile == null)
            return null;

        Map<String, Map<DomainProperty, String>> allProperties = new HashMap<>();
        try
        {
            Map<String, WellGroupTemplate> sampleGroupNames = getSampleWellGroupNameMap();

            DataLoader loader = DataLoaderService.get().createLoader(metadataFile, null, true, null, ExcelLoader.FILE_TYPE);

            boolean hasSampleNameCol = false;
            ColumnDescriptor[] columns = loader.getColumns();
            for (int col = 0; col < columns.length && !hasSampleNameCol; col++)
                hasSampleNameCol = _wellGroupColumnName.equals(columns[col].name);
            if (!hasSampleNameCol)
                throw new ExperimentException("Sample metadata file does not contain required column \"" + _wellGroupColumnName + "\".");

            for (Map<String, Object> row : loader)
            {
                String wellGroupName = (String) row.get(_wellGroupColumnName);
                WellGroupTemplate wellgroup = wellGroupName != null ? sampleGroupNames.get(wellGroupName) : null;
                if (wellgroup == null)
                {
                    throw new ExperimentException("Well group name \"" + (wellGroupName != null ? wellGroupName : "") +
                            "\" does not match any " + _metadataNoun + " well groups defined in plate template \"" + _template.getName() + "\"");
                }

                validateMetadataRow(row, wellGroupName, wellgroup);

                Map<DomainProperty, String> sampleProperties = allProperties.get(wellgroup.getName());
                if (sampleProperties == null)
                {
                    sampleProperties = new HashMap<>();
                    allProperties.put(wellgroup.getName(), sampleProperties);
                }

                for (DomainProperty property : _domainProperties)
                {
                    Object value = getValue(row, property);
                    String strVal = value != null ? value.toString() : null;

                    if (sampleProperties.containsKey(property) && !Objects.equals(sampleProperties.get(property), strVal)) {
                        throw new ExperimentException("Well group \"" + wellGroupName + "\" was specified more than once in the sample metadata file.");
                    }

                    sampleProperties.put(property, strVal);
                }
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException("Unable to parse sample properties file.  Please verify that the file is a valid TSV, CSV or Excel file.", e);
        }
        _sampleProperties = allProperties;
        return _sampleProperties;
    }

    protected void validateMetadataRow(Map<String, Object> row, String wellGroupName, WellGroupTemplate wellgroup) throws ExperimentException
    {
        String plateLocation = (String) row.get(PLATELOCATION_COLUMN);
        if (plateLocation != null && !plateLocation.equals(wellgroup.getPositionDescription()))
        {
            throw new ExperimentException("Well group \"" + wellGroupName + "\" is listed in plate location " +
                    plateLocation + ", but the stored plate template indicates that this group should be in location " +
                    wellgroup.getPositionDescription() + ".  Please contact an administrator to correct the saved template " +
                    "if sample locations have changed on the plate.");
        }
    }

    public File getMetadataFile()
    {
        return _metadataFile;
    }

    protected Object getValue(Map<String, Object> row, DomainProperty property)
    {
        Object value = row.get(property.getName());
        if (value != null)
            return value;
        for (String alias : property.getImportAliasSet())
        {
            value = row.get(alias);
            if (value != null)
                return value;
        }
        return null;
    }

    private static final String METADATA_PROVIDER_INPUT_NAME = "metadataFileProvider";
    private static final String METADATA_PROVIDER_OPTION_NEWUPLOAD = "newMetadataUpload";
    private static final String METADATA_PROVIDER_OPTION_PREVUPLOAD = "prevMetadataUpload";
    private static final String METADATA_PREVUPLOAD_LOCATION = "prevMetadataLocation";
    private static final String DATA_COLLECTOR_NAME = "dataCollectorName";
    private static final String PRIMARY_PREV_UPLOAD = "Previously uploaded files";
    private static final String PRIMARY_PREV_UPLOAD_PATH = "PreviouslyUploadedFilePaths";

    @Override
    public void addSampleColumns(InsertView view, User user, final AssayRunUploadForm defaultValueContext, final boolean errorReshow) throws ExperimentException
    {
        final File reshowFile;
        if (errorReshow)
            reshowFile = getMetadataFile();
        else if (defaultValueContext instanceof PlateUploadForm &&
                defaultValueContext.getReRunId() != null &&
                defaultValueContext.getProvider() instanceof PlateBasedAssayProvider)
        {
            int reRunId = defaultValueContext.getReRunId().intValue();
            reshowFile = ((PlateBasedAssayProvider) defaultValueContext.getProvider()).getSampleMetadataFile(defaultValueContext.getContainer(), reRunId);
        }
        else
            reshowFile = null;

        final boolean includesViruses = _template.getWellGroupCount(WellGroup.Type.VIRUS) > 0;

        DataRegion region = view.getDataRegion();
        region.addDisplayColumn(new SimpleDisplayColumn()
        {
            @Override
            public boolean isEditable()
            {
                return true;
            }

            @Override
            public void renderDetailsCaptionCell(RenderContext ctx, Writer out, @Nullable String cls) throws IOException
            {
                if (_metadataInputFormat == SampleMetadataInputFormat.FILE_BASED)
                {
                    String nounV1 = includesViruses ? "Sample/Virus" : "Sample";
                    String nounV2 = includesViruses ? "Sample and virus" : "Sample";
                    String helpPopup = PageFlowUtil.helpPopup(nounV1 + " Metadata", nounV2 + " metadata should be " +
                            "provided in a TSV, CSV or Excel file with one row per " + nounV1.toLowerCase() +
                            ".  This information is used to determine data processing and to map " + nounV2.toLowerCase() +
                            " values to plate locations.");

                    out.write("<td class=\"" + (cls != null ? cls : "lk-form-label") + "\">");
                    out.write(nounV1 + " Metadata" + helpPopup + " *");
                    out.write("</td>");
                }
            }

            @Override
            public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
            {
                if(_metadataInputFormat == SampleMetadataInputFormat.FILE_BASED)
                {
                    String nounV1 = includesViruses ? "Sample/Virus" : "Sample";
                    String nounV2 = includesViruses ? "Sample and virus" : "Sample";

                    out.write(nounV2 + " metadata should be uploaded in a TSV, CSV or Excel file with one row per " + nounV1.toLowerCase() + ".  ");
                    out.write(PageFlowUtil.textLink("Download template", PageFlowUtil.urlProvider(NabUrls.class).getSampleXLSTemplateURL(_container, _protocol)));
                    out.write("<br>");
                    if (reshowFile != null)
                    {
                        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(ctx.getContainer());
                        String filePath = PageFlowUtil.filter(pipelineRoot.relativePath(reshowFile).replace('\\', '/'));
                        String updateInputFn = "<script type=\"text/javascript\">" +
                                "function showMetadataPicker(showFilePicker) {\n" +
                                "document.getElementById('previousMetadataFileName').style.display = (showFilePicker ? 'none' : 'block');\n" +
                                "document.getElementById('newMetadataFileName').style.display = (!showFilePicker ? 'none' : 'block');\n" +
                                "}" +
                                "</script>";
                        out.write(updateInputFn);
                        out.write("\n<table><tr>");
                        out.write("\n<td><input type=\"radio\" name=\"" + METADATA_PROVIDER_INPUT_NAME + "\" value=\"" +
                                METADATA_PROVIDER_OPTION_PREVUPLOAD + "\"  onChange=\"showMetadataPicker(!this.checked);\" CHECKED></td>");
                        out.write("\n<td>Use the metadata that was already uploaded to the server</td>");
                        out.write("\n</tr><tr>");
                        out.write("\n<td></td><td><div id=\"previousMetadataFileName\" style=\"display:block\">" + PageFlowUtil.filter(reshowFile.getName()) + "</div></td>");
                        out.write("\n</tr><tr>");
                        out.write("\n<td><input type=\"hidden\" name=\"" + METADATA_PREVUPLOAD_LOCATION + "\" value=\"" + filePath  + "\">");
                        out.write("\n<input type=\"radio\" name=\"" + METADATA_PROVIDER_INPUT_NAME + "\" value=\"" +
                                METADATA_PROVIDER_OPTION_NEWUPLOAD + "\" onChange=\"showMetadataPicker(this.checked);\"></td>");
                        out.write("\n<td>Upload a data file</td>");
                        out.write("\n</tr><tr>");
                        out.write("\n<td></td><td><div id=\"newMetadataFileName\"  style=\"display:none\"><input type=\"file\" id=\"" +
                                SAMPLE_FILE_INPUT_NAME + "\" name=\"" + SAMPLE_FILE_INPUT_NAME + "\" size=\"40\" style=\"border: none\"></div></td>");
                        out.write("\n</tr></table>");
                    }
                    else
                    {
                        out.write("<table><tr><td></td><td>" +
                                "<input type=\"hidden\" name=\"" + METADATA_PROVIDER_INPUT_NAME + "\" value=\"" + METADATA_PROVIDER_OPTION_NEWUPLOAD + "\">" +
                                "<input type=\"file\" name=\"" + SAMPLE_FILE_INPUT_NAME + "\" size=\"40\" style=\"border: none\">" +
                                "</td></tr></table>");
                    }
                }
            }
        });

    }

    public SampleMetadataInputFormat getMetadataInputFormat()
    {
        return _metadataInputFormat;
    }
}
