package org.sakaiproject.xythos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.VersionException;


import com.xythos.common.api.*;
import com.xythos.fileSystem.File;
import com.xythos.jcr.api.AdminCredentials;
import com.xythos.jcr.api.NoPasswordCredentials;
import com.xythos.jcr.api.RepositoryFactory;
import com.xythos.security.api.*;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.*;
import com.xythos.storageServer.permissions.api.AccessControlEntry;
import com.xythos.webdav.dasl.api.DaslResultSet;
import com.xythos.webdav.dasl.api.DaslStatement;

import edu.nyu.XythosDocument;
import edu.nyu.XythosRemote;

public class XythosRemoteImpl implements XythosRemote {
  
  private static String ADMIN = "administrator";

  public boolean ping() {
    return true;
  }

  public void createDirectory (String l_username, String l_vsName, 
      String l_homedirectory, String l_name) {                                        
    try {
      Context l_context = null;
      try {      
        VirtualServer l_virtualserver = VirtualServer.find(l_vsName);
        l_context = getUserContext(l_username, l_vsName);
        String l_ownerPrincipalID = l_context.getContextUser().getPrincipalID();

        CreateDirectoryData l_data = new CreateDirectoryData(l_virtualserver,
            l_homedirectory,
            l_name,
            l_ownerPrincipalID);

        FileSystem.createDirectory(l_data, l_context);

        l_context.commitContext();
        l_context = null;



      } finally {
        if (l_context != null) {
          l_context.rollbackContext();
          l_context = null; 
        }  
      }
    } catch (Exception l_e) {
      try {
      } catch (Exception l_e2) {
        l_e2.printStackTrace();        
      }
    }
  }
  private static Context getUserContext(String p_username, String p_virtualserver) 
  throws XythosException {
    UserBase l_user = null;  
    Context l_context = null;  
    if (p_username == null)
      throw new WFSSecurityException("The user field was empty!");
    if (p_username.equalsIgnoreCase(ADMIN)) {
      return(AdminUtil.getContextForAdmin("1.1.1.1"));
    }

    l_user = PrincipalManager.findUser(p_username, p_virtualserver);
    if (l_user == null) 
      throw new WFSSecurityException("No user found!");

    Properties l_prop = new Properties();
    l_prop.put("Xythos.Logger.IPAddress", "192.168.0.27");

    l_context = ContextFactory.create(l_user,
        l_prop);

    return l_context;  
  }
  
    public String findAllFilesForUser(String l_username) {
      List<String> permittedFiles = new ArrayList<String>();
        try {
        Context l_context = null;
          l_context = AdminUtil.getContextForAdmin("1.1.1.1");
          VirtualServer vServer = VirtualServer.getDefaultVirtualServer();
          UserBase l_principal = PrincipalManager.findUser(l_username, vServer.getName());
          String[] topLevelDirectories = FileSystem.findTopLevelDirectoryEntries(vServer, l_context);
          for (String dirName : topLevelDirectories) {
            FileSystemTopLevelDirectory topDir = FileSystem.findTopLevelDirectoryEntry(vServer, dirName, l_context);
            FileSystemDirectory dir = topDir.getFileSystemDirectory(false, l_context);
            permittedFiles.addAll(permittedFilesForEntry(l_principal.getPrincipalID(), dir));  
          }
          StringBuffer fileList = new StringBuffer();
          for (String filename : permittedFiles) {
            fileList.append(filename + ", ");
          }
          return fileList.toString();
      } catch (XythosException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return "error";
      }     
    }
    
