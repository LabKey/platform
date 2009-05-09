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
package org.labkey.study.writer;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.DerivativeType;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class DerivativeTypeWriter implements Writer<DerivativeType[]>
{
    public void write(DerivativeType[] types, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter pw = fs.getPrintWriter("derivatives.tsv");

        pw.println("# derivatives");
        pw.println("derivative_id\tldms_derivative_code\tlabware_derivative_code\tderivative");

        for (DerivativeType type : types)
        {
            pw.print(String.valueOf(type.getExternalId()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getLdmsDerivativeCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getLabwareDerivativeCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getDerivative()));
            pw.println();
        }

        pw.close();
    }
}