/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.study.security;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * A class to bypass the security checks made by study datasets.  To use, call
 * {@link StudySecurityEscalator#beginEscalation(User, Container, String)} in a resource try block:
 *
 * <code>
 *      try (StudySecurityEscalator escalator = StudySecurityEscalator.beginEscalation()) {
 *          // insert/update rows here...
 *      }
 * </code>
 *
 * @see SecurityEscalator for more information about use cases.
 */
public class StudySecurityEscalator extends SecurityEscalator {
    // The ThreadLocal means each thread will have their own copy of this variable, and so it is essentially a "thread-wide"
    // global.
    private static final ThreadLocal<Integer> escalationStackLevel = new ThreadLocal<>();

    /**
     * Creates a new {@link StudySecurityEscalator}.  This is private to force the use of the static method {@link #beginEscalation(User, Container, String)},
     * which is a little more readable in client code.
     *
     * @see SecurityEscalator#SecurityEscalator(User, Container, String)
     */
    private StudySecurityEscalator(User user, Container container, String comment) {
        super(user, container, comment);
    }

    /**
     * Provides the escalation level tracker to the parent Security Escalator.
     *
     * @return A {@link ThreadLocal < Integer >} to track the escalation level.
     */
    @Override
    protected ThreadLocal<Integer> getEscalationLevelTracker() {
        return escalationStackLevel;
    }

    /**
     * Returns a new {@link SecurityEscalationAuditProvider.SecurityEscalationEvent} that will be filled out and
     * submitted by {@link SecurityEscalator}.
     *
     * @return A blank new {@link SecurityEscalationAuditProvider.SecurityEscalationEvent}.
     */
    @Override
    protected SecurityEscalationAuditProvider.SecurityEscalationEvent getNewSecurityEvent() {
        return new StudySecurityEscalationAuditProvider.StudySecurityEscalationEvent();
    }

    /**
     * Used in a try-with-resources block, this escalates calls to QueryUpdateService for Study tables within the
     * block.
     *
     * @param user The user being escalated.  This is used for auditing purposes.
     * @param container The container in which the user is being escalated.  This is for auditing purposes, but note
     *                  that the user will be escalated across <strong>ALL</strong> containers, not just the one
     *                  specified here.
     * @param comment A useful comment explaining why the user needed to be escalated, or rather, what the code is doing
     *                during the escalation.  For example: "Updating
     * @return
     */
    public static SecurityEscalator beginEscalation(User user, Container container, String comment) {
        SecurityEscalator securityEscalator = new StudySecurityEscalator(user, container, comment);

        return securityEscalator;
    }

    /**
     * Checks if the current thread is currently escalated.
     *
     * @return True if the current thread is escalated and so security checks should be ignored.
     * @see "DatasetUpdateService#hasPermission()"
     */
    public static boolean isEscalated() {
        Integer escalationLevel = escalationStackLevel.get();

        escalationLevel = (escalationLevel == null) ? 0 : escalationLevel.intValue();

        return escalationLevel > 0;
    }
}