    public Collection<Map<String,String>> findFilesWithXPath(String searchQuery, String userId) {
      Collection<Map<String,String>> rv = new ArrayList<Map<String,String>>();
      try {
      Repository repository = RepositoryFactory.newRepository(null);
        Session session = repository.login(new NoPasswordCredentials(userId));
        session.getWorkspace().getNamespaceRegistry().registerNamespace(JcrConstants.NS_SAKAIH_PREFIX, JcrConstants.NS_SAKAIH_URI);
        session.getWorkspace().getNamespaceRegistry().registerNamespace("sling", "http://sling.apache.org/jcr/sling/1.0");
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        Query query = queryManager.createQuery(searchQuery, Query.XPATH);
        Map<String, String> rowMap = null;
        QueryResult result = query.execute();
        for (Iterator<?> i = result.getNodes();i.hasNext(); ) {
          rowMap = new HashMap<String,String>();
          Node node = (Node)i.next();
          for (Iterator<?> j = node.getProperties();j.hasNext(); ) {
            javax.jcr.Property prop = (javax.jcr.Property)j.next();
            if (!prop.getDefinition().isMultiple()) {
              rowMap.put(prop.getName(), prop.getValue().getString());
            }
          }
          rowMap.put("jcr:path", node.getPath());
          rv.add(rowMap);
        }
        return rv;
    } catch (LoginException e) {
      e.printStackTrace();
      return null;
    } catch (InvalidQueryException e) {
      e.printStackTrace();
      return null;
    } catch (RepositoryException e) {
      e.printStackTrace();
      return null;
    }
    }
    
