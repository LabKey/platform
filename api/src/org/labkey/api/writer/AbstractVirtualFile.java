package org.labkey.api.writer;

import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * User: jeckels
 * Date: Jan 19, 2012
 */
public abstract class AbstractVirtualFile implements VirtualFile
{
    @Override
    public void saveWebdavTree(WebdavResource resource) throws IOException
    {
        for (WebdavResource child : resource.list())
        {
            if (child.isCollection())
            {
                getDir(child.getName()).saveWebdavTree(child);
            }
            else
            {
                OutputStream outputStream = getOutputStream(child.getName());
                InputStream inputStream = null;
                try
                {
                    byte[] bytes = new byte[4096];
                    int length;
                    inputStream = child.getInputStream();
                    if (inputStream != null)
                    {
                        while ((length = inputStream.read(bytes)) != -1)
                        {
                            outputStream.write(bytes, 0, length);
                        }
                    }
                }
                finally
                {
                    try { outputStream.close(); } catch (IOException ignored) {}
                    if (inputStream != null) { try { inputStream.close(); } catch (IOException ignored) {} }
                }
            }
        }
    }
}
