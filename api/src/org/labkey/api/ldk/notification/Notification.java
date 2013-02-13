/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.ldk.notification;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 7/14/12
 * Time: 2:53 PM
 */
public interface Notification
{
    /**
     * @return The display name used to identify this notification
     */
    public String getName();

    /**
     * @return An arbitrary string used to group notifications in the management UI
     */
    public String getCategory();

    /**
     * A string representing the schedule of this notification, similar to cron, that will be passed to
     * Quartz for scheduling.  See Quartz docs for more detail.  Note: if NULL is returned, this notification
     * will not be scheduled, but could be run on demand.
     */
    public String getCronString();

    /**
     * @return The string describing the scheduled intervals.  This is solely used for display purposes, because it is difficult to translate the a list of ScheduledFuture object into english.
     * This string should make sense in the context of the text: 'Schedule : ' + {description here}
     */
    public String getScheduleDescription();

    /**
     * @return The body of the email message to be sent.  If null is returned, no email will be sent.
     */
    public String getMessage(Container c, User u);

    /**
     * @return The string description of this notification that appears in the UI
     */
    public String getDescription();

    /**
     * @return The email subject line
     */
    public String getEmailSubject();

    /**
     * @return True if this notification is allowable in the passed container.
     * Typically this would test whether the owning module is active; however, this could
     * also include other others such as the presence of a study or other resource
     */
    public boolean isAvailable(Container c);
}
