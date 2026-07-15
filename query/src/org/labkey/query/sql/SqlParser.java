/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.query.sql;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.MismatchedTokenException;
import org.antlr.runtime.MissingTokenException;
import org.antlr.runtime.ParserRuleReturnScope;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.antlr.runtime.tree.Tree;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.sql.LabKeySql;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.query.QueryServiceImpl;
import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.SoftReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.query.sql.antlr.SqlBaseParser.AGGREGATE;
import static org.labkey.query.sql.antlr.SqlBaseParser.ALIAS;
import static org.labkey.query.sql.antlr.SqlBaseParser.ALL;
import static org.labkey.query.sql.antlr.SqlBaseParser.AND;
import static org.labkey.query.sql.antlr.SqlBaseParser.ANY;
import static org.labkey.query.sql.antlr.SqlBaseParser.AS;
import static org.labkey.query.sql.antlr.SqlBaseParser.ASCENDING;
import static org.labkey.query.sql.antlr.SqlBaseParser.AVG;
import static org.labkey.query.sql.antlr.SqlBaseParser.BETWEEN;
import static org.labkey.query.sql.antlr.SqlBaseParser.BIT_AND;
import static org.labkey.query.sql.antlr.SqlBaseParser.BIT_OR;
import static org.labkey.query.sql.antlr.SqlBaseParser.BIT_XOR;
import static org.labkey.query.sql.antlr.SqlBaseParser.CASE;
import static org.labkey.query.sql.antlr.SqlBaseParser.CASE2;
import static org.labkey.query.sql.antlr.SqlBaseParser.CAST;
import static org.labkey.query.sql.antlr.SqlBaseParser.CLOSE;
import static org.labkey.query.sql.antlr.SqlBaseParser.COLON;
import static org.labkey.query.sql.antlr.SqlBaseParser.COMMA;
import static org.labkey.query.sql.antlr.SqlBaseParser.COMMENT;
import static org.labkey.query.sql.antlr.SqlBaseParser.CONCAT;
import static org.labkey.query.sql.antlr.SqlBaseParser.COUNT;
import static org.labkey.query.sql.antlr.SqlBaseParser.CROSS;
import static org.labkey.query.sql.antlr.SqlBaseParser.DATATYPE;
import static org.labkey.query.sql.antlr.SqlBaseParser.DECLARATION;
import static org.labkey.query.sql.antlr.SqlBaseParser.DELETE;
import static org.labkey.query.sql.antlr.SqlBaseParser.DESCENDING;
import static org.labkey.query.sql.antlr.SqlBaseParser.DISTINCT;
import static org.labkey.query.sql.antlr.SqlBaseParser.DIV;
import static org.labkey.query.sql.antlr.SqlBaseParser.DOT;
import static org.labkey.query.sql.antlr.SqlBaseParser.ELSE;
import static org.labkey.query.sql.antlr.SqlBaseParser.END;
import static org.labkey.query.sql.antlr.SqlBaseParser.EOF;
import static org.labkey.query.sql.antlr.SqlBaseParser.EQ;
import static org.labkey.query.sql.antlr.SqlBaseParser.ESCAPE;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXCEPT;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXISTS;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPANCESTORSOF;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPDESCENDANTSOF;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPLINEAGEOF;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPONENT;
import static org.labkey.query.sql.antlr.SqlBaseParser.EXPR_LIST;
import static org.labkey.query.sql.antlr.SqlBaseParser.FALSE;
import static org.labkey.query.sql.antlr.SqlBaseParser.FLOAT_SUFFIX;
import static org.labkey.query.sql.antlr.SqlBaseParser.FROM;
import static org.labkey.query.sql.antlr.SqlBaseParser.FULL;
import static org.labkey.query.sql.antlr.SqlBaseParser.GE;
import static org.labkey.query.sql.antlr.SqlBaseParser.GROUP;
import static org.labkey.query.sql.antlr.SqlBaseParser.GROUP_CONCAT;
import static org.labkey.query.sql.antlr.SqlBaseParser.GT;
import static org.labkey.query.sql.antlr.SqlBaseParser.HAVING;
import static org.labkey.query.sql.antlr.SqlBaseParser.HEX_DIGIT;
import static org.labkey.query.sql.antlr.SqlBaseParser.IDENT;
import static org.labkey.query.sql.antlr.SqlBaseParser.ID_LETTER;
import static org.labkey.query.sql.antlr.SqlBaseParser.ID_START_LETTER;
import static org.labkey.query.sql.antlr.SqlBaseParser.IFDEFINED;
import static org.labkey.query.sql.antlr.SqlBaseParser.IN;
import static org.labkey.query.sql.antlr.SqlBaseParser.INNER;
import static org.labkey.query.sql.antlr.SqlBaseParser.INSERT;
import static org.labkey.query.sql.antlr.SqlBaseParser.INTERSECT;
import static org.labkey.query.sql.antlr.SqlBaseParser.INTO;
import static org.labkey.query.sql.antlr.SqlBaseParser.IN_LIST;
import static org.labkey.query.sql.antlr.SqlBaseParser.IS;
import static org.labkey.query.sql.antlr.SqlBaseParser.IS_NOT;
import static org.labkey.query.sql.antlr.SqlBaseParser.JOIN;
import static org.labkey.query.sql.antlr.SqlBaseParser.LE;
import static org.labkey.query.sql.antlr.SqlBaseParser.LEFT;
import static org.labkey.query.sql.antlr.SqlBaseParser.LIKE;
import static org.labkey.query.sql.antlr.SqlBaseParser.LIMIT;
import static org.labkey.query.sql.antlr.SqlBaseParser.LINE_COMMENT;
import static org.labkey.query.sql.antlr.SqlBaseParser.LT;
import static org.labkey.query.sql.antlr.SqlBaseParser.MAX;
import static org.labkey.query.sql.antlr.SqlBaseParser.METHOD_CALL;
import static org.labkey.query.sql.antlr.SqlBaseParser.MIN;
import static org.labkey.query.sql.antlr.SqlBaseParser.MINUS;
import static org.labkey.query.sql.antlr.SqlBaseParser.MODULO;
import static org.labkey.query.sql.antlr.SqlBaseParser.NE;
import static org.labkey.query.sql.antlr.SqlBaseParser.NOT;
import static org.labkey.query.sql.antlr.SqlBaseParser.NOT_BETWEEN;
import static org.labkey.query.sql.antlr.SqlBaseParser.NOT_IN;
import static org.labkey.query.sql.antlr.SqlBaseParser.NOT_LIKE;
import static org.labkey.query.sql.antlr.SqlBaseParser.NULL;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_DOUBLE;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_FLOAT;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_INT;
import static org.labkey.query.sql.antlr.SqlBaseParser.NUM_LONG;
import static org.labkey.query.sql.antlr.SqlBaseParser.ON;
import static org.labkey.query.sql.antlr.SqlBaseParser.OPEN;
import static org.labkey.query.sql.antlr.SqlBaseParser.OR;
import static org.labkey.query.sql.antlr.SqlBaseParser.ORDER;
import static org.labkey.query.sql.antlr.SqlBaseParser.OUTER;
import static org.labkey.query.sql.antlr.SqlBaseParser.PARAM;
import static org.labkey.query.sql.antlr.SqlBaseParser.PIVOT;
import static org.labkey.query.sql.antlr.SqlBaseParser.PLUS;
import static org.labkey.query.sql.antlr.SqlBaseParser.QUERY;
import static org.labkey.query.sql.antlr.SqlBaseParser.QUOTED_IDENTIFIER;
import static org.labkey.query.sql.antlr.SqlBaseParser.QUOTED_STRING;
import static org.labkey.query.sql.antlr.SqlBaseParser.RANGE;
import static org.labkey.query.sql.antlr.SqlBaseParser.RIGHT;
import static org.labkey.query.sql.antlr.SqlBaseParser.ROW_STAR;
import static org.labkey.query.sql.antlr.SqlBaseParser.SELECT;
import static org.labkey.query.sql.antlr.SqlBaseParser.SELECT_FROM;
import static org.labkey.query.sql.antlr.SqlBaseParser.SET;
import static org.labkey.query.sql.antlr.SqlBaseParser.SOME;
import static org.labkey.query.sql.antlr.SqlBaseParser.SQL_NE;
import static org.labkey.query.sql.antlr.SqlBaseParser.STAR;
import static org.labkey.query.sql.antlr.SqlBaseParser.STATEMENT;
import static org.labkey.query.sql.antlr.SqlBaseParser.STDDEV;
import static org.labkey.query.sql.antlr.SqlBaseParser.SUM;
import static org.labkey.query.sql.antlr.SqlBaseParser.THEN;
import static org.labkey.query.sql.antlr.SqlBaseParser.TRUE;
import static org.labkey.query.sql.antlr.SqlBaseParser.UNARY_MINUS;
import static org.labkey.query.sql.antlr.SqlBaseParser.UNARY_PLUS;
import static org.labkey.query.sql.antlr.SqlBaseParser.UNION;
import static org.labkey.query.sql.antlr.SqlBaseParser.UNION_ALL;
import static org.labkey.query.sql.antlr.SqlBaseParser.UPDATE;
import static org.labkey.query.sql.antlr.SqlBaseParser.VALUES;
import static org.labkey.query.sql.antlr.SqlBaseParser.WHEN;
import static org.labkey.query.sql.antlr.SqlBaseParser.WHERE;
import static org.labkey.query.sql.antlr.SqlBaseParser.WITH;
import static org.labkey.query.sql.antlr.SqlBaseParser.WS;


/**
 * SqlParser is responsible for the first two phases of the SQL transformation process
 * step one - the ANTLR parser returns a tree of Nodes
 * step two - translate the tree into a tree of QNodes
 */

@SuppressWarnings({"ThrowableResultOfMethodCallIgnored","ThrowableInstanceNeverThrown"})
public class SqlParser
{
    // these are not a regular method and need special handling
    public static final String FIND_COLUMN_METHOD_NAME = "findcolumn";


    private static final Logger _log = LogHelper.getLogger(SqlParser.class, "LabKey SQL parser");

    boolean failOnUnrecognizedMethodName = false;
    ArrayList<Exception> _parseErrors;
    List<QueryParseException> _parseWarnings;
    QNode _root;
    ArrayList<QParameter> _parameters;
    final SqlDialect _dialect;
    Container _container = null;

    final static SoftPool<_SqlParser> _parserPool = new SoftPool<>();

    static class SoftPool<T> implements MemTrackerListener
    {
        SoftPool()
        {
            MemTracker.get().register(this);
            assert MemTracker.get().put(this);
        }

        @Override
        public synchronized void beforeReport(Set<Object> set)
        {
            set.add(SqlParser._parserPool);
            for (var r : _pool)
                set.add(r.get());
        }

        final int maxPoolSize=3;
        final ArrayList<SoftReference<T>> _pool = new ArrayList<>();
        @Nullable
        public synchronized T get()
        {
            while (!_pool.isEmpty())
            {
                SoftReference<T> r = _pool.removeFirst();
                T t = r.get();
                if (null != t)
                    return t;
            }
            return null;
        }
        public synchronized void put(T t)
        {
            if (_pool.size()<maxPoolSize)
            {
                _pool.add(new SoftReference<>(t));
                return;
            }
            for (int i=0 ; i<_pool.size() ; i++)
            {
                if (null==_pool.get(i).get())
                {
                    _pool.set(i, new SoftReference<>(t));
                    return;
                }
            }
        }
    }

    //
    // PUBLIC
    //

    public SqlParser()
    {
        _dialect =null;
    }

    /**
     * providing a dialect only changes validation against passthrough methods,
     * container only affects {substitutePath moduleProperty()}
     */
    public SqlParser(SqlDialect d, Container c)
    {
        _dialect = d;
        _container = c;
    }

    SqlParser setFailOnUnrecognizedMethodName(boolean b)
    {
        failOnUnrecognizedMethodName = b;
        return this;
    }

    /** see also _SqlParser.close() for AutoCloseable behavior */
    private _SqlParser getAntlrParser()
    {
        _SqlParser ret = _parserPool.get();
        if (null == ret)
            ret = new _SqlParser();
        return ret;
    }

    // for testing only
    public Tree rawQuery(String str) throws Exception
    {
        try (var parser = getAntlrParser())
        {
            _parseErrors = new ArrayList<>();
            parser.reset(str, _parseErrors);
            ParserRuleReturnScope selectScope = parser.statement();
            if (!_parseErrors.isEmpty())
                throw _parseErrors.getFirst();
            return (Tree) selectScope.getTree();
        }
    }


