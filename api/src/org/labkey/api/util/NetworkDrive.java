/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Utility to map Windows file shares as "drives" with their own letter. Shells out to do a NET USE to map it
 * if not already mounted.
 */
public class NetworkDrive
{
    protected String path;
    protected String user;
    protected String password;

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getUser()
    {
        return user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String mount(char driveChar) throws InterruptedException, IOException
    {
        unmount(driveChar);
        StringBuilder sb = new StringBuilder();
        sb.append("net use ");
        sb.append(driveChar);
        sb.append(": ");
        sb.append(getPath());
        sb.append(" ");
        if (getPassword() != null && !"".equals(getPassword().trim()))
        {
            sb.append(getPassword());
        }
        if (getUser() != null && !"".equals(getUser().trim()))
        {
            sb.append(" /USER:");
            sb.append(getUser());
        }
        String connCommand = sb.toString();
        Process p = Runtime.getRuntime().exec(connCommand);
        p.waitFor();

        if (p.exitValue() != 0)
        {
            int count;
            char buffer[] = new char[4096];

            InputStreamReader reader = new InputStreamReader(p.getErrorStream(), "US-ASCII");
            StringBuffer errors = new StringBuffer();
            while ((count = reader.read(buffer, 0, buffer.length - 1)) != -1)
                errors.append(buffer, 0, count);

            return "Failed to map network drive for " + path + ":\n" +
                    connCommand + "\n" +
                    errors;
        }
        return null;
    }

    public void unmount(char driveChar)
        throws IOException, InterruptedException
    {
        // Make sure OS isn't holding another path mapped to this drive.
        String disconnCommand = "net use " + driveChar + ": " + "/d";
        Process p = Runtime.getRuntime().exec(disconnCommand);
        p.waitFor();
    }

    private static Logger _log = Logger.getLogger(NetworkDrive.class);

    /**
     * @return whether the file exists, mounting the drive if needed
     */
    public static boolean exists(File f)
    {
        if (f == null)
            return false;
        if (f.exists())
            return true;
        ensureDrive(f.getPath());
        return f.exists();
    }

    /**
     * Force mounting of the drive if it's not already available.
     */
    public static void ensureDrive(String path)
    {
        if (path.length() != 1)
        {
            if (path.length() < 2 || path.charAt(1) != ':')
                return; // Not a path with a drive.
        }

        char driveChar = path.toLowerCase().charAt(0);

        File driveRoot = new File(driveChar + ":\\");
        if (driveRoot.exists())
            return; // Drive root already exists.

        try
        {
            NetworkDrive drive = getNetworkDrive(path);
            if (drive != null)
            {
                _log.info("Attempting to mount " + path.toUpperCase().charAt(0) + " drive at " + drive.getPath() + " with user " + drive.getUser());
                String error = drive.mount(driveChar);
                if (error != null)
                {
                    _log.error("Failed to map network drive for " + path + ": " + error);
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Exception trying to map network drive for " + path, e);
        }
    }

    public static NetworkDrive getNetworkDrive(String path)
    {
        if (path.length() != 1)
        {
            if (path.length() < 2 || path.charAt(1) != ':')
                return null; // Not a path with a drive.
        }

        char driveChar = path.toLowerCase().charAt(0);

        PipelineJobService.ApplicationProperties props = PipelineJobService.get().getAppProperties();
        if (props.getNetworkDriveLetter() != null && Character.toLowerCase(props.getNetworkDriveLetter().charValue()) == driveChar)
        {
            NetworkDrive drive = new NetworkDrive();
            drive.setPath(props.getNetworkDrivePath());
            drive.setUser(props.getNetworkDriveUser());
            drive.setPassword(props.getNetworkDrivePassword());
            return drive;
        }
        
        return null;
    }

    public static String getDrive(String path)
    {
        if (null == path || path.length() < 2 || path.charAt(1) != ':')
            return null; // Not a path with a drive.

        return path.toLowerCase().substring(0, 2);
    }
}
