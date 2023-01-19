/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.security;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.premium.AntiVirusService;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.reader.Readers;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.labkey.api.premium.AntiVirusService.Result.FAILED;
import static org.labkey.api.premium.AntiVirusService.Result.OK;

public class DummyAntiVirusService implements AntiVirusService
{
    final static Logger LOG = LogManager.getLogger(DummyAntiVirusService.class);

    @Override
    public ScanResult scan(@NotNull File f, @Nullable String originalName, ViewBackgroundInfo info)
    {
        originalName = defaultString(originalName, f.getName());

        try
        {
            long len = f.length();
            // editors really like to tack on new lines, so why fight it
            if (len >= TEST_VIRUS_CONTENT.length() && len <= TEST_VIRUS_CONTENT.length()+2)
            {
                String s = IOUtils.toString(Readers.getReader(f));
                if (StringUtils.equals(TEST_VIRUS_CONTENT, s.trim()))
                {
                    String logmessage = "File failed virus scan: LABKEY test virus detected";
                    warnAndAuditLog(LOG, logmessage, info, originalName);

                    return new ScanResult(originalName, FAILED, "LABKEY test virus detected in file: '" + originalName + "'");
                }
            }
        }
        catch (IOException x)
        {
            return new ScanResult(originalName, FAILED, x.getMessage());
        }
        return new ScanResult(originalName, OK, null);
    }


    public static class Provider implements PremiumService.AntiVirusProvider
    {
        @Override
        public @NotNull String getId()
        {
            return DummyAntiVirusService.class.getName();
        }

        @Override
        public @NotNull String getDescription()
        {
            return "For test-purposes only";
        }

        @Override
        public @Nullable ActionURL getConfigurationURL()
        {
            return null;
        }

        @Override
        public @NotNull AntiVirusService getService()
        {
            return new DummyAntiVirusService();
        }
    }
}
