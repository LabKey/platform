/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.apache.commons.codec.binary.Hex;
import org.labkey.api.data.Container;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.xar.Replacer;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * User: jeckels
 * Date: Jan 18, 2006
 */
public class AutoFileLSIDReplacer implements Replacer
{
    private static final String AUTO_FILE_LSID_NAME = "AutoFileLSID";

    public static final String AUTO_FILE_LSID_SUBSTITUTION = XarContext.createSubstitution(AUTO_FILE_LSID_NAME);

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
        if (original.equals(AUTO_FILE_LSID_NAME))
        {
            if (_dataFileURL == null)
            {
                throw new XarFormatException("You must specify a dataFileURL when using AutoFileLSID");
            }

            String canonicalURL = !FileUtil.hasCloudScheme(_dataFileURL) ? _source.getCanonicalDataFileURL(_dataFileURL) : _dataFileURL;

            ExpData data = ExperimentService.get().getExpDataByURL(canonicalURL, _container);
            if (data != null)
            {
                return data.getLSID();
            }
            else
            {
                String objectId = _dataFileURL;
                // Bug 13692 - LSIDs are limited to 300 characters total, so don't let the path part get too long
                // If it's long enough to potentially cause a problem, use a hash of the relative path instead
                // It's not human readable, but all that we're really required to do is generate a unique LSID
                // for this path
                if (objectId.length() > 150)
                {
                    try
                    {
                        MessageDigest digest = MessageDigest.getInstance("SHA-1");
                        digest.update(objectId.getBytes());
                        objectId = "HASH-" + Hex.encodeHexString(digest.digest());
                    }
                    catch (NoSuchAlgorithmException ignored)
                    {
                        // Fall back and use the original string
                    }
                }
                return "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":${LSIDNamespace.Prefix}.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "-${XarFileId}:" + PageFlowUtil.encode(objectId);
            }
        }
        return null;
    }
}
