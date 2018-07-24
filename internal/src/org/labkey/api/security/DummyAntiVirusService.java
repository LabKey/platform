package org.labkey.api.security;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.premium.AntiVirusService;
import org.labkey.api.reader.Readers;

import java.io.File;
import java.io.IOException;

import static org.labkey.api.premium.AntiVirusService.Result.FAILED;
import static org.labkey.api.premium.AntiVirusService.Result.OK;

public class DummyAntiVirusService implements AntiVirusService
{
    @Override
    public ScanResult scan(File f)
    {
        try
        {
            long len = f.length();
            // editors really like to tack on new lines, so why fight it
            if (len >= TEST_VIRUS_CONTENT.length() && len <= TEST_VIRUS_CONTENT.length()+2)
            {
                String s = IOUtils.toString(Readers.getReader(f));
                if (StringUtils.equals(TEST_VIRUS_CONTENT, s.trim()))
                    return new ScanResult(FAILED, "virus found");
            }
        }
        catch (IOException x)
        {
            return new ScanResult(FAILED, x.getMessage());
        }
        return new ScanResult(OK, null);
    }
}
