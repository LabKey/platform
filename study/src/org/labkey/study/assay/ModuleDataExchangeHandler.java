/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.study.assay;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.study.assay.AssayRunUploadContext;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Apr 29, 2009
 */
public class ModuleDataExchangeHandler extends TsvDataExchangeHandler
{
    @Override
    protected Set<File> writeRunData(AssayRunUploadContext form, ExpRun run, File scriptDir, PrintWriter pw) throws Exception
    {
        Set<File> result = new HashSet<>();
        for (ExpData expData : run.getDataInputs().keySet())
        {
            // the original uploaded path
            pw.append(TsvDataExchangeHandler.Props.runDataUploadedFile.name());
            pw.append('\t');
            File file = expData.getFile();
            pw.println(file.getAbsolutePath());
            result.add(file);
        }

        if (form instanceof ModuleRunUploadContext)
        {
            ModuleRunUploadContext context = (ModuleRunUploadContext)form;

            List<Map<String, Object>> data = context.getRawData();
            if (data.size() > 0)
            {
                File runData = new File(scriptDir, RUN_DATA_FILE);
                getDataSerializer().exportRunData(context.getProtocol(), data, runData);
                result.add(runData);

                pw.append(Props.runDataFile.name());
                pw.append('\t');
                pw.println(runData.getAbsolutePath());
            }
        }
        return result;
    }
}
