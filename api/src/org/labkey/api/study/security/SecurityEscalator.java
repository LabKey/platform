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

import com.google.common.base.Joiner;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * A class to escalate/bypass the security checks made by {@link org.labkey.api.query.QueryUpdateService}s.  See
 * {@link StudySecurityEscalator} as a reference implementation.  It also audits that escalation in the LabKey
 * Audit Log.  You need to always {@link #close()} the {@link SecurityEscalator}, so you should use a pattern
 * like the following:
 *
 * {@code
 *      try (SecurityEscalator escalator = new SecurityEscalator()) {
 *          // Do some stuff....
 *      }
 * }
 *
 * <p>
 *     The purpose of this is grant the application/user access to make changes to tables (or retrieve data
 *     from those tables) that they should't have permission for.  For instance, a service could use this to
 *     allow a supervisor to edit their employee's records, without giving them access to edit any other rows
 *     in the table, or even see any other rows.  <strong>In other words, you move the security to the application,
 *     instead of the database.</strong>
 * </p>
 * <p>
 *     The calling code is therefore responsible for checking permissions on that transaction to
 *     make sure that the user should be able to perform the action, and that the transaction doesn't try to
 *     make other changes than what is expected (that means you should be validating your inputs).  Do NOT
 *     use this to grant public access to protected records, and do NOT ever allow client code (e.g. JavaScript)
 *     to pass in a variable that will invoke this to bypass checks (such as in extraContext, etc)!
 * </p>
 * <p>
 *     See the SecurityEscalatorService in the DBUtils module and the BehaviorDataEntryService in the WNPRC_EHR
 *     module for a good reference example of how to use this class.  It is recommended that you wrap the escalation
 *     in a transaction, to make sure any errors don't prevent the Audit Log from being updated, which happens when
 *     you {@link #close()} the escalator.
 * </p>
 *
 * @see "SecurityEscalatorService" in DBUtils Module
 * @see StudySecurityEscalator
 */
public abstract class SecurityEscalator implements AutoCloseable
{
    /**
     * Returns a {@link ThreadLocal < Integer >} that represents the escalation level.  This should be static
     * to a class.
     *
     * @return {@link ThreadLocal < Integer >} that represents the escalation level.
     */
    abstract protected ThreadLocal<Integer> getEscalationLevelTracker();

    /**
     * Returns a blank, new {@link SecurityEscalationAuditProvider.SecurityEscalationEvent} that will
     * be filled with data about the escalation.  You should only add values to the event that are specific
     * to your SecurityEscalator implementation, as this class will override all of the values tracked by
     * the base class.
     *
     * @return A blank, new {@link SecurityEscalationAuditProvider.SecurityEscalationEvent}.
     */
    abstract protected SecurityEscalationAuditProvider.SecurityEscalationEvent getNewSecurityEvent();

    private SecurityEscalationAuditProvider.SecurityEscalationEvent _event;
    private User user;

    /**
     * Constructor to return a new escalator object.  This increments the escalation level, and prepares
     * an Audit Message (but doesn't add it just yet).
     *
     * @param user
     * @param container
     * @param comment
     */
    public SecurityEscalator(User user, Container container, String comment) {
        this.user = user;

        // Get our caller
        List<StackTraceElement> stackTraceElements = Arrays.asList(Thread.currentThread().getStackTrace());
        String thisFileName = stackTraceElements.get(0).getFileName();

        String serviceName = null;
        List<String> relevantStackTraceElements = new ArrayList<>();
        boolean active = false;
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            // Don't start joining until the first level that's not either the stack trace generator or this
            if (!active
                    && !stackTraceElement.getClassName().equals("java.lang.Thread")
                    && !stackTraceElement.getFileName().equals("SecurityEscalator.java")
                    && !Modifier.isAbstract(stackTraceElement.getClass().getModifiers())
                    ) {

                active = true;

                serviceName = stackTraceElement.getClassName();
            }

            // We can cut out everything below this, because that's just boring Servlet/Tomcat traces, which
            // doesn't matter for our auditing.
            if (stackTraceElement.getClassName().equals("javax.servlet.http.HttpServlet")) {
                break;
            }


            if (active) {
                relevantStackTraceElements.add(stackTraceElement.getClassName() + ":" + stackTraceElement.getMethodName() + "+" + stackTraceElement.getLineNumber());
            }
        }
        String relevantStackTrace = Joiner.on("\n").join(relevantStackTraceElements);

        // Create an audit entry, but don't submit it yet.
        _event = this.getNewSecurityEvent();
        _event.setContainer(container.getId());
        _event.setStartTime(new Date());
        _event.setServiceName(serviceName);
        _event.setStackTrace(relevantStackTrace);
        _event.setComment(comment);
        _event.setLevel(getEscalationLevel());
        _event.setEscalatingUser(user.getUserId());

        // Increase Escalation Level
        getEscalationLevelTracker().set(getEscalationLevel() + 1);
    }

    /**
     * Lowers the escalation level by one, and submits an Audit Log message.  Called automatically if the
     * constructor is used in a try-with-resources block.
     */
    @Override
    public void close() {
        // Set end time and submit the log message.
        _event.setEndTime(new Date());
        AuditLogService.get().addEvent(user, _event);

        // Decrement the escalation level.
        if (getEscalationLevel() == 1) {
            getEscalationLevelTracker().remove();
        }
        else {
            getEscalationLevelTracker().set(getEscalationLevel() - 1);
        }
    }

    private int getEscalationLevel() {
        Integer escalationLevel = getEscalationLevelTracker().get();

        return (escalationLevel == null) ? 0 : escalationLevel.intValue();
    }
}
