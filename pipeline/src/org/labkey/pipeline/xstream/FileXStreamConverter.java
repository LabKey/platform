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
package org.labkey.pipeline.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.SingleValueConverter;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.file.PathMapper;

/**
 * FileXStreamConverter class
 * File marshalling handler for XML.<p/>
 * Created: Oct 4, 2007
 *
 * @author bmaclean
 */
public class FileXStreamConverter implements SingleValueConverter
{
    public boolean canConvert(Class type)
    {
        return type.equals(File.class);
    }

    /**
     * Load local file for UMO from URI file spec on LabKey Server.
     * @param str remote URI string
     * @return A file object converted to the local file system
     */
    public Object fromString(String str)
    {
        try
        {
            return new File(new URI(getPathMapper().remoteToLocal(str)));
        }
        catch (URISyntaxException e)
        {
            throw new ConversionException(e);
        }
    }

    /**
     * Marshal file from UMO on local file system to URI file spec on LabKey Server.
     * @param obj local file
     * @return A remote URI string
     */
    public String toString(Object obj)
    {
        return getPathMapper().localToRemote(((File) obj).toURI().toString());
    }

    private PathMapper getPathMapper()
    {
        return PipelineJobService.get().getPathMapper();
    }
}
