/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.qc;

import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.Handler;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.commons.lang.StringUtils;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.activation.DataHandler;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/*
* User: Karl Lum
* Date: Jan 7, 2009
* Time: 5:16:43 PM
*/
public class TsvDataExchangeHandler implements DataExchangeHandler
{
    enum Props {
        assayId,                // the assay id from the run properties field
        runComments,            // run properties comments
        containerPath,
        assayType,              // assay definition name : general, nab, elispot etc.
        assayName,              // assay instance name
        userName,               // user email

        runDataFile,
        errorsFile,
    }
    public static final String VALIDATION_RUN_INFO_FILE = "runProperties.tsv";
    public static final String ERRORS_FILE = "validationErrors.tsv";
    public static final String RUN_DATA_FILE = "runData.tsv";

    private Map<String, String> _formFields = new HashMap<String, String>();

    public File createValidationRunInfo(AssayRunUploadContext context, ExpRun run, File scriptDir) throws Exception
    {
        File runProps = new File(scriptDir, VALIDATION_RUN_INFO_FILE);
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runProps)));

        try {
            Map<PropertyDescriptor, String> runProperties = context.getRunProperties();
            runProperties.putAll(context.getUploadSetProperties());
            runProperties.putAll(context.getRunProperties());

            // serialize the run properties to a tsv
            for (Map.Entry<PropertyDescriptor, String> entry : runProperties.entrySet())
            {
                pw.append(entry.getKey().getName());
                pw.append('\t');
                pw.append(entry.getValue());
                pw.append('\t');
                pw.println(entry.getKey().getPropertyType().getJavaType().getName());

                _formFields.put(entry.getKey().getName(), entry.getValue());
            }

            // additional context properties
            for (Map.Entry<String, String> entry : getContextProperties(context).entrySet())
            {
                pw.append(entry.getKey());
                pw.append('\t');
                pw.append(entry.getValue());
                pw.append('\t');
                pw.println(String.class.getName());
            }

            // add the run data entries
            for (ExpData expData : run.getDataOutputs())
            {
                ExperimentDataHandler handler = expData.findDataHandler();
                if (handler instanceof ValidationDataHandler)
                {
                    Map<String, Object>[] data = ((ValidationDataHandler)handler).loadFileData(context.getProvider().getRunDataColumns(context.getProtocol()), expData.getDataFile());
                    File runData = new File(scriptDir, RUN_DATA_FILE);
                    writeRunData(data, runData);

                    pw.append(Props.runDataFile.name());
                    pw.append('\t');
                    pw.println(runData.getAbsolutePath());
                }
            }
            // errors file location
            File errorFile = new File(scriptDir, ERRORS_FILE);
            pw.append(Props.errorsFile.name());
            pw.append('\t');
            pw.println(errorFile.getAbsolutePath());
            
            return runProps;
        }
        finally
        {
            pw.close();
        }
    }

    private Map<String, String> getContextProperties(AssayRunUploadContext context)
    {
        Map<String, String> map = new HashMap<String, String>();

        map.put(Props.assayId.name(), context.getName());
        map.put(Props.runComments.name(), context.getComments());
        map.put(Props.containerPath.name(), context.getContainer().getPath());
        map.put(Props.assayType.name(), context.getProvider().getName());
        map.put(Props.assayName.name(), context.getProtocol().getName());
        map.put(Props.userName.name(), context.getUser().getEmail());

        return map;
    }

    public void processValidationOutput(File runInfo) throws ValidationException
    {
        if (runInfo.exists())
        {
            List<ValidationError> errors = new ArrayList<ValidationError>();

            try {
                TabLoader loader = new TabLoader(runInfo, false);
                loader.setColumns(new ColumnDescriptor[]{
                        new ColumnDescriptor("name", String.class),
                        new ColumnDescriptor("value", String.class),
                        new ColumnDescriptor("type", String.class)
                });

                Map[] maps = (Map[])loader.load();
                File errorFile = null;
                for (Map row : maps)
                {
                    if (row.get("name").equals(Props.errorsFile.name()))
                    {
                        errorFile = new File(row.get("value").toString());
                        break;
                    }
                }

                if (errorFile != null && errorFile.exists())
                {
                    TabLoader errorLoader = new TabLoader(errorFile, false);
                    errorLoader.setColumns(new ColumnDescriptor[]{
                            new ColumnDescriptor("type", String.class),
                            new ColumnDescriptor("property", String.class),
                            new ColumnDescriptor("message", String.class)
                    });

                    for (Map row : (Map[])errorLoader.load())
                    {
                        if ("error".equalsIgnoreCase(row.get("type").toString()))
                        {
                            String propName = mapPropertyName(StringUtils.trimToNull((String)row.get("property")));

                            if (propName != null)
                                errors.add(new PropertyValidationError(row.get("message").toString(), propName));
                            else
                                errors.add(new SimpleValidationError(row.get("message").toString()));
                        }
                    }
                }
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);
        }
    }

    /**
     * Ensures the property name recorded maps to a valid form field name
     * @param name
     * @return
     */
    private String mapPropertyName(String name)
    {
        if (Props.assayId.name().equals(name))
            return "name";
        if (Props.runComments.name().equals(name))
            return "comments";
        if (_formFields.containsKey(name))
            return name;

        return null;
    }

    private void writeRunData(Map<String, Object>[] data, File runDataFile) throws Exception
    {
        if (data.length > 0)
        {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(runDataFile)));

            try {
                // write the column header
                List<String> columns = new ArrayList<String>(data[0].keySet());
                String sep = "";
                for (String name : columns)
                {
                    pw.append(sep);
                    pw.append(name);
                    sep = "\t";
                }
                pw.println();

                // write the rows
                for (Map<String, Object> row : data)
                {
                    sep = "";
                    for (String name : columns)
                    {
                        pw.append(sep);
                        pw.append(String.valueOf(row.get(name)));
                        sep = "\t";
                    }
                    pw.println();
                }
            }
            finally
            {
                pw.close();
            }
        }
    }
}