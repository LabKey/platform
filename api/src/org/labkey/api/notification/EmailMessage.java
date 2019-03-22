/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.notification;

import org.labkey.api.util.MimeMap.MimeType;
import org.labkey.api.view.HttpView;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.List;

/**
 * Utility implementation for building up outgoing emails
 * User: klum
 * Date: Apr 21, 2010
 */
public interface EmailMessage
{
    String getFrom();
    List<String> getTo();
    String getSubject();

    // TODO: Unused... delete?
    void setHeader(String name, String value);
    // TODO: Only used by tests... delete?
    void setFiles(List<File> files);
    void addContent(MimeType type, String content);
    void addContent(MimeType type, HttpServletRequest request, HttpView view) throws Exception;

    /**
     * Sets the display name for the email sender, the actual sender email address will be the one configured via site or project settings
     */
    void setSenderName(String senderName);

    MimeMessage createMessage() throws MessagingException;
}
