/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.study.specimen.settings;

import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.data.Container;
import org.labkey.api.settings.LookAndFeelProperties;

import javax.mail.Address;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;

/*
 * User: brittp
 * Date: May 8, 2009
 * Time: 2:52:06 PM
 */

public class RequestNotificationSettings
{
    public static final String REPLY_TO_CURRENT_USER_VALUE = "[current user]";
    private static final String KEY_REPLYTO = "ReplyTo";
    private static final String KEY_CC = "CC";
    private static final String KEY_SUBJECTSUFFIX = "SubjectSuffix";
    private static final String KEY_NEWREQUESTNOTIFY = "NewRequestNotify";
    private static final String KEY_DEFAULTEMAILNOTIFY = "DefaultEmailNotify";
    private static final String KEY_SPECIMENSATTACHMENT = "SpecimensAttachment";
    private String _replyTo;
    private String _cc;
    private String _subjectSuffix;
    private String _newRequestNotify;
    private boolean _ccCheckbox;
    private boolean _newRequestNotifyCheckbox;
    private DefaultEmailNotifyEnum _defaultEmailNotify = DefaultEmailNotifyEnum.ActorsInvolved;
    private SpecimensAttachmentEnum _specimensAttachment = SpecimensAttachmentEnum.InEmailBody;

    public enum DefaultEmailNotifyEnum {All, None, ActorsInvolved}
    public enum SpecimensAttachmentEnum {InEmailBody, ExcelAttachment, TextAttachment, Never}

    public RequestNotificationSettings()
    {
        // no-arg constructor for struts reflection
    }

    public RequestNotificationSettings(Map<String, String> map)
    {
        _replyTo = map.get(KEY_REPLYTO);
        _cc = map.get(KEY_CC);
        _subjectSuffix = map.get(KEY_SUBJECTSUFFIX);
        _newRequestNotify = map.get(KEY_NEWREQUESTNOTIFY);
        setDefaultEmailNotify(map.get(KEY_DEFAULTEMAILNOTIFY));
        setSpecimensAttachment(map.get(KEY_SPECIMENSATTACHMENT));
    }

    public String getReplyToEmailAddress(User currentAdmin)
    {
        if (RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(getReplyTo()))
            return currentAdmin.getEmail();
        else
            return getReplyTo();
    }

    public String getReplyTo()
    {
        return _replyTo;
    }

    public void setReplyTo(String replyTo)
    {
        _replyTo = replyTo;
    }

    public String getCc()
    {
        return _cc;
    }

    public void setCc(String cc)
    {
        _cc = cc;
    }

    public String getSubjectSuffix()
    {
        return _subjectSuffix;
    }

    public void setSubjectSuffix(String subjectSuffix)
    {
        _subjectSuffix = subjectSuffix;
    }

    public String getNewRequestNotify()
    {
        return _newRequestNotify;
    }

    public void setNewRequestNotify(String newRequestNotify)
    {
        _newRequestNotify = newRequestNotify;
    }

    public boolean isCcCheckbox()
    {
        return _ccCheckbox;
    }

    public void setCcCheckbox(boolean ccCheckbox)
    {
        _ccCheckbox = ccCheckbox;
    }

    public boolean isNewRequestNotifyCheckbox()
    {
        return _newRequestNotifyCheckbox;
    }

    public void setNewRequestNotifyCheckbox(boolean newRequestNotifyCheckbox)
    {
        _newRequestNotifyCheckbox = newRequestNotifyCheckbox;
    }

    public void populateMap(Map<String, String> map)
    {
        map.put(KEY_REPLYTO, _replyTo);
        map.put(KEY_CC, _cc);
        map.put(KEY_SUBJECTSUFFIX, _subjectSuffix);
        map.put(KEY_NEWREQUESTNOTIFY, _newRequestNotify);
        map.put(KEY_DEFAULTEMAILNOTIFY, getDefaultEmailNotify());
        map.put(KEY_SPECIMENSATTACHMENT, getSpecimensAttachment());
    }

    public static RequestNotificationSettings getDefaultSettings(Container c)
    {
        RequestNotificationSettings defaults = new RequestNotificationSettings();
        defaults.setReplyTo(LookAndFeelProperties.getInstance(c).getSystemEmailAddress());
        defaults.setCc("");
        defaults.setNewRequestNotify("");
        defaults.setSubjectSuffix("Specimen Request Notification");
        return defaults;
    }

    public Address[] getCCAddresses() throws ValidEmail.InvalidEmailException
    {
        if (_cc == null || _cc.length() == 0)
            return null;
        StringTokenizer splitter = new StringTokenizer(_cc, ",;:\t\n\r");
        List<Address> addresses = new ArrayList<>();
        while (splitter.hasMoreTokens())
        {
            String token = splitter.nextToken();
            ValidEmail tester = new ValidEmail(token);
            addresses.add(tester.getAddress());
        }
        return addresses.toArray(new Address[addresses.size()]);
    }

    public Address[] getNewRequestNotifyAddresses() throws ValidEmail.InvalidEmailException
    {
        if (_newRequestNotify == null || _newRequestNotify.length() == 0)
            return null;
        StringTokenizer splitter = new StringTokenizer(_newRequestNotify, ",;:\t\n\r");
        List<Address> addresses = new ArrayList<>();
        while (splitter.hasMoreTokens())
        {
            String token = splitter.nextToken();
            ValidEmail tester = new ValidEmail(token);
            addresses.add(tester.getAddress());
        }
        return addresses.toArray(new Address[addresses.size()]);
    }

    public boolean isReplyToCurrentUser()
    {
        return RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(getReplyTo());
    }

    public String getDefaultEmailNotify()
    {
        return _defaultEmailNotify.name();
    }

    public DefaultEmailNotifyEnum getDefaultEmailNotifyEnum()
    {
        return _defaultEmailNotify;
    }

    public void setDefaultEmailNotify(String defaultEmailNotify)
    {
        if (null == defaultEmailNotify)
            _defaultEmailNotify = DefaultEmailNotifyEnum.ActorsInvolved;
        else
            _defaultEmailNotify = DefaultEmailNotifyEnum.valueOf(defaultEmailNotify);
    }

    public String getSpecimensAttachment()
    {
        return _specimensAttachment.name();
    }

    public SpecimensAttachmentEnum getSpecimensAttachmentEnum()
    {
        return _specimensAttachment;
    }

    public void setSpecimensAttachment(String specimensAttachment)
    {
        if (null == specimensAttachment)
            _specimensAttachment = SpecimensAttachmentEnum.InEmailBody;
        else
            _specimensAttachment = SpecimensAttachmentEnum.valueOf(specimensAttachment);
    }
}