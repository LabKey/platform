/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.experiment.xar;

import org.labkey.api.data.Container;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.xar.Replacer;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: jeckels
 * Date: Jan 18, 2006
 */
public class AutoFileLSIDReplacer implements Replacer
{
    private final String _dataFileURL;
    private final Container _container;
    private final XarSource _source;

    public AutoFileLSIDReplacer(String dataFileURL, Container container, XarSource source)
    {
        _dataFileURL = dataFileURL;
        _container = container;
        _source = source;
    }

    public String getReplacement(String original) throws XarFormatException
    {
        if (original.equals("AutoFileLSID"))
        {
            if (_dataFileURL == null)
            {
                throw new XarFormatException("You must specify a dataFileURL when using AutoFileLSID");
            }

            String canonicalURL = _source.getCanonicalDataFileURL(_dataFileURL);

            ExpData data = ExperimentService.get().getExpDataByURL(canonicalURL, _container);
            if (data != null)
            {
                return data.getLSID();
            }
            else
            {
                return "urn:lsid:${LSIDAuthority}:${LSIDNamespace.Prefix}.Folder-${Container.RowId}-${XarFileId}:" + PageFlowUtil.encode(_dataFileURL);
            }
        }
        return null;
    }
}
