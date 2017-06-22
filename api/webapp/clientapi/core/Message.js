/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2017 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * @namespace <font color="black">LabKey Email Notification Helper class.
 * This class provides static methods to generate an email notification message that gets sent from the
 * LabKey SMTP server.</font>
 *            <p>Additional documentation on SMTP setup for LabKey Server:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=configWindows">Install LabKey via the Installer</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=cpasxml">Modify the Configuration File -- Includes SMTP Settings</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Message = new function()
{
    /*-- public methods --*/
    /** @scope LABKEY.Message */
    return {
        /**
         * A map of the email recipient types. The values in
         * this map are as follows:
         * <ul>
         * <li>to</li>
         * <li>cc</li>
         * <li>bcc</li>
         * </ul>
         * For example, to refer to the cc type, the syntax would be:<br/>
         * <pre><code>LABKEY.Message.recipientType.cc</code></pre>
         */
        recipientType : {
            to : 'TO',
            cc : 'CC',
            bcc : 'BCC'
        },
        /**
         * A map of the email message body types. Email messages can contain multiple content types allowing a
         * client application the option to display the content it is best suited to handle. A common practice is to
         * include both plain and html body types to allow applications which cannot render html content to display
         * a plain text version. The values in
         * this map are as follows:
         * <ul>
         * <li>plain</li>
         * <li>html</li>
         * </ul>
         * For example, to refer to the html type, the syntax would be:<br/>
         * <pre><code>LABKEY.Message.msgType.html</code></pre>
         */
        msgType : {
            plain : 'text/plain',
            html : 'text/html'
        },

        /**
         * Sends an email notification message through the LabKey Server. Message recipients and the sender
         * must exist as valid accounts, or the current user account must have permission to send to addresses
         * not associated with a LabKey Server account at the site-level, or an error will be thrown.
         * @param config A configuration object containing the following properties
         * @param {String} config.msgFrom The email address that appears on the email from line.
         * @param {String} [config.msgSubject] The value that appears on the email subject line.
         * @param {Object[]} config.msgRecipients An array of recipient objects which have the following properties:
         *  <ul>
         *      <li>type: the recipient type, must be one of the values from: LABKEY.Message.recipientType.</li>
         *      <li>address: the email address of the recipient.</li>
         *  </ul>
         * The utility function LABKEY.Message.createRecipient can be used to help create these objects.
         * @param {Object[]} config.msgContent An array of content objects which have the following properties:
         *  <ul>
         *      <li>type: the message content type, must be one of the values from: LABKEY.Message.msgType.</li>
         *      <li>content: the email message body for this content type.</li>
         *  </ul>
         * The utility function LABKEY.Message.createMsgContent can be used to help create these objects.
         * @param {function} [config.success] A reference to a function to call if the action succeeds. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>result:</b> an object containing a boolean property: success..
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request.
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         * @example Example:
         <pre name="code" class="xml">
         &lt;script type="text/javascript"&gt;
         
         function errorHandler(errorInfo, responseObj)
         {
             LABKEY.Utils.displayAjaxErrorResponse(responseObj, errorInfo);
         }

         function onSuccess(result)
         {
             alert('Message sent successfully.');
         }

         LABKEY.Message.sendMessage({
             msgFrom: 'admin@test.com',
             msgSubject: 'Testing email API...',
             msgRecipients: [
                 LABKEY.Message.createRecipient(LABKEY.Message.recipientType.to, 'user1@test.com'),
                 LABKEY.Message.createRecipient(LABKEY.Message.recipientType.cc, 'user2@test.com'),
                 LABKEY.Message.createRecipient(LABKEY.Message.recipientType.cc, 'user3@test.com'),
                 LABKEY.Message.createRecipient(LABKEY.Message.recipientType.bcc, 'user4@test.com')
             ],
             msgContent: [
                 LABKEY.Message.createMsgContent(LABKEY.Message.msgType.html, '&lt;h2&gt;This is a test message&lt;/h2&gt;'),
                 LABKEY.Message.createMsgContent(LABKEY.Message.msgType.plain, 'This is a test message')
             ],
             success: onSuccess,
             failure: errorHandler,
         });
         &lt;/script&gt;
         </pre>
         */
        sendMessage : function(config)
        {
            var dataObject = {};

            if (config.msgFrom != undefined)
                dataObject.msgFrom = config.msgFrom;
            if (config.msgRecipients != undefined)
                dataObject.msgRecipients = config.msgRecipients;
            if (config.msgContent != undefined)
                dataObject.msgContent = config.msgContent;
            if (config.msgSubject != undefined)
                dataObject.msgSubject = config.msgSubject;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("announcements", "sendMessage"),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : dataObject,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },
        /**
         * A utility function to create a message content object used in LABKEY.Message.sendMessage.
         * @param {LABKEY.Message.msgType} type The content type of this message part.
         * @param {String} content The message part content.
         */
        createMsgContent : function(type, content)
        {
            return {
                type : type,
                content : content
            };
        },
        /**
         * A utility function to create a recipient object used in LABKEY.Message.sendMessage.
         * @param {LABKEY.Message.recipientType} type Determines where the recipient email address will appear in the message.
         * @param {String} email The email address of the recipient.
         */
        createRecipient : function(type, email)
        {
            return {
                type : type,
                address : email
            }
        },

        /**
         * A utility function to create a recipient object (based on a user ID or group ID) used in LABKEY.Message.sendMessage.
         * Note: only server side validation or transformation scripts can specify a user or group ID.
         * @param {LABKEY.Message.recipientType} type Determines where the recipient email address will appear in the message.
         * @param {Integer} id The user or group id of the recipient.
         */
        createPrincipalIdRecipient : function(type, id)
        {
            return {
                type : type,
                principalId : id
            }
        }
    };
};
