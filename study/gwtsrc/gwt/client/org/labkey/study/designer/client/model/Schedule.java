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

package gwt.client.org.labkey.study.designer.client.model;

import java.util.List;

/**
 * User: Mark Igra
 * Date: Dec 20, 2006
 * Time: 4:26:30 PM
 */
public interface Schedule /*extends XMLSavable*/
{
    void removeTimepoint(GWTTimepoint tp);

    List<GWTTimepoint> getTimepoints();

    GWTTimepoint getTimepoint(int i);

    void addTimepoint(GWTTimepoint tp);

    void addTimepoint(int index, GWTTimepoint tp);

    void removeTimepoint(int index);

}
