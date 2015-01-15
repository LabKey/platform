/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.util.FileType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
    protected boolean shouldAddInputMaterials()
    {
        return true;
    }

    public void importTransformDataMap(ExpData data, AssayRunUploadContext context, ExpRun run, List<Map<String, Object>> dataMap) throws ExperimentException
    {
        try
        {
            importRows(data, context.getUser(), run, context.getProtocol(), context.getProvider(), dataMap);
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e.toString(), e);
        }
    }
}
