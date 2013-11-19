/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var tests = [];
var duplicates = {};
var currentTest = -1;

function runNextTest()
{
    var el = Ext.get("number");
    currentTest++;
    if (currentTest == tests.length)
    {
        if (el) el.dom.innerHTML = "<h3>" + currentTest + " tests done</h3>";
        return;
    }
    if (el)
        el.dom.innerHTML = '' + currentTest;
    if (typeof tests[currentTest] == "function")
        tests[currentTest]();
    else
        tests[currentTest].run();
}

var QueryTest = function(query, mdx, fnValidateCellSet)
{
    var s = JSON.stringify(query) + "||" + mdx;
    if (duplicates[s])
        console.error("duplicate " + s);
    duplicates[s] = s;

    this.query = query;
    this.expectedMdx = mdx;
    this.validate = fnValidateCellSet;
    tests.push(this);
};
QueryTest.cube = null; // global
QueryTest.prototype.getCube = function()
{
    return QueryTest.cube;
};
QueryTest.prototype.run = function()
{
    var mdx  = this.getCube().getMDX().translateQuery(this.query);
    if (this.expectedMdx)
        this.validateMdx(this.expectedMdx, mdx);
    if (typeof this.validate == "function")
    {
        var config = Ext.apply({success:this.handleResult, scope:this});
        Ext.apply(config,this.query);
        this.getCube().getMDX().query(config);
    }
    else
    {
        runNextTest();
    }
};
QueryTest.prototype.handleResult = function()
{
    if (this.validate)
        this.validate.call(window, arguments);
    runNextTest();
};
QueryTest.prototype.validateMdx = function(expected, actual)
{
    // change all white space into single space
    expected = expected.trim().replace( /\s+/g, ' ');
    actual = actual.trim().replace( /\s+/g, ' ');
    if (expected != actual)
    {
        var el = Ext.get("log");
        if (el) el.insertHtml('beforeEnd', "test failed: " + currentTest + "<br>&nbsp;" + JSON.stringify(this.query) + "<br>");
        if (el) el.insertHtml('beforeEnd', "expected:<br>" + expected + "<br>");
        if (el) el.insertHtml('beforeEnd', "actual:<br>" + actual + "<br><hr>");
    }
    return expected == actual;
};


new QueryTest(
    {showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','Adenovirus']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[Adenovirus]))\n' +
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' +
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest(
    {showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','Adenovirus','VRC-HIVADV014-00-VP']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[Adenovirus].[VRC-HIVADV014-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest(
    {showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest(
    {showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' +
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest(
    {showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[(All)].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[Study].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Lab].[Lab].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ', {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[(All)].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Study].[Study]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[Study].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Lab].[Lab]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Lab].[Lab].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Clade].[Clade]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Clade].[Clade].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Sample Type].[Sample Type]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Sample Type].[Sample Type].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Tier].[Tier]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Tier].[Tier].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Clade].[Name]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Clade].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Assay.Target Area','lnum':2}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Lab','lnum':1}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Lab].[Lab].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Participant.Race','lnum':0}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Participant.Race].[(All)].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Participant.Race','lnum':1}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Participant.Race].[Race].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Participant.Country','lnum':1}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Participant.Country].[Country].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Participant.Sex].[Sex]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Participant.Sex].[Sex].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Vaccine.Type]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY {[Vaccine.Type].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Vaccine Component]'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ',  NON EMPTY [Vaccine Component].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env','gp140']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env].[gp140]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','gag']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[gag]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env','gp145']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env].[gp145]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','gag','gag']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[gag].[gag]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','pol']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[pol]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','nef','nef']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[nef].[nef]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','nef']}]}}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[nef]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Vaccine Component.Vaccine Insert].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[(All)].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[Study].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Lab].[Lab].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ', {[Assay.Target Area].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Assay.Target Area].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Assay.Target Area].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Assay.Target Area].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[(All)].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[Study].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Lab].[Lab].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Assay.Target Area].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[(All)].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Study].[Study].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Lab].[Lab].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ',  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','#null']}]}},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[#null])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Assay.Target Area].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Study','members':'members'}],'filter':[]},
    'SELECT\n' + 
    '[Measures].DefaultMember ON COLUMNS\n' + 
    ', {[Study].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');
new QueryTest({showEmpty:true, 'onRows':[{'hierarchy':'Study','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]},
    'WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n' + 
    'MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n' +
    'SELECT\n' + 
    '[Measures].ParticipantCount ON COLUMNS\n' + 
    ', {[Study].members} ON ROWS\n' + 
    'FROM [ParticipantCube]');


// UNDONE: other operators

Ext.onReady(function(){
    QueryTest.cube = LABKEY.query.olap.CubeManager.getCube({
        configId: 'CDS:/CDS',
        schemaName: 'CDS',
        name: 'ParticipantCube',
        deferLoad: true
    });
    QueryTest.cube.load();
    QueryTest.cube.onReady(runNextTest);
});