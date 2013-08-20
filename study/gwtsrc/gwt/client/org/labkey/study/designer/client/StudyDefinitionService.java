/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.rpc.RemoteService;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDesignVersion;

/**
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 8:46:26 PM
 */
public interface StudyDefinitionService extends RemoteService
{
    public GWTStudyDesignVersion save(GWTStudyDefinition def);
    public GWTStudyDefinition getBlank();
    public GWTStudyDefinition getRevision(int studyId, int revision);
    public GWTStudyDesignVersion[] getVersions(int studyId);
    public GWTStudyDefinition getTemplate();
    public GWTStudyDefinition ensureDatasetPlaceholders(GWTStudyDefinition def);
    public GWTStudyDefinition createTimepoints(GWTStudyDefinition def);
    public GWTStudyDefinition createCohorts(GWTStudyDefinition def);
    public Boolean hasNewCohorts(GWTStudyDefinition def);
}