    private List<String> permittedFilesForEntry(String l_username,
        FileSystemEntry entry) {
      List<String> rv = new ArrayList<String>();
      try {
        if (userCanRead(l_username, entry)) {
          rv.add(entry.getName());
        }
        if (entry instanceof FileSystemDirectory) {
          FileSystemEntry[] contents = ((FileSystemDirectory)entry).getFindableEntries();
          for (FileSystemEntry child : contents) {
            rv.addAll(permittedFilesForEntry(l_username, child));
          }
        }
      } catch (XythosException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return rv;

    }

    private boolean userCanRead(String userPrincipalId, FileSystemEntry entry) {
      try {
        if (entry.getEntryOwnerPrincipalID().equalsIgnoreCase(userPrincipalId)) return true;
        List<String> searchPrincipals = new ArrayList<String>();
        searchPrincipals.add(userPrincipalId);
        GroupArray globalGroups = PrincipalManager.searchForGroups("*", "*", "*", PrincipalManager.PRINCIPAL_SEARCH_UNLIMITED_RESULTS, null);
        for (Group g : globalGroups.getGroups()) {
          if (g.getMembers() != null) {
            for (Principal p : g.getMembers()) {
              if (p.getPrincipalID().equalsIgnoreCase(userPrincipalId)) searchPrincipals.add(g.getPrincipalID());
            }
          }
        }
        for (String principalId : searchPrincipals) {
          AccessControlEntry ace = entry.getAccessControlEntry(principalId);
          if (ace.isReadable()) return true;
        }
        return false;
      } catch (XythosException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return false;
      }
    }

    public String saveFile(String path, String id, byte[] contentBytes, String fileName,
        String contentType, String userId) {
      if (fileName != null && !fileName.equals("")) {
        // Clean the filename.
        Repository repository;
        try {
          repository = RepositoryFactory.newRepository(null);
        Session session = null;
        session = repository.login(new NoPasswordCredentials(userId));
        session.getWorkspace().getNamespaceRegistry().registerNamespace(JcrConstants.NS_SAKAIH_PREFIX, JcrConstants.NS_SAKAIH_URI);
        session.getWorkspace().getNamespaceRegistry().registerNamespace("sling", "http://sling.apache.org/jcr/sling/1.0");
        Node fileNode = null;

        // Create or get the file.
          if ( !session.itemExists(path) ) {
            // create the node administratively, and set permissions
            Session adminSession = null;
            try {
              adminSession = repository.login(new AdminCredentials("test"));

              fileNode = JcrUtils.deepGetOrCreateNode(adminSession, path, JcrConstants.NT_FILE);
              Node content = null;
              if (fileNode.canAddMixin(JcrConstants.MIX_REFERENCEABLE)) {
                fileNode.addMixin(JcrConstants.MIX_REFERENCEABLE);
              }
              //fileNode.addMixin("sakai:propertiesmix");
              fileNode.setProperty("sling:resourceType","sakai/file");
              fileNode.setProperty("sakai:id", id);

              // Create the content node.
              content = fileNode.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
              content.setProperty(JcrConstants.JCR_DATA, new ByteArrayInputStream(contentBytes));
              content.setProperty(JcrConstants.JCR_MIMETYPE, contentType);
              content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
              // Set the person who last modified it.s
              fileNode.setProperty("sakai:user", userId);

              fileNode.setProperty("sakai:filename", fileName);
              if (adminSession.hasPendingChanges()) {
                adminSession.save();
              }
            } catch (Exception e) {
              // woah, what happened here?
              e.printStackTrace();
            } finally {
              adminSession.logout();
            }
            // Node rv = (Node) session.getItem(path);
            return "http://localhost:9090" + path;
          } else {
            fileNode = (Node) session.getItem(path);
            // This is not a new node, so we should already have a content node.
            // Just in case.. catch it
            Node content = null;
            try {
              content = fileNode.getNode(JcrConstants.JCR_CONTENT);
            } catch (PathNotFoundException pnfe) {
              content = fileNode.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
            }

            content.setProperty(JcrConstants.JCR_DATA, new ByteArrayInputStream(contentBytes));
            content.setProperty(JcrConstants.JCR_MIMETYPE, contentType);
            content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
            // Set the person who last modified it.
            // fileNode.setProperty("sakai:user", session.getUserID());

            // fileNode.setProperty("sakai:filename", fileName);
            if (session.hasPendingChanges()) {
              session.save();
            }
            return "http://localhost:9090" + path;
          }
        } catch (LoginException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (NoSuchNodeTypeException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (VersionException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (ConstraintViolationException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (LockException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (ValueFormatException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (ItemExistsException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (PathNotFoundException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (AccessDeniedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvalidItemStateException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (RepositoryException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      return null;
    }

  public Map<String, String> getProperties() {
    Map<String, String> rv = new HashMap<String, String>();
    rv.put("name", "Zachary");
    rv.put("rank", "lt. colonel");
    rv.put("serial", "464917732");
    return rv;
  }

  public XythosDocument getDocument(String path, String userId) {
    try {
      final String finalPath = path;
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File)FileSystem.getEntry(defaultVirtualServer, path, false, AdminUtil.getContextForAdmin("1.1.1.1"));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      file.getFileContent(output);
      final byte[] data = output.toByteArray();
      final String contentType = file.getFileContentType();
      final long contentLength = file.getEntrySize();
//      Repository repository = RepositoryFactory.newRepository(null);
//      Session session = repository.login(new NoPasswordCredentials(userId));
//      Node document = (Node)session.getItem(path);
//      final InputStream data = document.getProperty(JcrConstants.JCR_DATA).getStream();
//      final String mimeType = document.getProperty(JcrConstants.JCR_MIMETYPE).getString();
//      final long contentLength = new Long(data.available());
      return new XythosDocument() {

        public long getContentLength() {
          return contentLength;
        }

        public String getContentType() {
          return contentType;
        }

        public byte[] getDocumentContent() {
          return data;
        }

        public Map<String, Object> getProperties() {
          return new HashMap<String, Object>();
        }

        public String getUri() {
          return "http://localhost:9090" + finalPath;
        }

      };
    } catch (Exception e) {
      return null;
    }
  }

  public long getContentLength(String path, String userId) {
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File)FileSystem.getEntry(defaultVirtualServer, path, false, AdminUtil.getContextForAdmin("1.1.1.1"));
      return file.getEntrySize();
    } catch (Exception e) {
      return 0;
    }
  }

  public String getContentType(String path, String userId) {
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File)FileSystem.getEntry(defaultVirtualServer, path, false, AdminUtil.getContextForAdmin("1.1.1.1"));
      return file.getFileContentType();
    } catch (Exception e) {
      return null;
    }
  }

  public String getContentUri(String path, String userId) {
    try {
      return path;
    } catch (Exception e) {
      return null;
    }
  }

  public byte[] getFileContent(String path, String userId) {
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File)FileSystem.getEntry(defaultVirtualServer, path, false, AdminUtil.getContextForAdmin("1.1.1.1"));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      file.getFileContent(output);
      return output.toByteArray();
    } catch (Exception e) {
      return new byte[]{};
    }
  }

  public Map<String, Object> getFileProperties(String path, String userId) {
    Map<String, Object> rv = new HashMap<String, Object>();
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      FileSystemFile file = (FileSystemFile)FileSystem.getEntry(defaultVirtualServer, path, false, AdminUtil.getContextForAdmin("1.1.1.1"));
      rv.put("filename", file.getName().substring(file.getName().lastIndexOf("/") + 1));
      com.xythos.storageServer.properties.api.Property[] props = file.getProperties(false, AdminUtil.getContextForAdmin("1.1.1.1"));
      for(com.xythos.storageServer.properties.api.Property prop : props) {
        rv.put(prop.getName(), prop.getValue());
      }
      return rv;
    } catch (Exception e) {
      return rv;
    }
  }

  public List<String> doSearch(Map<String, Object> searchProperties, String userId) {
    List<String> rv = new ArrayList<String>();
    try {
      String queryString = (String) searchProperties.get("q");
      if (queryString == null | "*".equals(queryString) || "".equals(queryString)) {
    	  queryString = "";
      } else {
    	  // '%' has a special meaning in a query string, so we'll escape it
    	  queryString = queryString.replaceAll("%", "\\%");
    	  queryString = queryString + "%";
      }
      String dasl = "<d:searchrequest xmlns:d=\"DAV:\">"
+                    "  <d:basicsearch>"
+                    "    <d:select>"
+                    "      <d:prop><d:displayname/></d:prop>"
+                    "    </d:select>"
+                    "    <d:from>"
+                    "      <d:scope>"
+                    "        <d:href>http://localhost:8080/"+userId+"</d:href>"
+                    "        <d:depth>infinity</d:depth>"
+                    "      </d:scope>"
+                    "    </d:from>"
+                    "    <d:where>"
+                    "      <d:like caseless=\"yes\">" 
+                    "        <d:prop><d:displayname/></d:prop>"
+                    "        <d:literal><![CDATA[%"+queryString+"]]></d:literal>"
+                    "      </d:like>"
+                    "    </d:where>"
+                    "    <d:orderby>"
+                    "      <d:order>"
+                    "        <d:prop><d:displayname/></d:prop>"
+                    "        <d:ascending/>"
+                    "      </d:order>"
+                    "    </d:orderby>"
+                    "  </d:basicsearch>"
+                    "</d:searchrequest>";
      VirtualServer server = VirtualServer.getDefaultVirtualServer();
      Context ctx = getUserContext(userId, server.getName());
      DaslStatement statement = new DaslStatement(dasl, ctx, true);
      DaslResultSet result = statement.executeDaslQuery();
      while (result.nextEntry()) {
    	  FileSystemEntry e = result.getCurrentEntry();
    	  if ( e instanceof File ) {
    		  rv.add(e.getName());
    	  }
      }
      return rv;
    } catch (XythosException e) {
		e.printStackTrace();
		return null;
	}
  }

  public void updateFile(String path, byte[] fileData,
      Map<String, Object> properties, String userId) {
    Session session = null;
    try {
      Repository repository = RepositoryFactory.newRepository(null);
        session = repository.login(new NoPasswordCredentials(userId));
        if (session.itemExists(path)) {
          Node fileNode = (Node) session.getItem(path);
          fileNode.setProperty("jcr:data", new ByteArrayInputStream(fileData));
          fileNode.save();
        } else {
          // get the parent node
          Node parent = (Node)session.getItem(path.substring(0, path.lastIndexOf("/")));
          // add file node
          Node file = parent.addNode(path.substring(path.lastIndexOf("/") + 1), "nt:file"); 
          // add jcr:content child node
          Node content = file.addNode("jcr:content", "nt:resource"); 
          content.setProperty("jcr:data", new ByteArrayInputStream(fileData));
          if (properties.containsKey("contentType")) {
            content.setProperty("jcr:mimeType", (String)properties.get("contentType"));
          }
          parent.save();
        }
    } catch (ValueFormatException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (VersionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (LockException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ConstraintViolationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (RepositoryException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      if (session != null){
        session.logout();
      }
    }
    
  }
}