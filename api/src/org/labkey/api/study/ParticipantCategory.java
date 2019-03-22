/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.study;

/**
 * User: klum
 * Date: Jul 11, 2012
 */
public interface ParticipantCategory
{
    String SEND_PARTICIPANT_GROUP_TYPE = "Study.SendParticipantGroup";

    public static int OWNER_SHARED = -1;

    public enum Type {
        manual,
        list,
        query,
        cohort,
    }
    
    String getLabel();
    String getType();
    String[] getGroupNames();
}
