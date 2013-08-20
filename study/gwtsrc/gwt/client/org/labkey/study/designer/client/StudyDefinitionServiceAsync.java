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

import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import com.google.gwt.user.client.rpc.AsyncCallback;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDesignVersion;

/**
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 8:47:24 PM
 */
public interface StudyDefinitionServiceAsync
{

    void save(GWTStudyDefinition def, AsyncCallback<GWTStudyDesignVersion> async);

    void getBlank(AsyncCallback<GWTStudyDefinition> async);

    void getRevision(int studyId, int revision, AsyncCallback<GWTStudyDefinition> async);

    void getVersions(int studyId, AsyncCallback<GWTStudyDesignVersion[]> async);

    void getTemplate(AsyncCallback<GWTStudyDefinition> async);

    void ensureDatasetPlaceholders(GWTStudyDefinition def, AsyncCallback<GWTStudyDefinition> callback);

    void createTimepoints(GWTStudyDefinition def, AsyncCallback<GWTStudyDefinition> callback);

    void createCohorts(GWTStudyDefinition def, AsyncCallback<GWTStudyDefinition> callback);

    void hasNewCohorts(GWTStudyDefinition def, AsyncCallback<Boolean> callback);

}