    public QNode parseQuery(@NotNull String str, @NotNull List<? super QueryParseException> errors, @Nullable List<QueryParseException> warnings)
    {
        _parseErrors = new ArrayList<>();
        _parseWarnings = null==warnings ? new ArrayList<>() : warnings;
        try (var parser = getAntlrParser())
        {
            parser.reset(str, _parseErrors);
            ParserRuleReturnScope selectScope = null;
            try
            {
                selectScope = parser.parseSelect();
                int last = parser.getTokenStream().LA(1);
                if (EOF != last)
                {
                    CommonToken t = (CommonToken)parser.getTokenStream().LT(1);
                    //noinspection ThrowableInstanceNeverThrown
                    if (null != t)
                        _parseErrors.add(new QueryParseException("Unexpected token: " + t.getText(), null, t.getLine(), t.getCharPositionInLine()));
                    else
                        _parseErrors.add(new QueryParseException("EOF expected", null, 0, 0));
                }
                else if (null != warnings)
                {
                    warnings.addAll(parser.getWarnings());
                }
            }
            catch (Exception x)
            {
                _parseErrors.add(x);
            }

            if (_parseErrors.isEmpty())
            {
                CommonTree parseRoot = (CommonTree) selectScope.getTree();
                assert parseRoot != null;
                assert parseRoot.getType() == STATEMENT;
                assert parseRoot.getChildCount() == 1 || parseRoot.getChildCount() == 2 || parseRoot.getChildCount() == 3;

                ArrayList<CommonTree> list = new ArrayList<>((Collection<CommonTree>) parseRoot.getChildren());
                CommonTree parameters;


                // PARAMETERS

                if (!list.isEmpty() && list.getFirst().getType() == SqlBaseParser.PARAMETERS)
                {
                    _parameters = new ArrayList<>();
                    parameters = list.removeFirst();
                    for (Object parameter : parameters.getChildren())
                    {
                        QParameter p = convertParameter((CommonTree)parameter);
                        if (null == p)
                        {
                            assert !_parseErrors.isEmpty();
                            continue;
                        }
                        _parameters.add(p);
                    }
                }


                // COMMON TABLE EXPRESSIONS

                QNode qnodeWith = null;
                if (!list.isEmpty() && list.getFirst().getType() == SqlBaseParser.WITH)
                {
                    CommonTree withStmt = list.removeFirst();
                    if (null == _dialect || _dialect.isLabKeyWithSupported())    // Check dialect if we have one
                        qnodeWith = convertParseTree(withStmt);
                    else
                        errors.add(new QueryParseException("WITH statement not supported", null, parseRoot.getLine(), parseRoot.getCharPositionInLine()));
                }


                // SELECT

                if (list.isEmpty())
                {
                    errors.add(new QueryParseException("SELECT statement expected", null, parseRoot.getLine(), parseRoot.getCharPositionInLine()));
                    return null;
                }

                CommonTree selectStmt = list.removeFirst();
                if (selectStmt.getType() != QUERY && !isSetOperator(selectStmt.getType()))
                {
                    errors.add(new QueryParseException(tokenName(selectStmt.getType()) + " statements are not supported", null, parseRoot.getLine(), parseRoot.getCharPositionInLine()));
                    return null;
                }

                QNode qnodeSelect = convertParseTree(selectStmt);
                QNode qnodeRoot;
                if (null != qnodeWith)
                {
                    qnodeRoot = new QWithQuery();
                    qnodeRoot.setTokenText("WithQuery");
                    qnodeRoot.appendChildren(qnodeWith, qnodeSelect);
                }
                else
                {
                    qnodeRoot = qnodeSelect;
                }
                assert dump(qnodeRoot);
                assert MemTracker.getInstance().put(qnodeRoot);

                if (qnodeRoot instanceof QQuery || qnodeRoot instanceof QUnion || qnodeRoot instanceof QWithQuery)
                    _root = qnodeRoot;
                else
                    errors.add(new QueryParseException("This does not look like a WITH, SELECT or UNION query", null, 0, 0));
            }

            CommonTokenStream tokens = parser.getTokenStream() instanceof CommonTokenStream cts ? cts : null;
            for (Throwable e : _parseErrors)
            {
                errors.add(wrapParseException(e, tokens));
            }

            if (null != _root)
            {
                dump(_root);
                _log.debug(toPrefixString(_root));
            }
            return _root;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
            return null;
        }
    }


    private boolean isSetOperator(int type)
    {
        return switch (type)
        {
            case UNION, UNION_ALL, INTERSECT, EXCEPT -> true;
            default -> false;
        };
    }


    public QNode getRoot()
    {
        return _root;
    }


    public ArrayList<QParameter> getParameters()
    {
        return null==_parameters ? new ArrayList<>(0) : _parameters;
    }

    public QExpr parseExpr(String str, List<? super QueryParseException> errors)
    {
        return parseExpr(str, false, errors);
    }

    public QExpr parseExpr(String str, boolean constExpression, List<? super QueryParseException> errors)
    {
        _parseErrors = new ArrayList<>();
        _parseWarnings = new ArrayList<>();
        try (var parser = getAntlrParser())
        {
            parser.reset(str, _parseErrors);
            ParserRuleReturnScope exprScope = null;
            try
            {
                if (constExpression)
                    exprScope = parser.parseConstantExpression();
                else
                    exprScope = parser.parseExpression();
                int last = parser.getTokenStream().LA(1);
                if (EOF != last)
                    //noinspection ThrowableInstanceNeverThrown
                    _parseErrors.add(new QueryParseException("EOF expected", null, 0, 0));
            }
            catch (Exception x)
            {
                _parseErrors.add(x);
            }

            if (_parseErrors.isEmpty())
            {
                CommonTree parseRoot = (CommonTree) exprScope.getTree();
                assert MemTracker.getInstance().put(parseRoot);
                if (null == parseRoot)
                    return null;

                QNode qnodeRoot = convertParseTree(parseRoot);
                assert dump(qnodeRoot);
                assert MemTracker.getInstance().put(qnodeRoot);

                _root = qnodeRoot instanceof QExpr ? (QExpr) qnodeRoot : null;
            }

            CommonTokenStream tokens = parser.getTokenStream() instanceof CommonTokenStream cts ? cts : null;
            for (Throwable e : _parseErrors)
            {
                errors.add(wrapParseException(e, tokens));
            }
            return (QExpr)_root;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
            return null;
        }
    }

    public SchemaKey parseIdentifier(String str)
    {
        _parseErrors = new ArrayList<>();
        try (var parser = getAntlrParser())
        {
            parser.reset(str, _parseErrors);
            try
            {
                ParserRuleReturnScope scope = parser.dottedIdentifier();

                int last = parser.getTokenStream().LA(1);
                if (EOF != last || !_parseErrors.isEmpty())
                    return SchemaKey.fromParts(str);

                LinkedList<CommonTree> q = new LinkedList<>();
                ArrayList<String> parts = new ArrayList<>();
                q.add((CommonTree) scope.getTree());
                while (!q.isEmpty())
                {
                    CommonTree t = q.removeFirst();
                    if (t.getType() == IDENT)
                        parts.add(t.getText());
                    else if (t.getType() == QUOTED_IDENTIFIER)
                        parts.add(LabKeySql.unquoteIdentifier(t.getText()));
                    else if (t.getType() == DOT)
                    {
                        q.addFirst((CommonTree)t.getChildren().get(1));
                        q.addFirst((CommonTree)t.getChildren().get(0));
                    }
                    else
                        return SchemaKey.fromParts(str);
                }
                return SchemaKey.fromParts(parts);
            }
            catch (Exception x)
            {
                return SchemaKey.fromParts(str);
            }
        }
        catch (Exception x)
        {
            return SchemaKey.fromParts(str);
        }
    }



    public static String toPrefixString(Tree tree)
    {
        StringBuilder sb = new StringBuilder();
        _prefix(tree, sb);
        return sb.toString();
    }


    private static void _prefix(Tree tree, StringBuilder sb)
    {
        if (tree.getChildCount() == 0)
        {
            sb.append(tree.getText());
        }
        else
        {
            sb.append("(");
            sb.append(tree.getText());
            for (int i=0 ; i<tree.getChildCount() ; i++)
            {
                sb.append(" ");
                _prefix(tree.getChild(i),sb);
            }
            sb.append(")");
        }
    }

    public static String toPrefixString(QNode tree)
    {
        StringBuilder sb = new StringBuilder();
        _prefix(tree, sb);
        return sb.toString();
    }


    private static void _prefix(QNode tree, StringBuilder sb)
    {
        if (tree.getFirstChild() == null)
        {
            sb.append(_text(tree));
        }
        else
        {
            sb.append("(");
            sb.append(_text(tree));
            for (QNode child : tree.children())
            {
                sb.append(" ");
                _prefix(child,sb);
            }
            sb.append(")");
        }
    }

    private static String _text(QNode q)
    {
        if (q.getTokenType() == METHOD_CALL)
            return "METHOD_CALL";
        if (q instanceof QType qtype)
            return qtype.getSourceText();
        return q.getTokenText();
    }


    public boolean dump(CommonTree tree)
    {
        if (!_log.isDebugEnabled())
            return true;
        StringWriter sw = new StringWriter();
        dump(tree, new PrintWriter(sw), "\n");
        _log.debug(sw.toString());
        return true;
    }


    public void dump(CommonTree tree, PrintWriter out)
    {
        dump(tree, out, "\n");
        out.println();
    }


    protected void dump(Tree tree, PrintWriter out, String nl)
    {
        out.printf("%s%s: %s", nl, getClass().getSimpleName(), tree.getText());
        for (int i=0 ; i<tree.getChildCount() ; i++)
        {
            Tree c = tree.getChild(i);
            dump(c, out, nl + "    |");
        }
    }


