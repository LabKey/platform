package org.labkey.api.microarray;

import java.io.File;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;


public interface FeatureExtractionClient {
    public boolean setProxyURL(String proxyURL);
    public void findWorkableSettings() throws ExtractionConfigException;
    public void testConnectivity() throws ExtractionException;
    public File run(File[] images) throws ExtractionException;
    public String getTaskId();
    public int saveProcessedRuns(User u, Container c, File outputDir);
}

