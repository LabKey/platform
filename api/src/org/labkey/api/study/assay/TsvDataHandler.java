/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 11:17:56 AM
 */
public class TsvDataHandler extends AbstractAssayTsvDataHandler implements TransformDataHandler
{
    public static final DataType RELATED_TRANSFORM_FILE_DATA_TYPE = new DataType("RelatedTransformFile");
    public static final String NAMESPACE = "AssayRunTSVData";
    private static final AssayDataType DATA_TYPE;

    static
    {
        FileType fileType = new FileType(Arrays.asList(".tsv", ".csv", ".xls", ".xlsx", ".txt", ".fna", ".fasta"), ".tsv");
        fileType.setExtensionsMutuallyExclusive(false);
        DATA_TYPE = new AssayDataType(NAMESPACE, fileType);
    }
    
    private boolean _allowEmptyData = false;

    @Override
    public DataType getDataType()
    {
        return DATA_TYPE;
    }

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid) || RELATED_TRANSFORM_FILE_DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    protected boolean allowEmptyData()
    {
        return _allowEmptyData;
    }

    public void setAllowEmptyData(boolean allowEmpty)
    {
        _allowEmptyData = allowEmpty;
    }

    @Override
    public String getFileName(ExpData data, String defaultName)
    {
        ExpRun run = data.getRun();

        if (!data.isFinalRunOutput())
            return defaultName;

        if (defaultName.startsWith("uploadTemp"))
            return FilenameUtils.getBaseName(defaultName) + ".tsv";
        else
            return FilenameUtils.getFullPath(defaultName) + FilenameUtils.getBaseName(defaultName) + ".tsv";
    }

    @Override
    protected boolean shouldAddInputMaterials()
    {
        return true;
    }

    @Override
    public boolean hasContentToExport(ExpData data, File file)
    {
        return hasContentToExport(data, null != file ? file.toPath() : null);
    }

    @Override
    public boolean hasContentToExport(ExpData data, Path file)
    {
        return data.isFinalRunOutput();
    }

    @Override
    public void exportFile(ExpData data, Path dataFile, User user, OutputStream out) throws ExperimentException
    {
        if (data.isFinalRunOutput())
        {
            ExpRun run = data.getRun();
            ExpProtocol protocol = run.getProtocol();
            if (protocol != null)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider instanceof AbstractTsvAssayProvider)
                {
                    AssayProtocolSchema schema = provider.createProtocolSchema(user, data.getContainer(), protocol, null);
                    TableInfo dataTable = schema.createDataTable(false);

                    if (dataTable != null)
                    {
                        // Filter to get rows just from that one file
                        Results rs = new TableSelector(dataTable, new SimpleFilter(FieldKey.fromParts("DataId"), data.getRowId()), new Sort("RowId")).getResults();
                        if (rs.getSize() == 0)
                            return;

                        try
                        {
                            TSVGridWriter writer = new TSVGridWriter(rs);
                            writer.setColumnHeaderType(ColumnHeaderType.FieldKey);
                            File tempFile = File.createTempFile(FileUtil.getBaseName(FileUtil.getFileName(dataFile)), ".tsv");
                            writer.write(tempFile);
                            writer.close();
                            FileUtils.copyFile(tempFile, out);
                        }
                        catch (Exception e)
                        {
                            throw new ExperimentException("Problem creating TSV grid for run " + run.getName() + "(lsid: " + run.getLSID() + ")", e);
                        }
                    }
                }
            }
        }
    }
}
