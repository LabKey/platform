<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">

    /* This method is called when a flag field is clicked */
    function setFlag(flagId)
    {
        Ext.onReady(function(){

            var defaultComment = "Flagged for review";

            Ext.QuickTips.init();

            var allImages = document.images, w;
            for (var i = 0; i < allImages.length; i ++) {
                if (allImages[i].getAttribute("flagId") == flagId) {
                    if (allImages[i].title != "Flag for review") defaultComment = allImages[i].title || defaultComment;
                    w = allImages[i];
                }
            }

            var el = Ext.get(w);
            var box = Ext.MessageBox.show({
                title   : 'Review',
                prompt  : true,
                msg     : 'Enter a comment',
                value   : defaultComment,
                width   : 300,
                fn      : function(btnId, value) {
                    if (btnId == 'ok'){
                        Ext.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('experiment', 'setFlag.api'),
                            params : {
                                lsid    : flagId,
                                comment : value,
                                unique  : new Date().getTime()
                            },
                            success : function() {
                                if (value.length) {
                                    w.src = LABKEY.contextPath + "/Experiment/flagDefault.gif";
                                    w.title = value;
                                }
                                else {
                                    w.src = LABKEY.contextPath + "/Experiment/unflagDefault.gif";
                                    w.title = "Flag for review";
                                }
                            },
                            failure : function() { alert("Failure!"); }
                        });
                    }
                },
                buttons : Ext.MessageBox.OKCANCEL
            });

            box.getDialog().setPosition(el.getAnchorXY()[0]-75, el.getAnchorXY()[1]-75);
        });

        return false;
    }
</script>