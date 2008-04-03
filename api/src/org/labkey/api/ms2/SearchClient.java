package org.labkey.api.ms2;

/**
 * User: jeckels
 * Date: Jan 9, 2007
 */
public interface SearchClient {
    public boolean setProxyURL(String proxyURL);
    public void findWorkableSettings(boolean useAuthentication);

    public int getErrorCode();
    public String getErrorString();
}