    private boolean dump(QNode node)
    {
        if (null != node && _log.isDebugEnabled())
        {
            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw))
            {
                node.dump(pw);
            }
            _log.debug(sw.toString());
        }
        return true;
    }

    static public boolean isLegalIdentifierChar(char ch, boolean fFirst)
    {
        if (!fFirst && ch >= '0' && ch <= '9')
            return true;
        return ch == '_' || ch == '$' ||
                ch >= 'a' && ch <= 'z' ||
                ch >= 'A' && ch <= 'Z';
    }

    static public boolean isLegalIdentifier(String str)
    {
        if (str.isEmpty())
            return false;
        if (keywords.contains(str))
            return false;
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalIdentifierChar(str.charAt(i), i == 0))
            {
                return false;
            }
        }
        return true;
    }


    //
    // IMPL
    //

    static private final Set<String> keywords = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "all","any","and","as","asc","avg",
            "between","both",
            "case","class","count","current_date","current_time","current_timestamp",
            "delete","desc","distinct",
            "elements","else","empty","end","escape","except","exists",
            "false","fetch","from","full",
            "group",
            "having",
            "in","indices","inner","insert","intersect","into","is",
            "join",
            "leading","left","like","limit",
            "max","member","min",
            "new","not","null",
            "of","on","or","order","outer",
            "right",
            "select","set","some","stddev","sum",
            "trailing","then","true",
            "union","update","user",
            "versioned",
            "when","where"
            ));


    static QueryParseException wrapParseException(Throwable e)
    {
        return wrapParseException(e, null);
    }


    static QueryParseException wrapParseException(Throwable e, @Nullable CommonTokenStream tokens)
    {
        if (e instanceof QueryParseException)
        {
            return (QueryParseException) e;
        }
//        else if (e instanceof TokenStreamRecognitionException)
//        {
//            e = ((TokenStreamRecognitionException) e).recog;
//        }
        else if (e instanceof RecognitionException re)
        {
            String message = formatRecognitionException(re, tokens);
            return new QueryParseException(message, re, re.line, re.charPositionInLine);
        }
        else if (e instanceof RuntimeException)
        {
            _log.error("Unexpected exception", e);
        }
        return new QueryParseException("Unexpected exception", e, 0, 0);
    }


    static String formatRecognitionException(RecognitionException re, @Nullable CommonTokenStream tokens)
    {
        String message = re.getMessage();
        if (null != message)
            return message;

        String missing = null;
        String near = null;

        if (null != re.token)
            near = re.token.getText();
        else if (re.c < 0)
            near = "<EOF>";     // lexer error at end of input (e.g. unterminated string literal)
        else if (re.c > 0)
            near = String.valueOf((char)re.c);      // lexer error: no token, but we know the offending character
        if (re instanceof MissingTokenException mte)
        {
            if (null != mte.inserted)
                missing = tokenName(((CommonToken)mte.inserted).getType());
        }
        else if (re instanceof MismatchedTokenException mte && null == re.token && mte.expecting > 0)
        {
            missing = String.valueOf((char)mte.expecting);      // lexer error: expected character
        }

        if (null != near)
            message = "Syntax error near '" + near + "'";
        else
            message = "Syntax error";
        if (null != missing)
            message += ", expected '" + missing + "'";

        // append a targeted hint when the failure looks like a recognizable unsupported construct
        String hint = forSyntaxError(re, tokens);
        if (null != hint)
            message += ". " + hint;
        return message;
    }


    /**
     * Suggestions for common standard-SQL constructs that LabKey SQL does not support. Authors (and AI assistants)
     * regularly reach for window functions, OFFSET, EXTRACT, ILIKE, etc.; the generic "Syntax error near '...'" gives
     * them no way to converge on working LabKey SQL, so we append a targeted hint when the failure looks recognizable.
     *
     * These hints are consulted ONLY after a parse error has already occurred, keyed off the token ANTLR blames (with a
     * little look-behind for constructs where the blamed token is generic). None of the trigger words are reserved in
     * LabKey SQL -- most are legal identifiers -- so this method must never influence what parses; message text only.
     */
    @Nullable
    static String forSyntaxError(RecognitionException re, @Nullable CommonTokenStream tokens)
    {
        if (null == re.token || null == re.token.getText())
            return null;
        String near = re.token.getText().toLowerCase();
        String prev1 = previousToken(tokens, re.token, 1);
        String prev2 = previousToken(tokens, re.token, 2);
        String prev3 = previousToken(tokens, re.token, 3);

        switch (near)
        {
            case "offset":
                return "OFFSET is not supported. Use LIMIT n; apply paging via the client API (maxRows/offset).";
            case "fetch":
                return "FETCH FIRST is not supported. Use LIMIT n.";
            case "ilike":
                return "ILIKE is not supported. Use LOWER(x) LIKE LOWER(pattern).";
            case "using":
                return "JOIN ... USING is not supported. Use JOIN ... ON a.col = b.col.";
            case "nulls":
                return "NULLS FIRST/LAST is not supported. Try ORDER BY x IS NULL, x.";
            case ":":
                return "The '::' cast syntax is not supported. Use CAST(expr AS TYPE).";
            case "(":
                // "MAX(a) OVER (...)" parses OVER as a column alias, so the '(' gets the blame
                if ("over".equals(prev1))
                    return "Window functions (OVER) are not supported in LabKey SQL.";
                if ("filter".equals(prev1))
                    return "FILTER is not supported. Use an aggregate over CASE: SUM(CASE WHEN condition THEN 1 ELSE 0 END).";
                // "JOIN S USING (x)" parses USING as the table alias, so the '(' gets the blame
                if ("using".equals(prev1))
                    return "JOIN ... USING is not supported. Use JOIN ... ON a.col = b.col.";
                // "CURRENT_DATE()" -- these are niladic keywords, not functions, so the trailing '(' is unexpected
                if ("current_date".equals(prev1) || "current_time".equals(prev1) || "current_timestamp".equals(prev1))
                    return "CURRENT_DATE/CURRENT_TIME/CURRENT_TIMESTAMP take no parentheses; use them as bare keywords.";
                return null;
            case "distinct":
                if ("is".equals(prev1) || "not".equals(prev1))
                    return "IS [NOT] DISTINCT FROM is not supported. Use is_distinct_from(a, b) or is_not_distinct_from(a, b).";
                if ("(".equals(prev1))
                    return "DISTINCT is only supported inside COUNT() and GROUP_CONCAT().";
                return null;
            case "from":
                // "EXTRACT(YEAR FROM d)" parses as a method call, so the FROM gets the blame
                if ("(".equals(prev2) && "extract".equals(prev3))
                    return "EXTRACT is not supported. Use YEAR(), MONTH(), DAYOFMONTH(), HOUR(), etc.";
                return null;
            default:
                if (near.startsWith("'") && "interval".equals(prev1))
                    return "INTERVAL literals are not supported. Use TIMESTAMPADD('SQL_TSI_DAY', n, ts) and TIMESTAMPDIFF().";
                // "SELECT TOP 10 a FROM R" parses TOP as an expression, so the blame lands on a later token
                if (("top".equals(prev1) && "select".equals(prev2)) || ("top".equals(prev2) && "select".equals(prev3)))
                    return "TOP is not supported. Use LIMIT n at the end of the statement.";
                return null;
        }
    }


    @Nullable
    private static String previousToken(@Nullable CommonTokenStream tokens, Token t, int back)
    {
        if (null == tokens)
            return null;
        int i = t.getTokenIndex();
        if (i < back || i >= tokens.size())
            return null;
        Token p = tokens.get(i - back);
        return null == p || null == p.getText() ? null : p.getText().toLowerCase();
    }


    // Suggestions for unrecognized method names, keyed by lower-cased name. These entries are valid on both
    // databases; dialect-specific suggestions live in forUnknownMethod(). Some entries (len, charindex, instr)
    // are dialect-specific methods that resolve on one database and land here on the other.
    private static final Map<String, String> methodHints = Map.ofEntries(
            Map.entry("position", "Use LOCATE(substring, string[, start])."),
            Map.entry("extract", "Use YEAR(), MONTH(), DAYOFMONTH(), HOUR(), etc."),
            Map.entry("string_agg", "Use GROUP_CONCAT([DISTINCT] expr[, separator])."),
            Map.entry("nvl", "Use COALESCE(a, b) or IFNULL(a, b)."),
            Map.entry("isnull", "Use IFNULL(a, b) or COALESCE(a, b)."),
            Map.entry("iif", "Use CASE WHEN condition THEN a ELSE b END."),
            Map.entry("if", "Use CASE WHEN condition THEN a ELSE b END."),
            Map.entry("datediff", "Use TIMESTAMPDIFF('SQL_TSI_DAY', ts1, ts2) or AGE()/age_in_days()."),
            Map.entry("dateadd", "Use TIMESTAMPADD('SQL_TSI_DAY', n, ts)."),
            Map.entry("date_part", "Use YEAR(), MONTH(), DAYOFMONTH(), HOUR(), etc."),
            Map.entry("day", "Use DAYOFMONTH(date)."),
            Map.entry("len", "Use LENGTH(string)."),
            Map.entry("instr", "Use LOCATE(substring, string)."),
            Map.entry("charindex", "Use LOCATE(substring, string)."),
            Map.entry("getdate", "Use NOW()."),
            Map.entry("sysdate", "Use NOW().")
    );

    /**
     * The suggestion should simply be appropriate for the current dialect -- never name a database product.
     * A null dialect (expression parsing, tests) gets the portable suggestion.
     */
    @Nullable
    static String forUnknownMethod(String name, @Nullable SqlDialect dialect)
    {
        boolean pg = null != dialect && dialect.isPostgreSQL();
        return switch (name.toLowerCase())
        {
            case "trim" -> pg ? "Use btrim(x) or LTRIM(RTRIM(x))." : "Use LTRIM(RTRIM(x)).";
            case "substring_index" -> pg ? "Use split_part(string, delimiter, n)." : null;
            case "regexp_like", "regexp_matches" -> pg ? "Use similar_to(x, pattern) or regexp_replace(x, pattern, replacement)." : "Use LIKE with wildcards.";
            case "date_trunc" -> pg ? "Use CAST(ts AS DATE) for day granularity, or to_char(ts, format)." : "Use CAST(ts AS DATE) for day granularity.";
            default -> methodHints.get(name.toLowerCase());
        };
    }


    public static String tokenName(int type)
    {
        return switch (type)
        {
            case EOF -> "EOF";
            case AGGREGATE -> "AGGREGATE FUNCTION";
            case ALIAS -> "AS";
            case EXPR_LIST -> "EXPR LIST";
            case IN_LIST -> "IN LIST";
            case IS_NOT -> "IS NOT";
            case METHOD_CALL -> "METHOD CALL";
            case NOT_BETWEEN -> "NOT BETWEEN";
            case NOT_IN -> "NOT IN";
            case NOT_LIKE -> "NOT LIKE";
            case QUERY -> "QUERY";
            case RANGE -> "RANGE";
            case ROW_STAR -> "*";
            case SELECT_FROM -> "SELECT FROM";
            case UNARY_MINUS -> "-";
            case UNARY_PLUS -> "+";
            case UNION_ALL -> "UNION ALL";
            case ALL -> "ALL";
            case ANY -> "ANY";
            case AND -> "AND";
            case AS -> "AS";
            case ASCENDING -> "ASCENDING";
            case AVG -> "AVG";
            case BETWEEN -> "BETWEEN";
            case CASE -> "CASE";
            case CASE2 -> "CASE";
            case CAST -> "CAST";
            case COUNT -> "COUNT";
            case DATATYPE -> "DATATYPE";
            case DELETE -> "DELETE";
            case DESCENDING -> "DESCENDING";
            case DISTINCT -> "DISTINCT";
            case DOT -> "DOT";
            case ELSE -> "ELSE";
            case END -> "END";
            case ESCAPE -> "ESCAPE";
            case EXISTS -> "EXISTS";
            case FALSE -> "FALSE";
            case FROM -> "FROM";
            case FULL -> "FULL";
            case GROUP -> "GROUP";
            case HAVING -> "HAVING";
            case IFDEFINED -> "IFDEFINED";
            case IN -> "IN";
            case INNER -> "INNER";
            case INSERT -> "INSERT";
            case INTO -> "INTO";
            case IS -> "IS";
            case JOIN -> "JOIN";
            case LEFT -> "LEFT";
            case LIKE -> "LIKE";
            case LIMIT -> "LIMIT";
            case MAX -> "MAX";
            case GROUP_CONCAT -> "GROUP_CONCAT";
            case MIN -> "MIN";
            case NOT -> "NOT";
            case NULL -> "NULL";
            case ON -> "ON";
            case OR -> "OR";
            case ORDER -> "ORDER";
            case OUTER -> "OUTER";
            case RIGHT -> "RIGHT";
            case SELECT -> "SELECT";
            case SET -> "SET";
            case SOME -> "SOME";
            case STDDEV -> "STDDEV";
            case SUM -> "SUM";
            case THEN -> "THEN";
            case TRUE -> "TRUE";
            case UNION -> "UNION";
            case UPDATE -> "UPDATE";
            case WHERE -> "WHERE";
            case WHEN -> "WHEN";
            case COMMA -> ",";
            case EQ -> "=";
            case OPEN -> "(";
            case CLOSE -> ")";
            case NUM_INT -> "NUMBER";
            case BIT_OR -> "|";
            case BIT_XOR -> "^";
            case NE -> "!=";
            case SQL_NE -> "<>";
            case LT -> "<";
            case GT -> ">";
            case LE -> "<=";
            case GE -> ">=";
            case CONCAT -> "||";
            case PLUS -> "+";
            case MINUS -> "-";
            case BIT_AND -> "&";
            case STAR -> "*";
            case DIV -> "/";
            case MODULO -> "%";
            case PARAM -> "?";
            case QUOTED_STRING -> "QUOTED STRING";
            case NUM_LONG -> "NUMBER";
            case NUM_DOUBLE -> "NUMBER";
            case NUM_FLOAT -> "NUMBER";
            case IDENT -> "IDENTIFIER";
            case QUOTED_IDENTIFIER -> "QUOTED IDENTIFIER";
            case COLON -> ":";
            case ID_START_LETTER -> "ID_START_LETTER";
            case ID_LETTER -> "ID_LETTER";
            case WS -> "WHITE SPACE";
            case EXPONENT -> "EXPONENT";
            case FLOAT_SUFFIX -> "FLOAT_SUFFIX";
            case HEX_DIGIT -> "HEX_DIGIT";
            case COMMENT -> "COMMENT";
            case LINE_COMMENT -> "LINE COMMENT";
            case EXCEPT -> "EXCEPT";
            case INTERSECT -> "INTERSECT";
            default -> null;
        };
    }


    private QParameter convertParameter(CommonTree node)
    {
        if (node.getChildCount() < 2 || node.getChildCount() > 3)
        {
            _parseErrors.add(new QueryParseException("Invalid parameter declaration", null, node.getLine(), node.getCharPositionInLine()));
            return null;
        }

        QNode parameter = convertParseTree(node);
        QNode nodeName = parameter.childList().get(0);
        QNode nodeType = parameter.childList().get(1);
        QNode nodeDefault = null;
        
        if (parameter.childList().size() > 2)
        {
            nodeDefault = parameter.childList().get(2);
            if (!(nodeDefault instanceof IConstant))
            {
                Tree n = node.getChild(2);
                _parseErrors.add(new QueryParseException("Constant expected after DEFAULT", null, n.getLine(), n.getCharPositionInLine()));
                return null;
            }
        }

        if (!(nodeName instanceof QIdentifier identName) || !(nodeType instanceof QIdentifier identType) || (null != nodeDefault && !(nodeDefault instanceof QExpr)))
        {
            _parseErrors.add(new QueryParseException("Parse exception in parameter declaration", null, node.getLine(), node.getCharPositionInLine()));
            return null;
        }

        QExpr exprDefault = (QExpr)nodeDefault;
        boolean required = exprDefault == null;
        // parameter types are a subset of convert types
        ParameterType parameterType = ParameterType.resolve(identType.getIdentifier());
        if (null == parameterType)
        {
            _parseErrors.add(new QueryParseException("Parameter type is not supported: " + identType.getIdentifier(), null, identType.getLine(), identType.getColumn()));
            return null;
        }
        Object value = null==exprDefault ? null : parameterType.convert(((IConstant)exprDefault).getValue());

        // reuse convert helper (we already validated parameter type above)
        QType convertType = createType(nodeType);
        if (null == convertType)
            return null;
        return new QParameter(parameter, identName.getIdentifier(), convertType, required, value);
    }

    private QNode convertParseTree(CommonTree node)
    {
        return convertParseTree(node, false);
    }

    private QNode convertParseTree(CommonTree node, boolean constExpr)
    {
        if (node.getToken().getType() == SqlBaseParser.CONSTANT_EXPRESSION)
        {
            assert 1 == node.getChildCount();
            return convertParseTree((CommonTree)node.getChild(0), true);
        }

        LinkedList<QNode> l = new LinkedList<>();
        for (int i=0 ; i<node.getChildCount() ; i++)
        {
            CommonTree child = (CommonTree)node.getChild(i);
            if (child.getType() == COMMA)
                continue;
            boolean fn = node.getType() == METHOD_CALL && i==0;
            QNode q = convertParseTree(child, constExpr && !fn);
            if (q == null)
            {
                assert !_parseErrors.isEmpty();
                return null;
            }
            l.add(q);
        }
        return convertNode(node, l, constExpr);
    }


    private QNode convertNode(CommonTree node, LinkedList<QNode> children, boolean constExpr)
    {
        label:
        switch (node.getType())
        {
            case SqlBaseParser.ALIAS:
            case SqlBaseParser.AS:
            {
                // CONSIDER: check type
//                if (children.size() == 1)
//                    return first(children);
                node.getToken().setType(SqlBaseParser.AS);
                break;
            }
            case SqlBaseParser.DIV:
            {
                var usesNullIf = false;
                var nonZeroConstant = false;
                var divisorType = children.size() > 1 ? children.get(1).getTokenType() : 0;
                if (divisorType== SqlBaseParser.METHOD_CALL)
                {
                    var method = children.get(1).childList().getFirst();
                    if ("NULLIF".equalsIgnoreCase(method.getTokenText()))
                        usesNullIf = true;
                }
                else if (divisorType== SqlBaseParser.NUM_DOUBLE || divisorType== SqlBaseParser.NUM_FLOAT || divisorType== SqlBaseParser.NUM_INT || divisorType== SqlBaseParser.NUM_LONG)
                {
                    try
                    {
                        nonZeroConstant = 0.0 != (Double) JdbcType.DOUBLE.convert(children.get(1).getTokenText());
                    }
                    catch(ConversionException e)
                    {
                        nonZeroConstant = true;
                    }
                }
                if (!usesNullIf && !nonZeroConstant)
                    _parseWarnings.add(new QueryParseWarning("Consider using NULLIF() to prevent division by zero. e.g. dividend / NULLIF(divisor,0))", null, node.getLine(), node.getCharPositionInLine()));
                break;
            }
            case SqlBaseParser.ESCAPE:
            {
                if (children.size() != 1)
                {
                    _parseErrors.add(new QueryParseException("ESCAPE expects simple string specification", null, node.getLine(), node.getCharPositionInLine()));
                    break;
                }
                return QNode.first(children);
            }
            case SqlBaseParser.IN:
            case SqlBaseParser.NOT_IN:
            {
                var lhs = QNode.firstOrThrow(children);
                var rhs = QNode.secondOrThrow(children);
                if (rhs.getTokenType() == SqlBaseParser.METHOD_CALL)
                {
                    // rewrite "IN EXPANCESTORS" "IN EXPDESCENDANTS"
                    var method = rhs.getFirstChild();
                    if (method.getTokenType() != SqlBaseParser.EXPANCESTORSOF && method.getTokenType() != SqlBaseParser.EXPDESCENDANTSOF && method.getTokenType() != SqlBaseParser.EXPLINEAGEOF)
                    {
                        _parseErrors.add(new QueryParseException("Illegal syntax near 'IN'", null, node.getLine(), node.getCharPositionInLine()));
                        return null;
                    }

                    var rhsChildren = rhs.childList();
                    if (rhsChildren.size() > 3)
                    {
                        _parseErrors.add(new QueryParseException(method.getTokenText().toUpperCase() + " supports at most 2 arguments", null, node.getLine(), node.getCharPositionInLine()));
                        return null;
                    }

                    var qInLineage = new QInLineage(node.getType() == SqlBaseParser.IN, method.getTokenType());
                    var qInLineageChildren = new LinkedList<QNode>();
                    qInLineageChildren.add(lhs);
                    qInLineageChildren.add(QNode.secondOrThrow(rhsChildren));
                    if (rhsChildren.size() > 2)
                        qInLineageChildren.add(QNode.childOrThrow(rhsChildren, 2));

                    qInLineage._replaceChildren(qInLineageChildren);
                    return qInLineage;
                }
            }
            case SqlBaseParser.METHOD_CALL:
            {
                @NotNull QNode id = QNode.firstOrThrow(children);
                @NotNull QNode exprList = QNode.secondOrThrow(children);

                // check for special case table method "findColumn", this isn't a real method so it's easier if it has its own node type

                if (id instanceof QDot)
                {
                    FieldKey full = ((QDot) id).getFieldKey();
                    if (full.size() == 2 && FIND_COLUMN_METHOD_NAME.equalsIgnoreCase(full.getName()))
                    {
                        var resolveMethod = new QResolveTableColumn(node);
                        resolveMethod._replaceChildren(children);
                        return resolveMethod;
                    }
                }

                if (!(id instanceof QIdentifier))
                        break;
                String name = ((QIdentifier)id).getIdentifier().toLowerCase();

                switch (name)
                {
                    case "convert", "cast" ->
                    {
                        if (!(exprList instanceof QExprList) || exprList.childList().size() != 2)
                        {
                            _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 2 arguments", null, node.getLine(), node.getCharPositionInLine()));
                            break label;
                        }
                        var valueExpression = exprList.childList().get(0);
                        QNode type = createType(exprList.childList().get(1));
                        if (null == type)
                        {
                            assert !_parseErrors.isEmpty();
                            return null;
                        }
                        exprList._replaceChildren(new LinkedList<>(List.of(valueExpression, type)));
                    }
                    case "timestampadd", "timestampdiff" ->
                    {
                        if (!(exprList instanceof QExprList) || exprList.childList().size() != 3)
                        {
                            _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 3 arguments", null, node.getLine(), node.getCharPositionInLine()));
                            break label;
                        }
                        assert exprList.childList().size() == 3;
                        LinkedList<QNode> args = new LinkedList<>();
                        args.add(constantToStringNode(exprList.childList().get(0)));
                        args.add(exprList.childList().get(1));
                        args.add(exprList.childList().get(2));
                        exprList._replaceChildren(args);
                        validateTimestampConstant(args.getFirst());
                    }
                    case "age" ->
                    {
                        if (!(exprList instanceof QExprList) || exprList.childList().size() < 2 || exprList.childList().size() > 3)
                        {
                            _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 2 or 3 arguments", null, node.getLine(), node.getCharPositionInLine()));
                            break label;
                        }
                        assert exprList.childList().size() == 2 || exprList.childList().size() == 3;
                        LinkedList<QNode> args = new LinkedList<>();
                        args.add(exprList.childList().get(0));
                        args.add(exprList.childList().get(1));
                        if (exprList.childList().size() == 3)
                            args.add(constantToStringNode(exprList.childList().get(2)));
                        exprList._replaceChildren(args);
                        if (args.size() == 3)
                            validateTimestampConstant(args.get(2));
                    }
                }

                // special case for table returning method
                var isTableResultMethod = id.getTokenType() == SqlBaseParser.EXPANCESTORSOF || id.getTokenType() == SqlBaseParser.EXPDESCENDANTSOF || id.getTokenType() == SqlBaseParser.EXPLINEAGEOF;
                if (!isTableResultMethod)
                {
                    try
                    {
                        Method m = Method.resolve(_dialect, name);
                        if (null != m)
                        {
                            m.validate(node, exprList.childList(), _parseErrors, _parseWarnings);
                        }
                    }
                    catch (IllegalArgumentException x)
                    {
                        if (failOnUnrecognizedMethodName)
                        {
                            String hint = forUnknownMethod(name, _dialect);
                            _parseErrors.add(new QueryParseException("Unknown method " + name + (null == hint ? "" : ". " + hint), null, id.getLine(), id.getColumn()));
                        }
                    }
                }
                break;
            }
            case SqlBaseParser.AGGREGATE:
            {
                if (constExpr)
                    return constError(node);
                QAggregate qAggregate = (QAggregate)qnode(node, children, false);
                if (!qAggregate.getType().dialectSupports(_dialect))
                {
                    _parseErrors.add(new QueryParseException(null != _dialect ? (_dialect.getProductName() + " does not support aggregate function " + qAggregate.getType().name()) : "Unknown SQL dialect", null, node.getLine(), node.getCharPositionInLine()));
                    return null;
                }

                if (QAggregate.GROUP_CONCAT.equalsIgnoreCase(node.getText()) || QAggregate.COUNT.equalsIgnoreCase(node.getText()))
                {
                    boolean distinct = false;

                    if (children.size() > 1 && QNode.first(children) instanceof QDistinct)
                    {
                        children.removeFirst();
                        distinct = true;
                    }

                    qAggregate.setDistinct(distinct);

                }
                return qAggregate;
            }
            case SqlBaseParser.TIMESTAMP_LITERAL:
            case SqlBaseParser.DATE_LITERAL:
            {
                String s = LabKeySql.unquoteString(QNode.firstOrThrow(children).getTokenText());
                try
                {
                    if (node.getType() == SqlBaseParser.TIMESTAMP_LITERAL)
                        return new QTimestamp(node,new Timestamp(DateUtil.parseDateTime(s)));
                    else
                        return new QDate(node,new java.sql.Date(DateUtil.parseDate(s)));
                }
                catch (ConversionException x)
                {
                    _parseErrors.add(new QueryParseException("Can't convert date literal: " + s, null, node.getLine(), node.getCharPositionInLine()));
                    return null;
                }
            }
            case SqlBaseParser.TABLE_PATH_SUBSTITUTION:
            {
                if (constExpr) return constError(node);
                if (children.size() != 3)
                {
                    _parseErrors.add(new QueryParseException("Bad escape syntax in FROM clause.", null, node.getLine(), node.getCharPositionInLine()));
                    return null;
                }
                QIdentifier type = (QIdentifier)children.get(0);
                if (!type.getIdentifier().equalsIgnoreCase("substitutePath"))
                {
                    _parseErrors.add(new QueryParseException("Unknown escape in FROM clause: " + type.getSourceText(), null, type.getLine(), type.getLine()));
                    return null;
                }
                QIdentifier fn = (QIdentifier)children.get(1);
                if (!fn.getIdentifier().equalsIgnoreCase("moduleProperty"))
                {
                    _parseErrors.add(new QueryParseException("Unknown escape function in FROM clause: " + fn.getSourceText(), null, fn.getLine(), fn.getLine()));
                    return null;
                }
                QExprList exprlist = (QExprList)children.get(2);
                List<QNode> args = exprlist.childList();
                if (args.size() != 2 || !(args.get(0) instanceof QString) || !(args.get(1) instanceof QString))
                {
                    _parseErrors.add(new QueryParseException("Expected two strings arguments to escape function: " + fn.getSourceText(), null, fn.getLine(), fn.getLine()));
                    return null;
                }
                return substituteModuleProperty(((QString) args.get(0)).getValue(), ((QString)args.get(1)).getValue());
            }
            case SqlBaseParser.QUERY:
            {
                if (constExpr) return constError(node);
                QQuery query = (QQuery)qnode(node, children, false);
                if (query.hasTransformableAggregate() && null != _dialect && _dialect.isSqlServer())
                {
                    return makeTransformedAggregateQuery(query);
                }
                return query;
            }
            case SqlBaseParser.RANGE:
            {
                if (constExpr)
                    return constError(node);
                // copy an annotations on the table specifications to the range node
                QUnknownNode range = (QUnknownNode)qnode(node, children, false);
                var annotations = ((SupportsAnnotations)node.getChild(0)).getAnnotations();
                range.setAnnotations(QNode.convertAnnotations(annotations));
                return range;
            }
            default:
                break;
        }

        return qnode(node, children, constExpr);
    }

    private QQuery makeTransformedAggregateQuery(QQuery query)
    {
        // Split Select into two selects:
        // -- inner will do percentile_cont to calculate median, but not aggregate other aggregates in select
        // -- outer aggregates other aggregates, does MAX on the result of percentile_cont, and does groupBy
        //
        // Transform
        // (Query (SelectFrom (Select (As(Agg:MEDIAN(x))) <other non-Agg As's> (As(Agg:nonMEDIAN(y))))
        //                    (From <from>))
        //        (Where <where>)
        //        (GroupBy <groupBy's>))
        //
        // (Query (SelectFrom (Select [medAlias.] (As(Agg:MAX(x))) <other non-Agg As's> (As(Agg:nonMEDIAN(y))))
        //                    (From (Range (Query (SelectFrom (Select (As(Agg:MEDIANPrime(x) (Partition <groupBy's>))
        //                                                             <other non-Agg As's> (As y))
        //                                                    (From <from>))
        //                                        (Where <where>))
        //                                 (Id medAlias))))
        //        (GroupBy [medAlias.] <groupBy>))
        QWhere where = query.getWhere();
        QGroupBy groupBy = query.getGroupBy();
        QLimit limit = query.getLimit();
        QOrder orderBy = query.getOrderBy();

        String medAlias = "medAlias_";
        Map<FieldKey, QExpr> groupByAliasMap = new HashMap<>();        // Need to replace groupBys with aliases
        QSelectFrom transformedSelectFrom = new QSelectFrom();
        transformedSelectFrom.setTokenTypeAndText(SELECT_FROM);

        QSelect selectNew = transformSelect(query.getSelect(), query.getFrom(), groupBy, transformedSelectFrom, groupByAliasMap);
        QSelectFrom selectFrom = new QSelectFrom();
        selectFrom.setTokenTypeAndText(SELECT_FROM);
        selectFrom.appendChildren(selectNew, makeTransformedFrom(transformedSelectFrom, where, medAlias));

        QQuery queryNew = new QQuery();
        queryNew.setTokenTypeAndText(QUERY);
        queryNew.appendChild(selectFrom);
        if (null != groupBy)
        {
            List<QNode> groupChildren = groupBy.childList();
            groupBy.removeChildren();
            for (QNode groupChild : groupChildren)
            {
                QExpr expr = groupByAliasMap.get(((QExpr) groupChild).getFieldKey());
                if (null != expr)
                    groupBy.appendChild(expr);
            }

            if (!groupBy.childList().isEmpty())
                queryNew.appendChild(groupBy);          // If no groupBy fields are surfaced, no need for groupBy, since they went into partitioning median
        }
        if (null != limit)
            queryNew.appendChild(limit);
        if (null != orderBy)
            queryNew.appendChild(orderBy);

        queryNew.setHasTransformableAggregate(false);      // clear since we've processed it here
        return queryNew;
    }

    private QFrom makeTransformedFrom(QSelectFrom transformedSelectFrom, QWhere qWhere, String medAlias)
    {
        QQuery qQuery = new QQuery();
        qQuery.setTokenTypeAndText(QUERY);
        qQuery.appendChild(transformedSelectFrom);
        if (null != qWhere)
            qQuery.appendChild(qWhere);
        QUnknownNode unknownNode = new QUnknownNode();
        unknownNode.setTokenTypeAndText(RANGE);
        unknownNode.appendChildren(qQuery, new QIdentifier(medAlias));
        QFrom from = new QFrom();
        from.setTokenTypeAndText(FROM);
        from.appendChild(unknownNode);
        return from;
    }

    private QSelect transformSelect(QSelect select,                                 // input Select
                                    QFrom from,                                     // input From
                                    QGroupBy groupBy,                               // input GroupBy
                                    QSelectFrom innerSelectFrom,                    // [out] inner SelectFrom to be filled in
                                    @NotNull Map<FieldKey, QExpr> groupByAliasMap)  // [out] alias map for GroupBy
    {
        if (null == _dialect)
            throw new IllegalStateException("dialect is required");
        AliasManager aliasManager = new AliasManager(_dialect);     // Need to assign unique names to selected fields for them to be used in outer select
        for (QNode child : select.children())                       // Claim existing aliases
        {
            if (child instanceof QAs as)
            {
                QIdentifier identifier = as.childList().size() > 1 ? as.getAlias() : null;
                if (null != identifier)
                    aliasManager.claimAlias(identifier.getIdentifier(), identifier.getIdentifier());
            }
        }

        if (null != groupBy)
        {
            for (QNode groupChild : groupBy.children())
            {
                // populate map keys so we can find them and put the aliases as we go thru the Select
                groupByAliasMap.put(((QExpr) groupChild).getFieldKey(), null);
            }
        }

        QSelect innerSelect = new QSelect();
        innerSelect.setTokenTypeAndText(SELECT);

        List<QNode> innerSelectNewChildren = new ArrayList<>();
        for (QNode child : select.children())
        {
            if (child instanceof QAs as)
            {
                QIdentifier identifier = as.childList().size() > 1 ? as.getAlias() : null;
                innerSelectNewChildren.addAll(transformInnerExpr(as, identifier, groupBy, aliasManager, groupByAliasMap));

            }
            else
            {   // TODO: Is this possible?  I don't think so
                innerSelectNewChildren.add(child);
            }
        }
        innerSelect.appendChildren(filterDuplicateIdentifiers(innerSelectNewChildren));
        innerSelectFrom.appendChildren(innerSelect, from);
        return select;
    }


    private List<QNode> transformInnerExpr(@NotNull QAs as,                     // Input As   [possibly modified in place]
                                           @Nullable QIdentifier identifier,    // Input identifier
                                           @Nullable QGroupBy groupBy,          // Input GroupBy
                                           @NotNull AliasManager aliasManager,  // Input AliasManager [possibly modified]
                                           @NotNull Map<FieldKey, QExpr> groupByAliasMap) // [out] alias map for GroupBy
    {
        // Retain only aggregates and leaves of all other expressions
        QExpr expr = as.getExpression();
        if (null != expr.getFieldKey() && groupByAliasMap.containsKey(expr.getFieldKey()))
            groupByAliasMap.put(expr.getFieldKey(), null != identifier ? identifier : expr);

        if (expr instanceof QAggregate aggregate)
        {

            if (QAggregate.Type.MEDIAN.equals(aggregate.getType()))
            {
                QPartitionBy partitionBy = new QPartitionBy();
                if (null != groupBy)
                {
                    for (QNode groupChild : groupBy.children())
                        partitionBy.appendChild(groupChild.copyTree());
                }

                aggregate.appendChild(partitionBy);     // Append as last child

                QAs asNew = (QAs)as.clone();
                asNew.appendChild(aggregate);
                if (null == identifier)
                    identifier = new QIdentifier(aliasManager.decideAlias("expression"));
                asNew.appendChild(identifier);

                // For outer select replace children of As with MAX
                QAggregate aggregateNew = new QAggregate();
                aggregateNew.setTokenTypeAndText(MAX);
                aggregateNew.appendChild(identifier);
                as.removeChildren();
                as.appendChildren(aggregateNew, identifier);

                return Collections.singletonList(asNew);
            }
            else
            {
                // Don't aggregate here; if Agg has only 1 arg, use the alias for its result to bubble up;
                // If > 1 arg.... TODO -- currently for SQL Server, there are none (other than group_concat)
                // For other aggregates, including group_concat, just evaluate first child and alias that
                // There can't be aggregate within aggregate, so ok not to look at subexpression
                QAs asNew = (QAs)as.clone();
                asNew.appendChild(aggregate.getFirstChild());
                if (null == identifier)
                    identifier = new QIdentifier(aliasManager.decideAlias("expression"));
                asNew.appendChild(identifier);

                // For outer select leave aggregate in place but against identifier coming from inner select
                aggregate.removeChildren();
                aggregate.appendChild(identifier);

                return Collections.singletonList(asNew);
            }
        }
        else if (expr instanceof QOperator ||
                 expr instanceof QExprList ||
                 expr instanceof QCase ||
                 expr instanceof QWhen ||
                 expr instanceof QElse)
        {
            List<QNode> list = new ArrayList<>();
            List<QNode> exprNewChildren = new ArrayList<>();
            for (QNode opChild : expr.children())
            {
                QAs asTemp = (QAs)as.clone();
                asTemp.appendChild(opChild);
                list.addAll(transformInnerExpr(asTemp, null, groupBy, aliasManager, groupByAliasMap));
                exprNewChildren.add(asTemp.getExpression());
            }
            expr.removeChildren();
            expr.appendChildren(exprNewChildren);
            return list;
        }
        else if (expr instanceof QMethodCall)
        {
            QExprList exprList = (QExprList)expr.getLastChild();

            List<QNode> list = new ArrayList<>();
            List<QNode> exprListNewChildren = new ArrayList<>();
            for (QNode opChild : exprList.children())
            {
                QAs asTemp = (QAs)as.clone();
                asTemp.appendChild(opChild);
                list.addAll(transformInnerExpr(asTemp, null, groupBy, aliasManager, groupByAliasMap));
                exprListNewChildren.add(asTemp.getExpression());
            }
            exprList.removeChildren();
            exprList.appendChildren(exprListNewChildren);
            return list;
        }
        else if (expr instanceof QIdentifier || expr.isConstant())
        {
            QAs asNew = (QAs)as.copyTree();
            if (null != identifier)
            {
                as.removeChildren();
                as.appendChild(identifier);
            }
            return Collections.singletonList(asNew);
        }
        else                  // TODO: what else can be here?
        {
            QAs asNew = (QAs)as.clone();
            asNew.appendChild(expr);
            as.removeChildren();
            if (null == identifier)
            {
                identifier = new QIdentifier(aliasManager.decideAlias("expression"));
            }
            as.appendChild(identifier);
            asNew.appendChild(identifier);
            return Collections.singletonList(asNew);
        }

    }


    List<QNode> filterDuplicateIdentifiers(List<QNode> qnodes)
    {
        Set<FieldKey> fieldKeys = new HashSet<>();
        List<QNode> outList = new ArrayList<>();
        for (QNode node : qnodes)
        {
            if (node instanceof QAs as)
            {
                if (as.getExpression() instanceof QIdentifier && as.childList().size() == 1)
                {
                    FieldKey fieldKey = as.getExpression().getFieldKey();
                    if (fieldKeys.contains(fieldKey))
                        continue;       // Leave out dup
                    fieldKeys.add(fieldKey);
                }
            }
            outList.add(node);
        }
        return outList;
    }

    private QFieldKey substituteModuleProperty(String moduleName, String propertyName)
    {
        if (StringUtils.isEmpty(moduleName) || StringUtils.isEmpty(propertyName))
        {
            _parseErrors.add(new QueryParseException("Expected two strings arguments to escape function: moduleProperty()", null, -1, -1));
            return null;
        }

        Module module = ModuleLoader.getInstance().getModule(moduleName);
        if (null == module)
        {
            _parseErrors.add(new QueryParseException("Can not resolve module: " + moduleName, null, -1, -1));
            return null;
        }

        ModuleProperty mp = module.getModuleProperties().get(propertyName);
        if (null == mp)
        {
            _parseErrors.add(new QueryParseException("Can not resolve module property: " + propertyName, null, -1, -1));
            return null;
        }

        Container cCompile = _container;
        if (null == cCompile)
            cCompile = (Container) QueryServiceImpl.get().getEnvironment(QueryService.Environment.CONTAINER);
        if (null == cCompile)
        {
            _parseErrors.add(new QueryParseException("Can not resolve moduleProperty(), container is not specified", null, -1, -1));
            return null;
        }
        String value = mp.getEffectiveValue(cCompile);
        if (StringUtils.isEmpty(value))
        {
            _parseErrors.add(new QueryParseException("Module property is empty: " + propertyName, null, -1, -1));
            return null;
        }

        return substitutePath(value);
    }


    private QFieldKey substitutePath(String pathString)
    {
        Path p = Path.parse(pathString);
        if (p.isEmpty())
        {
            _parseErrors.add(new QueryParseException("Path substition is empty", null, -1, -1));
            return null;
        }
        // NOTE the "/" forces this to be interpreted as a directory (not a schema name)
        if (!pathString.endsWith(("/")))
            pathString += "/";
        return new QIdentifier(pathString);
    }


    private QType createType(QNode qType)
    {
        String typeString;
        if (qType instanceof QString qString)
        {
            typeString = qString.getValue();
        }
        else if (qType instanceof QIdentifier qIdentifier)
        {
            typeString = qIdentifier.getIdentifier();
        }
        else
        {
            _parseErrors.add(new QueryParseException("Unexpected token", null, qType.getLine(), qType.getColumn()));
            return null;
        }
        if (typeString.startsWith("SQL_"))
            typeString = typeString.substring(4);

        QNumber qLength = null;
        QNumber qScale = null;
        if (!qType.childList().isEmpty())
        {
            qLength = (QNumber)qType.childList().get(0);
            if (qType.childList().size() > 1)
                qScale = (QNumber)qType.childList().get(1);
        }

        try
        {

            ConvertType type = ConvertType.valueOf(typeString.toUpperCase());
            switch (type)
            {
                case VARCHAR ->
                {
                    if (null != qScale)
                    {
                        _parseErrors.add(new QueryParseException("Unexpected scale modifier for '" + typeString + "'", null, qType.getLine(), qType.getColumn()));
                        return null;
                    }
                }
                case NUMERIC, DECIMAL ->
                {
                    if (null != qLength && null != qScale)
                    {
                        int length = qLength._value.intValue();
                        int scale = qScale._value.intValue();
                        if (scale > length)
                        {
                            _parseErrors.add(new QueryParseException("NUMERIC scale must be between 0 and precision=" + length, null, qScale.getLine(), qScale.getColumn()));
                            return null;
                        }
                    }
                }
                default ->
                {
                    if (null != qLength)
                    {
                        _parseErrors.add(new QueryParseException("Unexpected length modifier for '" + typeString + "'", null, qType.getLine(), qType.getColumn()));
                        return null;
                    }
                }
            }

            return new QType(
                    type,
                    null==qLength?null:qLength._value.intValue(),
                    null==qScale?null:qScale._value.intValue()
            );
        }
        catch (IllegalArgumentException x)
        {
            _parseErrors.add(new QueryParseException("Unrecognized constant '" + typeString + "'", null, qType.getLine(), qType.getColumn()));
            return null;
        }
    }


    private boolean validateTimestampConstant(QNode n)
    {
        if (!(n instanceof QString))
        {
            _parseErrors.add(new QueryParseException("constant expected", null, n.getLine(), n.getColumn()));
            return false;
        }
        String s = ((QString)n).getValue();
        if (!s.startsWith("SQL_TSI_"))
            s = "SQL_TSI_" + s;
        try
        {
            Method.TimestampDiffInterval.valueOf(s);
            return true;
        }
        catch (IllegalArgumentException x)
        {
            _parseErrors.add(new QueryParseException("Unrecognized constant '" + ((QString)n).getValue() + "'", null, n.getLine(), n.getColumn()));
            return false;
        }
    }

    private QNode constantToStringNode(QNode node)
    {
        if (node instanceof QString)
        {
            QString q = new QString();
            q.setTokenText(node.getTokenText().toUpperCase());
            q.setLineAndColumn(node);
            return q;
        }
        else if (node instanceof QIdentifier)
        {
            String s =  ((QIdentifier)node).getIdentifier();
            QString q = new QString(s.toUpperCase());
            q.setLineAndColumn(node);
            return q;
        }
        else
            return node;
    }


    public static class CaseInsensitiveStringStream extends ANTLRStringStream
    {
        public CaseInsensitiveStringStream(String s)
        {
            super(s);
        }

        @Override
        public int LA(int i)
        {
            int r = super.LA(i);
            return 'A' <= r && r <= 'Z' ? r + ('a'-'A') : r;
        }
    }


    /**
     * The default ANTLR lexer error handling prints unmatchable input to System.err and then drops it,
     * letting the remaining characters re-lex into a different, valid-looking query (e.g.
     * "{d'2001-02-03'}" -- missing the space after "{d" -- evaluated as 2001-02-03 = 1996 and swallowed
     * the rest of the statement). Collect lexer errors so they surface as parse errors instead.
     */
    private static class _SqlLexer extends SqlBaseLexer
    {
        private final ArrayList<Exception> _errors;

        _SqlLexer(CharStream input, ArrayList<Exception> errors)
        {
            super(input);
            _errors = errors;
        }

        @Override
        public void reportError(RecognitionException e)
        {
            _errors.add(e);
        }
    }


    private static class _SqlParser extends SqlBaseParser implements AutoCloseable
    {
        ArrayList<Exception> _errors;

        public _SqlParser()
        {
            super(null);
            setTreeAdaptor(new LabKeyTreeAdaptor());
            _errors = null;
            assert MemTracker.getInstance().put(this);
        }

        public _SqlParser(String str, ArrayList<Exception> errors)
        {
            this();
            reset(str, errors);
        }

        public void reset(String str, ArrayList<Exception> errors)
        {
            _errors = errors;
            setTokenStream(new CommonTokenStream(new _SqlLexer(new CaseInsensitiveStringStream(str), errors)));
        }

        @Override
        public boolean isSqlType(String type)
        {
            type = type.toUpperCase();
            if (type.startsWith("SQL_"))
                type = type.substring(4);
            try
            {
                ConvertType.valueOf(type);
                return true;
            }
            catch (IllegalArgumentException x)
            {
                return false;
            }
        }

        @Override
        public void reportError(RecognitionException ex)
        {
            _errors.add(ex);
        }

        @Override
        public void close()
        {
            _errors = null;
            setTokenStream(null);
            _parserPool.put(this);
        }
    }


    QNode qnode(CommonTree n, LinkedList<QNode> children, boolean constExpr)
    {
        QNode q = qnode(n, constExpr);
        if (null == q)
            return null;
        if (q instanceof QDot && children.isEmpty())
            return q;
        q._replaceChildren(children);
        return q;
    }


    QNode constError(CommonTree node)
    {
        _parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "' in constant expression", null, node.getLine(), node.getCharPositionInLine()));
        return null;
    }


    QNode qnode(CommonTree node, boolean constExpr)
    {
        int type = node.getType();
        QNode q;
        
        switch (type)
        {
            case AS:
                q = new QAs();
                break;
            case EXPANCESTORSOF:
            case EXPDESCENDANTSOF:
            case EXPLINEAGEOF:
            case IDENT:
            case QUOTED_IDENTIFIER:
                return QIdentifier.create(node);
            case IFDEFINED:
                if (constExpr) return constError(node);
                q = new QIfDefined(node);
                break;
            case DOT:
                // NOTE: the rule for atom is stranger (fix this), so we need toc check that the left hand side is actually an identifier
                // atom: primaryExpression ( DOT^ (identifier | starAtom) )*;
                {
                    CommonTree left = (CommonTree)node.getChild(0);
                    if (left.getType() == DOT || left.getType() == IDENT || left.getType() == QUOTED_IDENTIFIER)
                    {
                        q = new QDot();
                        break;
                    }
                    if (left.getType() == QUOTED_STRING)
                        _parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "' after string literal", null, node.getLine(), node.getCharPositionInLine()));
                    else
                        _parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getCharPositionInLine()));
                    return null;
                }
            case QUOTED_STRING:
                q = new QString();
                break;
            case TRUE:
            case FALSE:
                q = new QBoolean();
                break;
            case NUM_DOUBLE:
            case NUM_FLOAT:
            case NUM_INT:
            case NUM_LONG:
                return new QNumber(node);
            case FROM:
                q = new QFrom();
                break;
            case SELECT_FROM:
                if (constExpr) return constError(node);
                q = new QSelectFrom();
                break;
            case SELECT:
                q = new QSelect();
                break;
            case VALUES:
                q = new QValues();
                break;
            case PIVOT:
                q = new QPivot();
                break;
            case QUERY:
                if (constExpr) return constError(node);
                q = new QQuery();
                break;
            case WHERE:
                q = new QWhere();
                break;
            case HAVING:
                q = new QWhere(true);
                break;
            case METHOD_CALL:
                q = new QMethodCall();
                break;
            case AGGREGATE:
            case COUNT:
                q = new QAggregate();
                break;
            case EXPR_LIST:
            case IN_LIST:
                q = new QExprList();
                break;
            case ROW_STAR:
                q = new QRowStar();
                break;
            case GROUP:
                q = new QGroupBy();
                break;
            case ORDER:
                q = new QOrder();
                break;
            case CASE:
            case CASE2:
                q = new QCase(type==CASE2);
                break;
            case WHEN:
                q = new QWhen();
                break;
            case ELSE:
                q = new QElse();
                break;
            case NULL:
                q = new QNull();
                break;
            case LIMIT:
                q = new QLimit();
                break;
            case DISTINCT:
                q = new QDistinct();
                break;
            case EXCEPT:
            case INTERSECT:
            case UNION:
            case UNION_ALL:
                if (constExpr) return constError(node);
                return new QUnion(node);

            case ON:
            case INNER:
            case LEFT:
            case RIGHT:
            case OUTER:
            case JOIN:
            case FULL:
            case CROSS:
            case ASCENDING:
            case DESCENDING:
            case RANGE:
                return new QUnknownNode(node);

            case EXISTS: case ANY: case SOME: case ALL:
            case EQ: case NE: case GT: case LT: case GE: case LE: case IS: case IS_NOT:
            case BETWEEN: case NOT_BETWEEN:
            case PLUS: case MINUS: case UNARY_MINUS: case STAR: case DIV: case MODULO: case CONCAT:
            case NOT: case AND: case OR: case LIKE: case NOT_LIKE:
            case BIT_AND: case BIT_OR: case BIT_XOR: case UNARY_PLUS:
                Operator op = Operator.ofTokenType(type);
                if (op == null)
                {
                    _parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getCharPositionInLine()));
                    return null;
                }
                q = op.expr();
                break;
            case IN: case NOT_IN:
                CommonTree right = (CommonTree)node.getChild(1);
                if (right.getToken().getType() == METHOD_CALL)
                {
                    // We should have handled this in convertTree()
                    throw new QueryParseException("Error parsing IN expression", null, node.getLine(), node.getCharPositionInLine());
                }
                else
                {
                    q = Operator.ofTokenType(type).expr();
                }
                break;
            case DECLARATION:
                return new QUnknownNode();
            case WITH:
                return new QWith(node);
            default:
                _parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getCharPositionInLine()));
                return null;
        }

        assert q != null || !_parseErrors.isEmpty();
        
        // default behavior for nodes that don't have QNode(Node N) constructors
        if (q != null)
            q.from(node);
        return q;
    }


    // resolve method identifiers into method fields
    // just duplicating the more complete version in QuerySelect
    static QExpr resolveMethods(QExpr expr)
    {
        QIdentifier methodName = null;
        if (expr instanceof QMethodCall)
        {
            if (expr.childList().getFirst() instanceof QIdentifier)
                methodName = (QIdentifier)expr.childList().getFirst();
        }

        QExpr ret = (QExpr) expr.clone();
        for (QNode child : expr.children())
        {
            //
            if (child == methodName)
                ret.appendChild(new QField(null, methodName.getIdentifier(), child));
            else
                ret.appendChild(resolveMethods((QExpr)child));
        }
        return ret;
    }


    //
    // TESTS
    //


    
    /* UNDONE keywords
    class delete elements fetch indices insert into limit new set update versioned both empty leading member of trailing
     */
    static String[] testSql = new String[]
    {
        "SELECT 'text',1,-2,1000000L,1.0f,3.1415926535897932384626433832795,6.02214179e23,TRUE,FALSE,0x0ab12,NULL FROM R",

        "SELECT DISTINCT R.a, b AS B FROM rel R INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",
        "SELECT R.* FROM R",

        "SELECT \"a\",\"b\",AVG(x),COUNT(x),COUNT(*),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b ORDER BY a ASC, b DESC, SUM(x)",

        "SELECT a = TRUE, b = FALSE, NOT c FROM R WHERE R.x IN (2,3,5,7) OR R.x BETWEEN 100 AND 200",

        "SELECT R.a, R.a B, R.a AS C FROM R",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x LEFT OUTER JOIN T ON S.y = T.y",

        "SELECT R.a, S.\"b\" FROM R LEFT JOIN S ON R.x = S.x",

        "SELECT \"R\".a, S.b FROM R FULL JOIN S ON R.x = S.x",

        "SELECT \"R\".a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT \"R\".a, S.b FROM R, S WHERE R.x = S.x",

        "SELECT \"R\".a, S.b FROM R FULL OUTER JOIN (S INNER JOIN T ON S.y = T.y) ON R.x = S.x",

        "SELECT \"R\".a, S.b FROM (R INNER JOIN S ON R.x=S.x) FULL OUTER JOIN (T INNER JOIN U ON T.y = U.y) ON S.q=T.q WHERE R.z = U.z",

        "SELECT \"R\".a, S.b FROM R INNER JOIN S ON R.x=S.x, (T INNER JOIN U ON T.y = U.y) WHERE R.z = U.z",

        "SELECT \"R\".a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT CASE WHEN R.a=R.b THEN 'same' WHEN R.c IS NULL THEN 'different' ELSE R.c END FROM R",
        "SELECT CASE R.a WHEN 1 THEN 'one' WHEN 2 THEN 'two' ELSE 'few' END FROM R",

        "SELECT R.a FROM R WHERE R.a LIKE 'a%'",

        "SELECT MS2SearchRuns.Flag,MS2SearchRuns.Links,MS2SearchRuns.Name,MS2SearchRuns.Created,MS2SearchRuns.RunGroups FROM MS2SearchRuns",

        "SELECT CURDATE() FROM R",
        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_TIMESTAMP'), CONVERT(d, 'TIMESTAMP') FROM R",
        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_Timestamp'), CONVERT(d, 'Timestamp') FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT TIMESTAMPDIFF(SQL_TSI_SECOND,a,b), TIMESTAMPDIFF(SECOND,a,b), TIMESTAMPDIFF('SQL_TSI_DAY',a,b), TIMESTAMPDIFF('DAY',a,b) FROM R",
        "SELECT TIMESTAMPDIFF('SQL_TSI_Second',a,b), TIMESTAMPDIFF('Second',a,b), TIMESTAMPDIFF('SQL_TSI_Day',a,b), TIMESTAMPDIFF('Day',a,b) FROM R",
        "SELECT TIMESTAMPADD(SQL_TSI_SECOND,1,b), TIMESTAMPADD(SECOND,1,b), TIMESTAMPADD('SQL_TSI_DAY',1,b), TIMESTAMPADD('DAY',1,b) FROM R",

        // date/timestamp literals (JDBC escape syntax; note the space after {d and {ts is required)
        "SELECT {d '2001-02-03'} AS d, {ts '2001-02-03 04:05:06'} AS ts FROM R",

        "SELECT (SELECT value FROM S WHERE S.x=R.x) AS V FROM R",
        "SELECT R.value AS V FROM R WHERE R.y > (SELECT MAX(S.y) FROM S WHERE S.x=R.x)",
        "SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S) T ON R.z=T.z",

        "SELECT a FROM R WHERE a=b AND b<>c AND b!=c AND c>d AND d<e AND e<=f AND f>=g AND g IS NULL AND h IS NOT NULL " +
                " AND i BETWEEN 1 AND 2 AND j+k-l=-1 AND m/n=o AND p||q=r AND (NOT s OR t) AND u LIKE '%x%' AND u NOT LIKE '%xx%' " +
                " AND v IN (1,2) AND v NOT IN (3,4) AND x&y=1 AND x|y=1 AND x^y=1",

        "SELECT a FROM R UNION SELECT b FROM S",
        "SELECT a FROM R UNION ALL SELECT b FROM S",
        "(SELECT a FROM R) UNION ALL (SELECT b FROM S UNION (SELECT c FROM T)) ORDER BY a",
        "SELECT a, b FROM (SELECT a, b FROM R UNION SELECT a, b FROM S) U",

        // HAVING
        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

        // HAVING without GROUP BY
        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

        // comments
        "SELECT DISTINCT R.a, b AS B --nadlkf (*&F asdfl alsdkfj\nFROM rel R /* aldkjf (alsdf !! */ INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",

        // GROUP_CONCAT
        "SELECT a, GROUP_CONCAT(b) FROM R GROUP BY a",
        "SELECT a, GROUP_CONCAT(DISTINCT b) FROM R GROUP BY a",
        "SELECT a, GROUP_CONCAT(b, '%$') FROM R GROUP BY a",
        "SELECT a, GROUP_CONCAT(b, CHR(10)) FROM R GROUP BY a",
        "SELECT a, GROUP_CONCAT(DISTINCT b, '%$') FROM R GROUP BY a",
        "SELECT a, GROUP_CONCAT(DISTINCT b, CHR(10)) FROM R GROUP BY a",
        "SELECT GROUP_CONCAT(b) FROM R GROUP BY a",

        // nested JOINS
        "SELECT R.a, \"S\".b FROM R LEFT OUTER JOIN (S RIGHT OUTER JOIN T ON S.y = T.y) ON R.x = S.x",
        // .*
        "SELECT R.* FROM R",

        // PIVOT
        "SELECT R.a, R.b, SUM(x) sumX FROM R GROUP BY R.a, R.b PIVOT sumX BY b",
        "SELECT R.a, R.b, SUM(x) sumX FROM R GROUP BY R.a, R.b PIVOT sumX BY b IN (0,1,2)",
        "SELECT R.a, R.b, SUM(x) sumX FROM R GROUP BY R.a, R.b PIVOT sumX BY b IN (0 AS Zero,1 ONE,2 TWO)",

        // EXPANCESTORSOF
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPANCESTORSOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0)",
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPANCESTORSOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0, 2)",
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPANCESTORSOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0, 2000)",

        // EXPDESCENDANTSOF
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPDESCENDANTSOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0)",
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPDESCENDANTSOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0, -2)",
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPDESCENDANTSOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0, 2000)",

        // EXPLINEAGEOF
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPLINEAGEOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0)",
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPLINEAGEOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0, 2)",
        "SELECT M.RowId, M.Name FROM exp.Materials M WHERE M.expObject() IN EXPLINEAGEOF (SELECT DD.expObject() FROM exp.Data DD WHERE DD.RowId > 0, 2000)"
    };

    static String[] failSql = new String[]
    {
        "",
        "lutefisk",
        "SELECT R.a FROM R WHERE > 5", "SELECT R.a + AS A FROM R", "SELECT (R.a +) R.b AS A FROM R",
        "SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S)",
        "SELECT SUM(*) FROM R",
        "SELECT a, GROUP_CONCAT(b, '%$', 'STUPID') FROM R GROUP BY a",
        "SELECT a, GROUP_CONCAT() FROM R GROUP BY a",

        // lexer errors must be reported, not silently dropped (see _SqlLexer)
        // missing space after {d used to evaluate as arithmetic (2001-02-03 = 1996) and swallow the rest of the statement
        "SELECT {d'2001-02-03'} AS d FROM R",
        "SELECT {ts'2001-02-03 04:05:06'} AS ts FROM R",
        // unmatchable character used to be dropped, silently parsing as "SELECT a b FROM R"
        "SELECT a # b FROM R",
        // unterminated string literal
        "SELECT a FROM R WHERE a = 'unterminated",

        "BROKEN",
            
        // empty select list
        "SELECT FROM R",
        // missing FROM
        "SELECT R.a WHERE R.a > 5",
        // no table name
        "SELECT S.a AS lutefisk FROM",
        // no group by
        "SELECT R.a, R.b, SUM(x) sumX FROM R PIVOT sumX BY b IN (0,1,2)",
        // With within subquery
        "SELECT * FROM (WITH peeps AS (SELECT * FROM study.participant) SELECT * FROM peeps)"
    };

    // unsupported standard-SQL constructs that should fail with a targeted hint (see forSyntaxError() above):
    // sql -> expected substring of the error message
    static List<Pair<String, String>> hintSql = Arrays.asList(
        new Pair<>("SELECT a FROM R LIMIT 5 OFFSET 10", "OFFSET is not supported"),
        new Pair<>("SELECT a FROM R ORDER BY a FETCH FIRST 5 ROWS ONLY", "FETCH FIRST is not supported"),
        new Pair<>("SELECT ROW_NUMBER() OVER (ORDER BY a) FROM R", "Window functions"),
        new Pair<>("SELECT SUM(x) OVER (PARTITION BY a) FROM R", "Window functions"),
        new Pair<>("SELECT COUNT(*) FILTER (WHERE a > 0) FROM R", "FILTER is not supported"),
        new Pair<>("SELECT a FROM R WHERE a ILIKE 'x%'", "ILIKE is not supported"),
        new Pair<>("SELECT a FROM R JOIN S USING (x)", "USING is not supported"),
        new Pair<>("SELECT a FROM R ORDER BY a NULLS LAST", "NULLS FIRST/LAST is not supported"),
        new Pair<>("SELECT a::INTEGER FROM R", "CAST(expr AS TYPE)"),
        new Pair<>("SELECT SUM(DISTINCT a) FROM R", "DISTINCT is only supported inside COUNT()"),
        new Pair<>("SELECT a FROM R WHERE a IS DISTINCT FROM b", "is_distinct_from"),
        new Pair<>("SELECT a FROM R WHERE a IS NOT DISTINCT FROM b", "is_distinct_from"),
        new Pair<>("SELECT EXTRACT(YEAR FROM d) FROM R", "EXTRACT is not supported"),
        new Pair<>("SELECT d + INTERVAL '1 day' FROM R", "INTERVAL literals are not supported"),
        new Pair<>("SELECT TOP 10 a FROM R", "TOP is not supported"),
        new Pair<>("SELECT CURRENT_DATE() FROM R", "take no parentheses")
    );

    // unrecognized method names that should fail with a suggested replacement (see forUnknownMethod() above)
    static List<Pair<String, String>> methodHintSql = Arrays.asList(
        new Pair<>("SELECT POSITION('a' IN b) FROM R", "LOCATE"),
        new Pair<>("SELECT DATEDIFF('day', a, b) FROM R", "TIMESTAMPDIFF"),
        new Pair<>("SELECT ISNULL(a, b) FROM R", "IFNULL"),
        new Pair<>("SELECT DAY(a) FROM R", "DAYOFMONTH"),
        new Pair<>("SELECT STRING_AGG(a, ',') FROM R", "GROUP_CONCAT"),
        new Pair<>("SELECT TRIM(a) FROM R", "LTRIM(RTRIM")
    );

    @SuppressWarnings("JUnitMalformedDeclaration")
    public static class SqlParserTestCase extends Assert
    {
        List<Pair<String, String>> parseExprs = Arrays.asList(
            // IDENT
            new Pair<>("a", "a"),
            new Pair<>("_a", "_a"),
            new Pair<>("$a", "$a"),
            new Pair<>("$_0", "$_0"),
            // QUOTED_IDENTIFIER
            new Pair<>("\"abcd\"", "\"abcd\""),
            new Pair<>("\"ab\"\"cd\"", "\"ab\"\"cd\""),
            // QUOTED_STRING
            new Pair<>("'abcdef'", "'abcdef'"),
            new Pair<>("'abc''def'", "'abc''def'"),
            // NUM_INT
            new Pair<>("123","123"),
            new Pair<>("-123.45","(- 123.45)"),
// HEX?           new Pair("0xff","0x00ff"),
            new Pair<>("1234567890L","1234567890"),
            new Pair<>("1.2e4","12000.0"),
            // OPERATORS and precedence
            new Pair<>("a = b","(= a b)"),
            new Pair<>("a < b","(< a b)"),
            new Pair<>("a > b","(> a b)"),
            new Pair<>("a <> b","(<> a b)"),
            new Pair<>("a != b","(!= a b)"),
            new Pair<>("a <= b","(<= a b)"),
            new Pair<>("a >= b","(>= a b)"),
            new Pair<>("a || b","(|| a b)"),
            new Pair<>("a + b","(+ a b)"),
            new Pair<>("a - b","(- a b)"),
            new Pair<>("a * b","(* a b)"),
            new Pair<>("a / b","(/ a b)"),
            new Pair<>("a | b","(| a b)"),
            new Pair<>("a ^ b","(^ a b)"),
            new Pair<>("a & b","(& a b)"),
            new Pair<>("-a","(- a)"),
            new Pair<>("+a","(+ a)"),
            new Pair<>("(a)","a"),
            new Pair<>("a IN (b)","(in a (IN_LIST b))"),
            new Pair<>("a IN (b,c)","(in a (IN_LIST b c))"),
            new Pair<>("a NOT IN (b,c)","(not in a (IN_LIST b c))"),
            new Pair<>("a BETWEEN 4 and 5", "(between a 4 5)"),
            new Pair<>("a NOT BETWEEN 4 and 5", "(not between a 4 5)"),
            new Pair<>("a LIKE 'b'", "(like a 'b')"),
            new Pair<>("a NOT LIKE 'b'", "(not like a 'b')"),

            new Pair<>("'a' || ('b' + 'c')", "(|| 'a' (+ 'b' 'c'))"),
            new Pair<>("a ^ -3 & 256", "(^ a (& (- 3) 256))"),
// CONCAT           new Pair<>("a OR b AND NOT b | c = d < e || f + g * -h", "")
            new Pair<>("a OR b AND NOT c | d ^ e & f = g < h + i * -j",
                    "(OR a (AND b (NOT (= (| c (^ d (& e f))) (< g (+ h (* i (- j))))))))"),
            new Pair<>("-a * b + c < d = e & f ^ g | h AND NOT i OR j",
                    "(OR (AND (= (< (+ (* (- a) b) c) d) (| (^ (& e f) g) h)) (NOT i)) j)"),

            // identPrimary functions aggregates
            new Pair<>("a.b","(. a b)"),
            new Pair<>("a.b.fn(5)","(METHOD_CALL (. (. a b) fn) (EXPR_LIST 5))"),
            new Pair<>("CURDATE()","(METHOD_CALL CURDATE EXPR_LIST)"),
            new Pair<>("CURRENT_DATE","(METHOD_CALL CURDATE EXPR_LIST)"),
            new Pair<>("CURRENT_TIME","(METHOD_CALL CURTIME EXPR_LIST)"),
            new Pair<>("CURRENT_TIMESTAMP","(METHOD_CALL NOW EXPR_LIST)"),
            new Pair<>("LCASE('a')","(METHOD_CALL LCASE (EXPR_LIST 'a'))"),
            new Pair<>("AGE(a,b)", "(METHOD_CALL AGE (EXPR_LIST a b))"),
            new Pair<>("SUM(a+b)","(SUM (+ a b))"),
            new Pair<>("CAST(a AS VARCHAR)", "(METHOD_CALL CAST (EXPR_LIST a VARCHAR))"),

            new Pair<>("CAST(1.23 AS NUMERIC)", "(METHOD_CALL CAST (EXPR_LIST 1.23 DECIMAL))"),
            new Pair<>("CAST(1.23 AS NUMERIC(10))", "(METHOD_CALL CAST (EXPR_LIST 1.23 DECIMAL(10)))"),
            new Pair<>("CAST(1.23 AS NUMERIC(10,2))", "(METHOD_CALL CAST (EXPR_LIST 1.23 DECIMAL(10,2)))")
        );


        List<Pair<String, JdbcType>> typeExprs = Arrays.asList(
            new Pair<>("CASE 1 WHEN 1 THEN 1 ELSE 2 END", JdbcType.INTEGER),
            new Pair<>("CASE 1 WHEN 1 THEN '1' ELSE '2' END", JdbcType.VARCHAR),
            new Pair<>("CASE 'one' WHEN 1 THEN 1 ELSE 2 END", JdbcType.INTEGER),
            new Pair<>("CASE 'one' WHEN 1 THEN '1' ELSE '2' END", JdbcType.VARCHAR),
            new Pair<>("1 = 1", JdbcType.BOOLEAN),
            new Pair<>("'one' = 'two'", JdbcType.BOOLEAN),
            new Pair<>("1 = 'two'", JdbcType.BOOLEAN),
            new Pair<>("'this ' || 'that'", JdbcType.VARCHAR),
            new Pair<>("1 || ' plus ' || 2", JdbcType.VARCHAR),
            new Pair<>("1 + 2", JdbcType.INTEGER),
            new Pair<>("1.0 + 2.1", JdbcType.DECIMAL),
            new Pair<>("1 + 2.1", JdbcType.DECIMAL),
            new Pair<>("ROUND(0.0,1)", JdbcType.DOUBLE),
            new Pair<>("1 + ROUND(0.0,1)", JdbcType.DOUBLE),
            new Pair<>("CASE WHEN TRUE THEN ROUND(0.0,1) ELSE ROUND(0.0,1) END", JdbcType.DOUBLE)
        );


        List<Pair<String,String>> parseStmts = Arrays.asList(
            // joinExpression
            new Pair<>("SELECT * FROM R JOIN S ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) (RANGE S) (ON (= x y))))))"),
            new Pair<>("SELECT * FROM R LEFT JOIN S ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))))))"),
            new Pair<>("SELECT * FROM R LEFT OUTER JOIN S ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))))))"),
            new Pair<>("SELECT * FROM R LEFT OUTER JOIN S ON x=y JOIN T ON y=z", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))) (RANGE T) (ON (= y z))))))"),
            new Pair<>("SELECT * FROM (R LEFT OUTER JOIN S ON x=y) JOIN T ON y=z", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))) (RANGE T) (ON (= y z))))))"),
            new Pair<>("SELECT * FROM R LEFT OUTER JOIN (S JOIN T on y=z) ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) LEFT (JOIN (RANGE S) (RANGE T) (on (= y z))) (ON (= x y))))))"),
            // 11440
            new Pair<>("SELECT jrRuns.run_num FROM jrRuns WHERE ((SELECT max(jrRuns.run_num) FROM jrRuns) - jrRuns.run_num) < 10",
                    "(QUERY (SELECT_FROM (SELECT (ALIAS (. jrRuns run_num))) (FROM (RANGE jrRuns))) (WHERE (< (- (QUERY (SELECT_FROM (SELECT (ALIAS (max (. jrRuns run_num)))) (FROM (RANGE jrRuns)))) (. jrRuns run_num)) 10)))"),
            new Pair<>("WITH peeps AS (SELECT * FROM R) SELECT * FROM peeps",
                    "(WithQuery (WITH (AS peeps (QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (RANGE R)))))) (QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (RANGE peeps)))))"),
            new Pair<>("WITH peeps1 AS (SELECT * FROM R), peeps AS (SELECT * FROM peeps1 UNION ALL SELECT * FROM peeps WHERE (1=0)) SELECT * FROM peeps",
                    "(WithQuery (WITH (AS peeps1 (QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (RANGE R))))) (AS peeps (UNION (QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (RANGE peeps1)))) (QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (RANGE peeps))) (WHERE (= 1 0)))))) (QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (RANGE peeps)))))")
        );

        private void good(String sql)
        {
            List<QueryParseException> errors = new ArrayList<>();
            QNode q = (new SqlParser()).parseQuery(sql,errors,null);
            if (!errors.isEmpty())
                fail(errors.getFirst(), sql);
            assertNotNull(q);
        }

        private void fail(QueryParseException qpe, String sql)
        {
            Exception ex = qpe;
            if (ex.getCause() instanceof Exception)
                ex = (Exception)ex.getCause();
            fail(ex.getMessage() + "\n" + sql);
        }

        private void bad(String sql)
        {
            List<QueryParseException> errors = new ArrayList<>();
            (new SqlParser()).parseQuery(sql,errors,null);
            if (errors.isEmpty())
                fail("BAD: " + sql);
        }

        
        @Test
        public void testExprs()
        {
            for (Pair<String,String> test : parseExprs)
            {
                List<QueryParseException> errors = new ArrayList<>();
                QExpr e = new SqlParser().parseExpr(test.first,errors);
                assertTrue(test.first + " no result and no error!", null != e || !errors.isEmpty());
                assertTrue(test.first + " has parse errors", errors.isEmpty());
                assertNotNull(test.first + " did not parse", e);
                String prefix = toPrefixString(e);
                assertEquals("error parsing expression: " + test.first + "\nexpected  <<" + test.second + ">>\nfound     <<" + prefix + ">>",
                        test.second, prefix);
            }
        }

        @Test
        public void testTypes()
        {
            for (Pair<String,JdbcType> test : typeExprs)
            {
                List<QueryParseException> errors = new ArrayList<>();
                QExpr parsed = new SqlParser().parseExpr(test.first,errors);
                QExpr e = SqlParser.resolveMethods(parsed);
                assertTrue(test.first + " no result and no error!", null != e || !errors.isEmpty());
                assertTrue(test.first + " has parse errors", errors.isEmpty());
                assertNotNull(test.first + " did not parse", e);
                assertEquals(e.getJdbcType(), test.second);
            }
        }


        @Test
        public void testStmts()
        {
            for (Pair<String,String> test : parseStmts)
            {
                List<QueryParseException> errors = new ArrayList<>();
                QNode e = new SqlParser().parseQuery(test.first,errors,null);
                assertTrue(test.first + " no result and no error!", null != e || !errors.isEmpty());
                assertTrue(test.first + " has parse errors", errors.isEmpty());
                assertNotNull(test.first + " did not parse", e);
                String prefix = toPrefixString(e);
                assertEquals(test.first, test.second, prefix);
            }
        }

        @Test
        public void testSql()
        {
            long start = System.currentTimeMillis();
            for (String sql : testSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
                    good(sql);
                }
                catch (Throwable t)
                {
                    fail(t.getMessage() + "\n" + sql);
                }
            }
            for (String sql : failSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
                    bad(sql);
                }
                catch (Throwable t)
                {
                    fail(sql);
                }
            }
            long end = System.currentTimeMillis();
            _log.trace("SqlParser.testSql(): {}", DateUtil.formatDuration(end - start));
        }

        @Test
        public void testSyntaxHints()
        {
            for (Pair<String, String> test : hintSql)
            {
                List<QueryParseException> errors = new ArrayList<>();
                new SqlParser().parseQuery(test.first, errors, null);
                assertFalse("expected a parse error: " + test.first, errors.isEmpty());
                assertTrue("expected error containing <<" + test.second + ">> for: " + test.first + "\nfound: " + errors.getFirst().getMessage(),
                        errors.stream().anyMatch(e -> StringUtils.contains(e.getMessage(), test.second)));
            }
        }

        @Test
        public void testUnknownMethodHints()
        {
            for (Pair<String, String> test : methodHintSql)
            {
                List<QueryParseException> errors = new ArrayList<>();
                new SqlParser().setFailOnUnrecognizedMethodName(true).parseQuery(test.first, errors, null);
                assertFalse("expected a parse error: " + test.first, errors.isEmpty());
                assertTrue("expected error containing <<" + test.second + ">> for: " + test.first + "\nfound: " + errors.getFirst().getMessage(),
                        errors.stream().anyMatch(e -> StringUtils.contains(e.getMessage(), test.second)));
            }
        }

        @Test
        public void testDialectMethodHints()
        {
            // the suggestion should be appropriate for the current dialect and never name a database product
            SqlDialect d = CoreSchema.getInstance().getSqlDialect();
            List<QueryParseException> errors = new ArrayList<>();
            new SqlParser(d, null).setFailOnUnrecognizedMethodName(true).parseQuery("SELECT TRIM(a) FROM R", errors, null);
            assertFalse(errors.isEmpty());
            String message = errors.getFirst().getMessage();
            assertTrue(message, StringUtils.contains(message, "LTRIM(RTRIM"));
            assertEquals(message, d.isPostgreSQL(), StringUtils.contains(message, "btrim"));
            assertFalse(message, StringUtils.containsIgnoreCase(message, "postgres"));
        }

        @Test
        public void testPrecedence() throws SQLException
        {
            // ^ is higher precedence than |
            assertEquals(1, evalInt("(1^1)|1"));
            assertEquals(0, evalInt("1^(1|1)"));
            assertEquals(1, evalInt("1^1|1 "));
            assertEquals(1, evalInt("1|1^1 "));
            assertEquals(0, evalInt("0|1^1 "));

            // & is higher precedence than ^
            assertEquals(1, evalInt("(0&1)^1"));
            assertEquals(0, evalInt("0&(1^1)"));
            assertEquals(1, evalInt("0&1^1"));
            assertEquals(1, evalInt("1^0&1"));
            assertEquals(1, evalInt("1^1&0"));

            // + is higher than &
            assertEquals(2, evalInt("(1+1)&2"));
            assertEquals(1, evalInt("1+(1&2)"));
            assertEquals(2, evalInt("1+1&2"));
            assertEquals(2, evalInt("2&1+1"));
        }

        @Test
        public void testParseIdentifier()
        {
            assertEquals("\"a\"",  new SqlParser().parseIdentifier("a").toSQLString(true));
            assertEquals("\"a\"",  new SqlParser().parseIdentifier("\"a\"").toSQLString(true));
            assertEquals("\"a\".\"b\"",  new SqlParser().parseIdentifier("a.b").toSQLString(true));
            assertEquals("\"a\".\"b\"",  new SqlParser().parseIdentifier("a.\"b\"").toSQLString(true));
            assertEquals("\"a\".\"b\"",  new SqlParser().parseIdentifier("\"a\".b").toSQLString(true));
            assertEquals("\"a\".\"b\"",  new SqlParser().parseIdentifier("\"a\".\"b\"").toSQLString(true));
            assertEquals("\"a\".\"b\".\"c\"",  new SqlParser().parseIdentifier("a.\"b\".c").toSQLString(true));
        }


        QuerySchema core = null;

        private int evalInt(String expr) throws SQLException
        {
            if (null == core)
            {
                core = DefaultSchema.get(TestContext.get().getUser(), JunitUtil.getTestContainer()).getSchema("core");
            }
            try (ResultSet rs = QueryServiceImpl.get().getSelectBuilder(core, "SELECT " + expr + " AS expr").select())
            {
                rs.next();
                return ((Number)rs.getObject(1)).intValue();
            }
        }
    }


    static class LabKeyTreeAdaptor extends CommonTreeAdaptor
    {
        @Override
        public Object create(Token payload)
        {
            return new LabKeyTreeType(payload);
        }
    }

    static class LabKeyTreeType extends CommonTree implements SupportsAnnotations
    {
        LabKeyTreeType(Token payload)
        {
            super(payload);
        }

        LabKeyTreeType(CommonTree tree)
        {
            super(tree);
            if (tree instanceof SupportsAnnotations)
                _annotations = ((SupportsAnnotations)tree).getAnnotations();
        }

        @Override
        public Tree dupNode()
        {
            return new LabKeyTreeType(this);
        }

        Map<String,Object> _annotations;

        @Override
        public void setAnnotations(Map<String, Object> a)
        {
            assert null == a || null != (a = Collections.unmodifiableMap(a));
            _annotations = a;
        }

        @Override
        public @NotNull Map<String, Object> getAnnotations()
        {
            return _annotations;
        }
    }
}
