<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>

<%
    Container c =  HttpView.currentView().getViewContext().getContainer();
    Study s = StudyManager.getInstance().getStudy(c);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounColumnName = s.getSubjectColumnName();
%>



<div style="max-width: 1000px">
    <p>
        If a(n) <%= PageFlowUtil.filter(subjectNounSingular) %>  in your study has been loaded with an incorrect <%= PageFlowUtil.filter(subjectNounColumnName) %>,
        you can change the <%= PageFlowUtil.filter(subjectNounColumnName) %> of that <%= PageFlowUtil.filter(subjectNounSingular.toLowerCase()) %>. If you change the <%= PageFlowUtil.filter(subjectNounColumnName) %>
        to an existing <%= PageFlowUtil.filter(subjectNounColumnName) %>, data will be merged into a single <%= PageFlowUtil.filter(subjectNounSingular) %>.
    </p>
</div>
<div id="mergeParticipantsPanel"></div>
<div id="previewPanel"></div>


<script type="text/javascript">
    (function(){

        // All of the datasets that might need updating
        var datasets;

        var init = function()
        {
            Ext4.QuickTips.init();

            // TODO: Config these fields so they're on one line, or use a different pattern
            var oldIdField = Ext4.create('Ext.form.field.Text', {
                fieldLabel: 'Change <%= PageFlowUtil.filter(subjectNounColumnName) %>',
                labelSeparator: '',
                value:"",
                width : 310,
                labelWidth: 200,
                maxLength: 20,
                enforceMaxLength: true
            });

            var newIdField = Ext4.create('Ext.form.field.Text', {
                fieldLabel: 'to',
                labelSeparator: '',
                value:"",
                width : 220,
                labelWidth: 130,
                maxLength: 20,
                enforceMaxLength: true
            });

            // TODO: var Create Alias checkbox, enabled conditional on Alias dataset being defined

            var controls = [oldIdField, newIdField];

            var form = Ext4.create('Ext.form.FormPanel', {
                renderTo: 'mergeParticipantsPanel',
                bodyPadding: 10,
                bodyStyle: 'background: none',
                frame: false,
                border: false,
                width: 600,
                buttonAlign : 'left',
                items: controls,
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui : 'footer',
                    style : 'background: none',
                    height : 30,
                    items: [{
                        xtype: 'button',
                        text: 'Preview',
                        handler: function() {previewMerge(oldIdField.getValue(), newIdField.getValue());}
                    },{
                    // TODO: Merge should be disabled until preview is performed and conflicts dealt with
                        xtype: 'button',
                        text: 'Merge',
                        handler: function() {commitMerge(oldIdField.getValue(), newIdField.getValue());}
                    },{
                        xtype: 'button',
                        text: 'Cancel',
                        handler: function() {}
                    }]
                }]
            });
        };

        LABKEY.Query.selectRows({
            schemaName : 'study',
            queryName : 'Datasets',
            columns: 'Name',
            success : function(details){
                var rows = details.rows;
                this.datasets = {};
                for (var i = 0; i < rows.length; i++) {
                    var name = rows[i].Name;
                    this.datasets[name] = { name: name };
                }
            },
            scope : this
        });

        var previewMerge = function(oldId, newId) {

            var filters = [ LABKEY.Filter.create(<%= PageFlowUtil.jsString(subjectNounColumnName)%>, oldId + ';' + newId, LABKEY.Filter.Types.IN) ];

            // Iterate on datasets.
            for (var datasetName in this.datasets) {
                LABKEY.Query.selectRows( {
                    schemaName: 'study',
                    queryName: datasetName,
                    scope: this,
                    success: function(data) {
                        var oldLsids = [];
                        var newLsids = [];
                        for (var i = 0; i < data.rows.length; i++) {
                            // Figure out which participant the rows belong to
                            var row = data.rows[i];
                            if (row[<%= PageFlowUtil.jsString(subjectNounColumnName)%>] == oldId) {
                                oldLsids.push(row.lsid);
                            }
                            if (row[<%= PageFlowUtil.jsString(subjectNounColumnName)%>] == newId) {
                                newLsids.push(row.lsid);
                            }
                        }
                        // Remember the keys for the data rows
                        var dataset = this.datasets[data.queryName];
                        dataset.oldLsids = oldLsids;
                        dataset.newLsids = newLsids;

                        document.getElementById('previewPanel').innerHTML +=
                                "<div>" + Ext4.util.Format.htmlEncode(data.queryName) + ": " + (oldLsids.length > 0 && newLsids.length > 0 ? 'Potential Conflicts!' : 'No conflicts') + "</div>";

                        // If no data for either, or only old, ignore dataset
                        // If only data for oldId, no conflict
                        // If data for both, test saveRows() validateOnly = true
                        // Iterate on results
                        // Show form of affected datasets, conflict status, and input for action
                    }
                });
            }
        };


        var commitMerge = function(oldId, newId){
            // Bundled save rows
        };


        Ext4.onReady(init);

    })();
</script>
