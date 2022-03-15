/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.StashingResultsFactory;
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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
        FileType fileType = new FileType(Arrays.asList(".tsv", ".csv", ".xls", ".xlsx", ".txt", ".fna", ".fasta", ".zip"), ".tsv");
        fileType.setExtensionsMutuallyExclusive(false);
        DATA_TYPE = new AssayDataType(NAMESPACE, fileType);
    }
    
    private boolean _allowEmptyData = false;

    @Override
    public DataType getDataType()
    {
        return DATA_TYPE;
    }

    @Override
    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid) || RELATED_TRANSFORM_FILE_DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    @Override
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
        if (!data.isFinalRunOutput())
            return defaultName;

        if (defaultName.startsWith(AssayFileWriter.TEMP_DIR_NAME))
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
                    TableInfo dataTable = schema.createDataTable(null, false);

                    if (dataTable != null)
                    {
                        // Filter to get rows just from that one file
                        TableSelector ts = new TableSelector(dataTable, new SimpleFilter(FieldKey.fromParts("DataId"), data.getRowId()), new Sort("RowId"));
                        // Be sure to request lookup values and other renderer-required info, see issue 36746
                        ts.setForDisplay(true);

                        try (var factory = new StashingResultsFactory(ts))
                        {
                            Results results = factory.get();
                            if (results.getSize() == 0)
                                return;

                            File tempFile = File.createTempFile(FileUtil.getBaseName(FileUtil.getFileName(dataFile)), ".tsv");

                            // Figure out the subset of columns to actually export in the TSV, see issue 36746
                            Set<FieldKey> ignored = Set.of(FieldKey.fromParts("Run"), FieldKey.fromParts("RowId"), FieldKey.fromParts("DataId"), FieldKey.fromParts("Folder"));
                            List<DisplayColumn> displayColumns = new ArrayList<>();
                            for (ColumnInfo column : dataTable.getColumns())
                            {
                                if (!ignored.contains(column.getFieldKey()))
                                {
                                    displayColumns.add(column.getRenderer());
                                }
                            }

                            try (TSVGridWriter writer = new TSVGridWriter(factory, displayColumns))
                            {
                                writer.setColumnHeaderType(ColumnHeaderType.FieldKey);
                                writer.write(tempFile);
                            }

                            FileUtils.copyFile(tempFile, out);
                        }
                        catch (IOException | SQLException e)
                        {
                            throw new ExperimentException("Problem creating TSV grid for run " + run.getName() + "(lsid: " + run.getLSID() + ")", e);
                        }
                    }
                }
            }
        }
    }
}
