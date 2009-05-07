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

import org.labkey.api.data.Container;
import org.labkey.api.util.Archive;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.SampleManager;
import org.labkey.study.model.Study;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:28:37 AM
 */
public class SpecimenArchiveWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        Container c = ctx.getContainer();
        SampleManager manager = SampleManager.getInstance();
        Archive zip = fs.createZipArchive("sample.zip");

        SiteWriter siteWriter = new SiteWriter();
        siteWriter.write(study.getSites(), ctx, zip);

        new PrimaryTypeWriter().write(manager.getPrimaryTypes(c), ctx, zip);
        new AdditiveTypeWriter().write(manager.getAdditiveTypes(c), ctx, zip);
        new DerivativeTypeWriter().write(manager.getDerivativeTypes(c), ctx, zip);
        new SpecimenWriter().write(study, ctx, zip);

        zip.close();
    }
}
