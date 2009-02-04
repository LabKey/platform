package org.labkey.query;

import org.labkey.api.webdav.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.Container;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.jetbrains.annotations.NotNull;
import org.apache.commons.lang.StringUtils;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 4, 2009
 * Time: 11:17:57 AM
 */
public class QueryWebdavprovider implements WebdavService.Provider
{
	final String QUERY_NAME = "@query";

	public Set<String> addChildren(@NotNull WebdavResolver.Resource target)
	{
		if (target instanceof WebdavResolverImpl.WebFolderResource)
			return PageFlowUtil.set(QUERY_NAME);
		return null;
	}

	public WebdavResolver.Resource resolve(@NotNull WebdavResolver.Resource parent, @NotNull String name)
	{
		if (!QUERY_NAME.equalsIgnoreCase(name))
			return null;
		if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
			return null;
		if (parent.getPath().equals("/"))
			return null;
		WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
		return new QueryResource(folder);
	}


	class QueryResource extends AbstractCollectionResource
	{
		Container _c;
		ArrayList<String> _schemaNames = null;

		QueryResource(WebdavResolverImpl.WebFolderResource parent)
		{
			super(parent.getPath(), QUERY_NAME);
			_c = parent.getContainer();
			_acl = _c.getAcl();
		}
		
		public boolean exists()
		{
			return true;
		}

		public WebdavResolver.Resource find(String name)
		{
			DefaultSchema folderSchema = DefaultSchema.get(null, _c);
			QuerySchema s  = folderSchema.getSchema(name);
			if (null != s && s instanceof UserSchema)
				return new SchemaResource(this, ((UserSchema)s).getSchemaName());
			return null;
		}

		public List<String> listNames()
		{
			if (_schemaNames == null)
			{
				DefaultSchema folderSchema = DefaultSchema.get(null, _c);
				ArrayList<String> names = new ArrayList<String>();
				Set<String> s = folderSchema.getUserSchemaNames();
				if (null != s)
					names.addAll(s);
				_schemaNames = names;
			}
			return _schemaNames;
		}

		public long getCreation()
		{
			return 0;
		}

		public long getLastModified()
		{
			return 0;
		}
	}


	class SchemaResource extends AbstractCollectionResource
	{
		QueryResource _parent;
		
		SchemaResource(QueryResource parent, String schemaName)
		{
			super(parent.getPath(), schemaName);
			_parent = parent;
			_acl = _parent._c.getAcl();
		}
		
		public boolean exists()
		{
			return true;
		}

		public WebdavResolver.Resource find(String name)
		{
			if (!name.endsWith(".sql"))
				return null;
			name = name.substring(0,name.length()-".sql".length());
			QueryDefinition q = QueryService.get().getQueryDef(_parent._c, getName(), name);
			if (null == q)
				return null;
			return new SqlResource(this, q);
		}

		public List<String> listNames()
		{
			Map<String, QueryDefinition> m = QueryService.get().getQueryDefs(_parent._c, getName());
			ArrayList<String> list = new ArrayList<String>(m.size());
			for (String name : m.keySet())
				list.add(name + ".sql");
			return list;
		}

		public long getCreation()
		{
			return 0;
		}

		public long getLastModified()
		{
			return 0; 
		}
	}


	class SqlResource extends AbstractDocumentResource
	{
		SchemaResource _parent;
		QueryDefinition _q;

		SqlResource(SchemaResource parent, QueryDefinition query)
		{
			super(parent.getPath(), query.getName() + ".sql");
			_parent = parent;
			_acl = _parent._parent._c.getAcl();
			_q = query;
		}

		public boolean exists()
		{
			return true;
		}

		public long getCreation()
		{
			return 0;	// TODO
		}

		public long getLastModified()
		{
			return 0;	// TODO
		}

		@Override
		public String getETag()
		{
			// TODO since getLastModified() is NYI
			String sql = StringUtils.trimToEmpty(_q.getSql());
			return "W/\"" + sql.length() + "-" + sql.hashCode() + "\"";
		}

		public InputStream getInputStream(User user) throws IOException
		{
			String sql = StringUtils.trimToEmpty(_q.getSql());
			return new ByteArrayInputStream(sql.getBytes("UTF-8"));
		}

		public long copyFrom(User user, InputStream in) throws IOException
		{
			String sql = PageFlowUtil.getStreamContentsAsString(in);
			_q.setSql(sql);
			try
			{
				_q.save(user, _q.getContainer());
			}
			catch (SQLException sqlx)
			{
				throw new IOException(sqlx);
			}
			return getContentLength();
		}

		public long getContentLength() throws IOException
		{
			String sql = StringUtils.trimToEmpty(_q.getSql());
			return sql.getBytes("UTF-8").length;
		}

		@Override
		public boolean canRead(User user)
		{
			return super.canRead(user);
		}

		@Override
		public boolean canWrite(User user)
		{
			return super.canWrite(user);
		}

		@Override
		public boolean canRename(User user)
		{
			return false;
		}

		@Override
		public boolean canCreate(User user)
		{
			return false;
		}

		@Override
		public boolean canDelete(User user)
		{
			return false;
		}
	}
}
