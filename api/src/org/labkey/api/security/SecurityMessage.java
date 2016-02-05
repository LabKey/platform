/*
 * Copyright (c) 2005-2016 LabKey Corporation
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

package org.labkey.api.security;

import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.data.Container;

import javax.mail.Address;
import javax.mail.internet.MimeMessage;

public class SecurityMessage
{
    private String _verificationURL;
    private User _originatingUser;
    private String _to;
    private String _type;
    private String _messagePrefix;
    private SecurityManager.SecurityEmailTemplate _template;

    public SecurityMessage(){}

    public void setEmailTemplate(SecurityManager.SecurityEmailTemplate template)
    {
        _template = template;
    }

    public MimeMessage createMailMessage(Container c) throws Exception
    {
        if (_template != null)
        {
            MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

            _template.setVerificationUrl(getVerificationURL());
            _template.setOriginatingUser(getOriginatingUser());
            _template.setRecipient(getTo());
            _template.setOptionPrefix(getMessagePrefix());

            m.setTemplate(_template, c);
            LookAndFeelProperties properties = LookAndFeelProperties.getInstance(c);
            m.addFrom(new Address[]{_template.renderFrom(c, properties.getSystemEmailAddress())});

            return m;
        }
        throw new IllegalStateException("An email template has not been set for this message yet.");
    }

    public EmailTemplate getEmailTemplate(){return _template;}

    public void setVerificationURL(String url)
    {
        _verificationURL = url;
    }
    public String getVerificationURL()
    {
        return _verificationURL;
    }
    public User getOriginatingUser()
    {
        return _originatingUser;
    }
    public void setOriginatingUser(User originatingUser)
    {
        _originatingUser = originatingUser;
    }

    public String getType()
    {
        return _type;
    }
    public void setType(String type)
    {
        _type = type;
    }

    public String getMessagePrefix()
    {
        return _messagePrefix;
    }

    public void setMessagePrefix(String messagePrefix)
    {
        _messagePrefix = messagePrefix;
    }

    public void setTo(String to){_to = to;}
    public String getTo(){return _to;}
}
