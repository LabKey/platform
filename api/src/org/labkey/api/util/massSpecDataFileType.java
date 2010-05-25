/*
 * Copyright (c) 2010 LabKey Corporation
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
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
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
        super(".mzXML",gzSupportLevel.SUPPORT_GZ);
        addMzmlIfAvailable(); // shall we add mzml to the list?
    }

    /**
     * use this constructor for things like ".msprefix.mzxml",
     * still adds .mzML if the pwiz DLL is available
     */
   public massSpecDataFileType(List<String> suffixes, String defaultSuffix)
    {
        super(suffixes, defaultSuffix, false, gzSupportLevel.SUPPORT_GZ);
        addMzmlIfAvailable(); // shall we add mzml to the list?
    }

    // use this to investigate availability of DLL that
    // implements the pwiz interface - it's declared in 
    // labkey.xml as parameter "org.labkey.api.ms2.mzmlLibrary"
    static private boolean _triedPwizLoadLib;
    static private boolean _isPwizAvailable;
    public static boolean isMZmlAvailable()
    {
        if (!_triedPwizLoadLib)
        {
            _triedPwizLoadLib = true;
            String mzMLLibName;
            try
            {
                SpringModule mod = (SpringModule)ModuleLoader.getInstance().getCoreModule();
                mzMLLibName = mod.getInitParameter("org.labkey.api.ms2.mzmlLibrary");
            }
            catch (Exception e)
            {
                mzMLLibName = "";
            }
            if (null == mzMLLibName)
            {   // completely unconfigured (thought exception caught this)
                mzMLLibName = "";
            }
            if (""!=mzMLLibName)
            {
                String why="";
                try {
                    System.loadLibrary(mzMLLibName);
                    _isPwizAvailable = true;
                } catch (UnsatisfiedLinkError e) {
                    why = e.getMessage();
                } catch (Exception e) {
                    why = e.getMessage();
                }
                if (!_isPwizAvailable)
                {
                    String msg = "Could not load native library";
                    msg += mzMLLibName;
                    msg += "for mzML input support: ";
                    msg += why;
                    msg += " Please refer to https://www.labkey.org/wiki/home/Documentation/page.view?name=WorkingWithmzML for more information.";
                    Logger.getLogger(massSpecDataFileType.class).warn(msg);
                }
            }
        }
        return _isPwizAvailable;
    }

    public static boolean retryIsMZmlAvailable()
    {
        // useful for unit test
        _triedPwizLoadLib = false;
        return isMZmlAvailable();
    }

    private boolean addMzmlIfAvailable()
    {
        boolean result = false;
        if (isMZmlAvailable())
        {
            this.addSuffix(".mzML");
            result = true;
        }
        return result;
    }
}
