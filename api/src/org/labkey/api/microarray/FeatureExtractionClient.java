/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.api.microarray;

import java.io.File;
import java.util.List;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;


public interface FeatureExtractionClient {
    public boolean setProxyURL(String proxyURL);
    public void findWorkableSettings() throws ExtractionConfigException;
    public void testConnectivity() throws ExtractionException;
    public File run(List<File> images) throws ExtractionException;
    public String getTaskId();
    public int saveProcessedRuns(User u, Container c, File outputDir);
}

