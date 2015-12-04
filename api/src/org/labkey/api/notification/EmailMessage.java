/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.List;

/**
 * User: klum
 * Date: Apr 21, 2010
 * Time: 10:57:29 AM
 */
public interface EmailMessage
{
    public enum contentType
    {
        PLAIN("text/plain"), HTML("text/html");

        private String _mimeType;

        private contentType(String mimeType)
        {
            _mimeType = mimeType;
        }

        public String getMimeType()
        {
            return _mimeType;
        }
    }
    
    String getFrom();
    String[] getTo();
    String getSubject();

    void setHeader(String name, String value);
    void setFiles(List<File> files);
    void addContent(String content);
    void addContent(contentType type, String content);
    void addContent(contentType type, ViewContext context, HttpView view) throws Exception;
    void addContent(contentType type, HttpServletRequest request, HttpView view) throws Exception;
    void setSenderName(String senderName);

    MimeMessage createMessage() throws MessagingException;
}
