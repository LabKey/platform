/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * mass spec data FileType  class
 * for .mzxml, .mzxml.gz, .mzml, etc
 * <p/>
 * Created: Jan 6, 2010
 *
 * @author bpratt
 */
public class massSpecDataFileType extends FileType
{
    /**
     * the normal constructor, gets you mzXML, mzXML.gz,
     * and mzML if the pwiz DLL is available
     */
    public massSpecDataFileType()
    {
        super(".mzXML",FileType.systemPreferenceGZ());
        try_mzml(); // shall we add mzml to the list?
    }

    /**
     * use this constructor for things like ".msprefix.mzxml",
     * still adds .mzML if the pwiz DLL is available
     */
   public massSpecDataFileType(List<String> suffixes, String defaultSuffix)
    {
        super(suffixes, defaultSuffix, false, FileType.systemPreferenceGZ());
        try_mzml(); // shall we add mzml to the list?
    }

    // use this to investigate availability of DLL that
    // implements the pwiz interface
    static private boolean _triedPwizLoadLib;
    static private boolean _isPwizAvailable;
    public static boolean isMZmlAvailable()
             throws IOException
    {
        if (!_triedPwizLoadLib)
        {
            _triedPwizLoadLib = true;
            String msg = "pwiz_swigbindings lib not found, no mzML support (this is not necessarily an error)";
            try {
                System.loadLibrary("pwiz_swigbindings");
                _isPwizAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                throw new IOException (msg + e);
            } catch (Exception e) {
                throw new IOException (msg + e);
            }
        }
        return _isPwizAvailable;
    }

    private boolean try_mzml()
    {
        boolean result = false;
        try {
            if (isMZmlAvailable())
            {
                this.addSuffix(".mzML");
                result = true;
            }
        } catch (IOException x) {
            Logger.getLogger(massSpecDataFileType.class).info(x);
        }
        return result;
    }
}
