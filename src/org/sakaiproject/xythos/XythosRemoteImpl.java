package org.sakaiproject.xythos;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
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

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.IdentifyCmd;
import org.im4java.process.ArrayListOutputConsumer;
import org.im4java.process.Pipe;
import org.im4java.process.ProcessStarter;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.nyu.XythosRemote;

public class XythosRemoteImpl implements XythosRemote {
  
  Logger log = LoggerFactory.getLogger(XythosRemoteImpl.class);

  private static String ADMIN = "administrator";
  private static final List<String> DIMENSIONABLE_MIME_TYPES = Arrays.asList(new String[] {"image/jpeg","image/jpg","image/png","image/gif","image/tiff","image/bmp"});

  private static final int MAX_THUMB_HEIGHT = 150;

  private static final int MAX_THUMB_WIDTH = 150;
  
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

  public boolean ping() {
    return true;
  }

  private void addBookmark(String userId, String path, String displayName)
      throws XythosException {
    Context context = null;
    try {
      VirtualServer defaultServer = VirtualServer.getDefaultVirtualServer();
      context = getUserContext(userId, defaultServer.getName());
      UserBase user = PrincipalManager.findUser(userId, defaultServer.getName());
      BookmarkManager.addBookmark(user, defaultServer, path, displayName, context);
      context.commitContext();
      context = null;
    } finally {
      if (context != null) {
        context.rollbackContext();
        context = null;
      }
    }
  }
  
  private void createUserHomeDirectory(String userId) {
    try {
      Context context = null;
      String homeDirectoryPath = "/" + userId;
      try {
        VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
        context = getUserContext(userId, defaultVirtualServer.getName());
        String ownerPrincipalID = context.getContextUser().getPrincipalID();
        String documentStoreName = Parameters.getNewUserDocumentStore();

        CreateTopLevelDirectoryData directoryData = new CreateTopLevelDirectoryData(defaultVirtualServer,
            homeDirectoryPath,
            documentStoreName,
            ownerPrincipalID);

        FileSystem.createTopLevelDirectory(directoryData, context);

        context.getContextUser().setHomeDirectory(homeDirectoryPath, defaultVirtualServer);

        context.commitContext();
        context = null;
      } finally {
        if (context != null) {
          context.rollbackContext();
          context = null;
        }
      }
    } catch (XythosException e) {
      e.printStackTrace();
    }
  }

  public void createDirectory(String l_username, String l_vsName, String l_homedirectory,
      String l_name) {
    try {
      Context l_context = null;
      try {
        VirtualServer l_virtualserver = VirtualServer.find(l_vsName);
        l_context = getUserContext(l_username, l_vsName);
        String l_ownerPrincipalID = l_context.getContextUser().getPrincipalID();

        CreateDirectoryData l_data = new CreateDirectoryData(l_virtualserver,
            l_homedirectory, l_name, l_ownerPrincipalID);

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
      return (AdminUtil.getContextForAdmin("1.1.1.1"));
    }

    l_user = PrincipalManager.findUser(p_username, p_virtualserver);
    if (l_user == null)
      throw new WFSSecurityException("No user found!");

    Properties l_prop = new Properties();
    l_prop.put("Xythos.Logger.IPAddress", "192.168.0.27");

    l_context = ContextFactory.create(l_user, l_prop);

    return l_context;
  }

  public String findAllFilesForUser(String l_username) {
    List<String> permittedFiles = new ArrayList<String>();
    try {
      Context l_context = null;
      l_context = AdminUtil.getContextForAdmin("1.1.1.1");
      VirtualServer vServer = VirtualServer.getDefaultVirtualServer();
      UserBase l_principal = PrincipalManager.findUser(l_username, vServer.getName());
      String[] topLevelDirectories = FileSystem.findTopLevelDirectoryEntries(vServer,
          l_context);
      for (String dirName : topLevelDirectories) {
        FileSystemTopLevelDirectory topDir = FileSystem.findTopLevelDirectoryEntry(
            vServer, dirName, l_context);
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

  public Collection<Map<String, String>> findFilesWithXPath(String searchQuery,
      String userId) {
    Collection<Map<String, String>> rv = new ArrayList<Map<String, String>>();
    try {
      Repository repository = RepositoryFactory.newRepository(null);
      Session session = repository.login(new NoPasswordCredentials(userId));
      session.getWorkspace().getNamespaceRegistry().registerNamespace(
          JcrConstants.NS_SAKAIH_PREFIX, JcrConstants.NS_SAKAIH_URI);
      session.getWorkspace().getNamespaceRegistry().registerNamespace("sling",
          "http://sling.apache.org/jcr/sling/1.0");
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(searchQuery, Query.XPATH);
      Map<String, String> rowMap = null;
      QueryResult result = query.execute();
      for (Iterator<?> i = result.getNodes(); i.hasNext();) {
        rowMap = new HashMap<String, String>();
        Node node = (Node) i.next();
        for (Iterator<?> j = node.getProperties(); j.hasNext();) {
          javax.jcr.Property prop = (javax.jcr.Property) j.next();
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

  private List<String> permittedFilesForEntry(String l_username, FileSystemEntry entry) {
    List<String> rv = new ArrayList<String>();
    try {
      if (userCanRead(l_username, entry)) {
        rv.add(entry.getName());
      }
      if (entry instanceof FileSystemDirectory) {
        FileSystemEntry[] contents = ((FileSystemDirectory) entry).getFindableEntries();
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
      if (entry.getEntryOwnerPrincipalID().equalsIgnoreCase(userPrincipalId))
        return true;
      List<String> searchPrincipals = new ArrayList<String>();
      searchPrincipals.add(userPrincipalId);
      GroupArray globalGroups = PrincipalManager.searchForGroups("*", "*", "*",
          PrincipalManager.PRINCIPAL_SEARCH_UNLIMITED_RESULTS, null);
      for (Group g : globalGroups.getGroups()) {
        if (g.getMembers() != null) {
          for (Principal p : g.getMembers()) {
            if (p.getPrincipalID().equalsIgnoreCase(userPrincipalId))
              searchPrincipals.add(g.getPrincipalID());
          }
        }
      }
      for (String principalId : searchPrincipals) {
        AccessControlEntry ace = entry.getAccessControlEntry(principalId);
        if (ace.isReadable())
          return true;
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
        session.getWorkspace().getNamespaceRegistry().registerNamespace(
            JcrConstants.NS_SAKAIH_PREFIX, JcrConstants.NS_SAKAIH_URI);
        session.getWorkspace().getNamespaceRegistry().registerNamespace("sling",
            "http://sling.apache.org/jcr/sling/1.0");
        Node fileNode = null;
        
        // check for their home directory first.
        if(!session.itemExists("/" + userId)) {
          log.info("Home directory didn't exist saving file " + path + ". Creating home directory.");
          createUserHomeDirectory(userId);
        }

        // Create or get the file.
        if (!session.itemExists(path)) {
          // create the node administratively, and set permissions
          Session adminSession = null;
          try {
            adminSession = repository.login(new AdminCredentials("test"));

            fileNode = JcrUtils.deepGetOrCreateNode(adminSession, path,
                JcrConstants.NT_FILE);
            Node content = null;
            if (fileNode.canAddMixin(JcrConstants.MIX_REFERENCEABLE)) {
              fileNode.addMixin(JcrConstants.MIX_REFERENCEABLE);
            }
            // fileNode.addMixin("sakai:propertiesmix");
            fileNode.setProperty("sling:resourceType", "sakai/file");
            fileNode.setProperty("sakai:id", id);

            // Create the content node.
            content = fileNode
                .addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
            content.setProperty(JcrConstants.JCR_DATA, new ByteArrayInputStream(
                contentBytes));
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
            content = fileNode
                .addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
          }

          content.setProperty(JcrConstants.JCR_DATA, new ByteArrayInputStream(
              contentBytes));
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

  public Map<String, Object> getDocument(String path, String userId) {
    try {
      if (log.isDebugEnabled()) log.debug("getDocument called with path: " + path);
      if (path.startsWith("/thumbs/")) {
        return getThumbnailDocument(path, userId);
      }
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      
      File file = (File) FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(path, "utf-8"), false,
          getUserContext(userId, defaultVirtualServer.getName()));
      final String contentType = file.getFileContentType();
      final long contentLength = file.getEntrySize();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      file.getFileContent(output);
      Map<String, Object> entry = new HashMap<String, Object>();
      entry.put("documentContent", null);
      entry.put("contentType", contentType);
      entry.put("contentLength", contentLength);
      Map<String, Object> props = new HashMap<String, Object>();
      if (fileHasDimensions(contentType)) {
        props.put("thumbnailUri", "/thumbs" + file.getName());
        readFileDimensionsIntoProperties(output.toByteArray(), props);
      }

      props
          .put("filename", file.getName().substring(file.getName().lastIndexOf("/") + 1));
      props.put("lastmodified", dateFormat.format(file.getLastUpdateTimestamp()));
      entry.put("properties", props);
      entry.put("uri", file.getName());
      return entry;
    } catch (Exception e) {
      return null;
    }

  }

  private Map<String, Object> getThumbnailDocument(String path, String userId) throws Exception {
    try {
      if (log.isDebugEnabled()) log.debug("getThumbnailDocument called with path " + path);
      Context adminContext = null;
      try {
        VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
        adminContext = AdminUtil.getContextForAdmin("127.0.0.1");
        FileSystemFile file = (File) FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(path, "utf-8"), false, adminContext);
        if (file == null) {
          if (log.isDebugEnabled()) log.debug("thumbnail not found for path " + path + " so creating new thumbnail.");
          // first request for this thumbnail
          // get the original file
          String originalPath = path.replaceFirst("/thumbs", "");
          if (log.isDebugEnabled()) log.debug("retrieving original file for thumbnail: " + originalPath);
          File originalFile = (File) FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(originalPath, "utf-8"), false, adminContext);
          if (originalFile != null) {
            if (log.isDebugEnabled()) log.debug("retrieved " + originalFile.getEntrySize() + " bytes of original file for thumbnail.");
          }
          String parent = URLDecoder.decode(path.substring(0,path.lastIndexOf("/")), "utf-8");
          String name = URLDecoder.decode(path.substring(path.lastIndexOf("/")+1), "utf-8");
          ByteArrayOutputStream original = new ByteArrayOutputStream();
          originalFile.getFileContent(original);
          ByteArrayOutputStream thumbnail = new ByteArrayOutputStream();
          if (log.isDebugEnabled()) log.debug("attempting to use ThumbnailGenerator");
          ThumbnailGenerator.transform(new ByteArrayInputStream(original.toByteArray()), MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT, thumbnail);
          if (log.isDebugEnabled()) log.debug("ThumbnailGenerator produced image of size " + thumbnail.size());
          createDirectoriesAlongPath(parent);
          if (log.isDebugEnabled()) log.debug("attempting to create new file called " + name + " located at " + parent);
          CreateFileData createFileData = new CreateFileData(defaultVirtualServer, parent, name, "image/jpeg", adminContext.getContextUser().getPrincipalID(),new ByteArrayInputStream(thumbnail.toByteArray()));
          try {
            file = FileSystem.createFile(createFileData, adminContext);
          } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("failed to create a new file. Exception is " + e.getClass().getCanonicalName());
            return null;
          }
          adminContext.commitContext();
          if (log.isDebugEnabled()) log.debug("context committed for thumbnail create operation");
        }
        adminContext = null;
        Map<String, Object> entry = new HashMap<String, Object>();
        entry.put("documentContent", null);
        entry.put("contentType", "image/jpeg");
        entry.put("contentLength", file.getEntrySize());
        Map<String, Object> props = new HashMap<String, Object>();
        props
            .put("filename", file.getName().substring(file.getName().lastIndexOf("/") + 1));
        props.put("lastmodified", dateFormat.format(file.getLastUpdateTimestamp()));
        entry.put("properties", props);
        entry.put("uri", file.getName());
        return entry;
      } finally {
        if (adminContext != null) {
          if (log.isDebugEnabled()) log.debug("failed to create a thumbnail image. rolling back context.");
          adminContext.rollbackContext();
          adminContext = null;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void readFileDimensionsIntoProperties(byte[] fileData,
      Map<String, Object> props) throws IOException, InterruptedException,
      IM4JavaException {
    ByteArrayInputStream input = new ByteArrayInputStream(fileData);
    Image image = javax.imageio.ImageIO.read(input);
    int imageHeight = image.getHeight(null);
    int imageWidth = image.getWidth(null);
    int[] thumbDims = ThumbnailGenerator.getThumbnailDimensions(imageWidth, imageHeight, MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT);
    props.put("thumbWidth", thumbDims[0]);
    props.put("thumbHeight", thumbDims[1]);
    props.put("width", image.getWidth(null));
    props.put("height", image.getHeight(null));
  }

  private boolean fileHasDimensions(String contentType) {
    return DIMENSIONABLE_MIME_TYPES.contains(contentType);
  }

  public long getContentLength(String path, String userId) {
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File) FileSystem.getEntry(defaultVirtualServer, path, false,
          getUserContext(userId, defaultVirtualServer.getName()));
      return file.getEntrySize();
    } catch (Exception e) {
      return 0;
    }
  }

  public String getContentType(String path, String userId) {
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File) FileSystem.getEntry(defaultVirtualServer, path, false,
          getUserContext(userId, defaultVirtualServer.getName()));
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
      if (path.startsWith("/thumbs/")) {
        if (log.isDebugEnabled()) log.debug("the contents of a thumbnail have been requested: " + path);
        return getImageThumb(path, userId);
      }
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      File file = (File) FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(path, "utf-8"), false,
          getUserContext(userId, defaultVirtualServer.getName()));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      file.getFileContent(output);
      return output.toByteArray();
    } catch (Exception e) {
      return null;
    }
  }
  
  private byte[] getImageThumb(String path, String userId) {
    try {
      String thumbnailPath = path;
      if (! path.startsWith("/thumbs/")) {
        thumbnailPath = "/thumbs" + path;
      } else {
        path = path.replaceFirst("/thumbs", "");
      }
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      // first get the original
      if (log.isDebugEnabled()) log.debug("retrieving the original for this thumbnail: " + path);
      File file = (File) FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(path, "utf-8"), false,
          getUserContext(userId, defaultVirtualServer.getName()));
      if (file == null) {
        if (log.isDebugEnabled()) log.debug("original file not found: " + path);
        return null;
      } else {
        ByteArrayOutputStream rv = new ByteArrayOutputStream();
        // look for a thumbnail
        File thumbFile = (File) FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(thumbnailPath, "utf-8"), false,
            AdminUtil.getContextForAdmin("127.0.0.1"));
        if (thumbFile == null) {
          Context adminContext = null;
          try {
            // thumbnail does not exist, generate one
            adminContext = AdminUtil.getContextForAdmin("127.0.0.1");
            String parent = thumbnailPath.substring(0,thumbnailPath.lastIndexOf("/"));
            String name = thumbnailPath.substring(thumbnailPath.lastIndexOf("/")+1);
            ByteArrayOutputStream original = new ByteArrayOutputStream();
            file.getFileContent(original);
            ThumbnailGenerator.transform(new ByteArrayInputStream(original.toByteArray()), MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT, rv);
            createDirectoriesAlongPath(parent);
            CreateFileData createFileData = new CreateFileData(defaultVirtualServer, parent, name, "image/jpeg", adminContext.getContextUser().getPrincipalID(),new ByteArrayInputStream(rv.toByteArray()));
            FileSystem.createFile(createFileData, adminContext);
            adminContext.commitContext();
            if (log.isDebugEnabled()) log.debug("getImageThumb successfully created new thumbnail " + parent + "/" + name);
            adminContext = null;
          } finally {
            if (adminContext != null) {
              adminContext.rollbackContext();
              adminContext = null;
            }
          }
        } else {
          if (log.isDebugEnabled()) log.debug("thumbnail image successfully retrieved from " + thumbnailPath);
          // thumbnail already exists, use it
          thumbFile.getFileContent(rv);
        }
        return rv.toByteArray();
      }
    } catch (Exception e) {
      if (log.isDebugEnabled()) log.debug("getImageThumb exception " + e.getClass().getCanonicalName());
      throw new RuntimeException(e);
    }
  }
  
  private void createDirectoriesAlongPath(String path) {
    if (log.isDebugEnabled()) log.debug("createDirectoriesAlongPath called for path: " + path);
    try {
      Context adminContext = null;
      try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      adminContext = AdminUtil.getContextForAdmin("127.0.0.1");
      // if the whole path exists already, we don't need to do anything
      if (FileSystem.getEntry(defaultVirtualServer, path, false, adminContext) != null) return;
      
      String[] pathSegments = path.split("/");
      StringBuffer existingPath = new StringBuffer();
      existingPath.append("/");
      FileSystemEntry directory = null;
      for (String pathSegment : pathSegments) {
        if ("".equals(pathSegment)) continue;
        existingPath.append(pathSegment);
        directory = FileSystem.getEntry(defaultVirtualServer, existingPath.toString(), false, adminContext);
        if (directory == null) {
          if (log.isDebugEnabled()) log.debug("path doesn't exist, creating it: " + existingPath.toString());
          String parent = existingPath.toString().substring(0, existingPath.toString().lastIndexOf("/"));
          CreateDirectoryData directoryData = new CreateDirectoryData(defaultVirtualServer, parent, pathSegment, adminContext.getContextUser().getPrincipalID());
          FileSystem.createDirectory(directoryData, adminContext);
        }
        existingPath.append("/");
      }
      adminContext.commitContext();
      adminContext = null;
      }finally {
        if (adminContext != null) {
          adminContext.rollbackContext();
          adminContext = null;
        }
      }
    } catch (Exception e) {
      log.error("createDirectoriesAlongPath exception: " + e.getMessage());
    }
  }

  public Map<String, Object> getFileProperties(String path, String userId) {
    Map<String, Object> rv = new HashMap<String, Object>();
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      FileSystemFile file = (FileSystemFile) FileSystem.getEntry(defaultVirtualServer,
          path, false, getUserContext(userId, defaultVirtualServer.getName()));
      rv.put("filename", file.getName().substring(file.getName().lastIndexOf("/") + 1));
      com.xythos.storageServer.properties.api.Property[] props = file.getProperties(
          false, getUserContext(userId, defaultVirtualServer.getName()));
      for (com.xythos.storageServer.properties.api.Property prop : props) {
        rv.put(prop.getName(), prop.getValue());
      }
      return rv;
    } catch (Exception e) {
      return rv;
    }
  }

  public List<Map<String, Object>> doSearch(Map<String, Object> searchProperties,
      String userId) {
    List<Map<String, Object>> rv = new ArrayList<Map<String, Object>>();
    try {
      String queryString = (String) searchProperties.get("q");
      if (queryString == null | "*".equals(queryString) || "".equals(queryString)) {
        queryString = "";
      } else {
        // '%' has a special meaning in a query string, so we'll escape it
        queryString = queryString.replaceAll("%", "\\%");
        queryString = queryString + "%";
        // we'll translate the * wildcard to ours, which is %
        queryString = queryString.replaceAll("\\*", "%");
      }
      String dasl = "<d:searchrequest xmlns:d=\"DAV:\">" + "  <d:basicsearch>"
          + "    <d:select>" + "      <d:prop><d:displayname/></d:prop>"
          + "    </d:select>" + "    <d:from>" + "      <d:scope>"
          + "        <d:href>http://localhost:8080/" + userId + "</d:href>"
          + "        <d:depth>infinity</d:depth>" + "      </d:scope>" + "    </d:from>"
          + "    <d:where>" + "      <d:like caseless=\"yes\">"
          + "        <d:prop><d:displayname/></d:prop>" + "        <d:literal><![CDATA[%"
          + queryString + "]]></d:literal>" + "      </d:like>" + "    </d:where>"
          + "    <d:orderby>" + "      <d:order>"
          + "        <d:prop><d:displayname/></d:prop>" + "        <d:ascending/>"
          + "      </d:order>" + "    </d:orderby>" + "  </d:basicsearch>"
          + "</d:searchrequest>";
      VirtualServer server = VirtualServer.getDefaultVirtualServer();
      Context ctx = getUserContext(userId, server.getName());
      // if home directory doesn't exist, create it
      FileSystemEntry homeDirectory = FileSystem.getEntry(server, "/"+userId, false, ctx);
      if ((homeDirectory == null) || !(homeDirectory instanceof FileSystemDirectory)) {
        log.info("During search of /" + userId + ", directory didn't exist, so creating it.");
        createUserHomeDirectory(userId);
        // search results will necessarily be empty because we have newly created the home dir
        return rv;
      }
      DaslStatement statement = new DaslStatement(dasl, ctx, true);
      DaslResultSet result = statement.executeDaslQuery();
      while (result.nextEntry()) {
        FileSystemEntry e = result.getCurrentEntry();
        if (e instanceof File) {
          String[] pathStems = e.getName().split("/");
          if (pathStems.length > 2 && pathStems[2].equals("trash")) {
            continue;
          }
          Map<String, Object> entry = new HashMap<String, Object>();
          entry.put("documentContent", null);
          entry.put("contentType", e.getFileContentType());
          entry.put("contentLength", e.getEntrySize());
          Map<String, Object> props = new HashMap<String, Object>();
          if (fileHasDimensions(e.getFileContentType())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ((File)e).getFileContent(out);
            props.put("thumbnailUri", "/thumbs" + e.getName());
            readFileDimensionsIntoProperties(out.toByteArray(), props);
          }
          props.put("filename", e.getName().substring(e.getName().lastIndexOf("/") + 1));
          props.put("lastmodified", dateFormat.format(e.getLastUpdateTimestamp()));
          entry.put("properties", props);
          entry.put("uri", e.getName());
          rv.add(entry);
        }
      }
      return rv;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public void updateFile(String path, byte[] fileData, Map<String, Object> properties,
      String userId) {
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
        Node parent = (Node) session.getItem(path.substring(0, path.lastIndexOf("/")));
        // add file node
        Node file = parent.addNode(path.substring(path.lastIndexOf("/") + 1), "nt:file");
        // add jcr:content child node
        Node content = file.addNode("jcr:content", "nt:resource");
        content.setProperty("jcr:data", new ByteArrayInputStream(fileData));
        if (properties.containsKey("contentType")) {
          content.setProperty("jcr:mimeType", (String) properties.get("contentType"));
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
      if (session != null) {
        session.logout();
      }
    }

  }

  public void toggleMember(String groupId, String userId) {
    Context context = null;
    try {
      context = AdminUtil.getContextForAdmin("1.1.1.1");
      String location = VirtualServer.getDefaultVirtualServer().getName();
      GroupArray groups = PrincipalManager.searchForGroups(groupId, "*", location, 1,
          context);
      if (groups == null) {
        return;
      }
      Group[] groupsArray = groups.getGroups();
      if (groupsArray.length < 1) {
        return;
      }
      GlobalGroup group = null;
      for (int i = 0; i < groupsArray.length; i++) {
        if (((GlobalGroup)groupsArray[i]).getName().equals(groupId)) {
          group = (GlobalGroup) groupsArray[i];
          break;
        }
      }
      if (group == null) {
        return;
      }
      UserBase user = PrincipalManager.findUser(userId, VirtualServer
          .getDefaultVirtualServer().getName());
      if ((group.getMembers() != null)
          && Arrays.asList(group.getMembers()).contains(user)) {
        group.removeMember(userId);
      } else {
        group.addMember(user);
        addBookmark(userId, groupId, groupId.substring(groupId.lastIndexOf("/") + 1));
      }
    } catch (XythosException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void createGroup(String groupName, String userId) {
    final String description = groupName.substring(groupName.lastIndexOf("/") + 1);
    Context context = null;
    try {
      context = AdminUtil.getContextForAdmin("1.1.1.1");
      String location = VirtualServer.getDefaultVirtualServer().getName();
      PrincipalManager.createGlobalGroup(groupName, location, description, "admin");
      context.commitContext();
      context = null;

      toggleMember(groupName, userId);
    } catch (XythosException e) {
      if (context != null) {
        try {
          context.rollbackContext();
        } catch (XythosException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      }
    }
  }

  public void removeDocument(String path, String userId) {
    Context ctx = null;
    try {
      VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
      ctx = getUserContext(userId, defaultVirtualServer.getName());
      FileSystemEntry file = (File) FileSystem.getEntry(defaultVirtualServer, path,
          false, ctx);
      file.delete();
      ctx.commitContext();
      ctx = null;
    } catch (XythosException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      if (ctx != null) {
        try {
          ctx.rollbackContext();
          ctx = null;
        } catch (XythosException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
  }

  public boolean shareFileWithGroup(String groupId, String filePath, String userId) {
    try {
      Context context = null;
      try {
        VirtualServer defaultVirtualServer = VirtualServer.getDefaultVirtualServer();
        String location = defaultVirtualServer.getName();
        context = getUserContext(userId, location);
        FileSystemEntry entry = FileSystem.getEntry(defaultVirtualServer, URLDecoder.decode(filePath, "utf-8"), true, context);
        if (entry == null) {
          return false;
        }
        // get the group based on its name
        GroupArray groups = PrincipalManager.searchForGroups(groupId, "*", location, 1,
            context);
        if (groups == null) {
          return false;
        }
        Group[] groupsArray = groups.getGroups();
        if (groupsArray.length < 1) {
          return false;
        }
        
        GlobalGroup group = null;
        for (int i = 0; i < groupsArray.length; i++) {
          if (((GlobalGroup)groupsArray[i]).getName().equals(groupId)) {
            group = (GlobalGroup) groupsArray[i];
            break;
          }
        }
        
        if (group == null) {
          return false;
        }
        AccessControlEntry ace = entry.getAccessControlEntry(group.getPrincipalID());

        // setting up permissions
        Boolean l_readable = Boolean.TRUE;
        Boolean l_writeable = Boolean.FALSE;
        Boolean l_deleteable = Boolean.FALSE;
        Boolean l_permissionable = Boolean.FALSE;

        if (entry instanceof FileSystemFile) {
          ace.setAccessControlEntry(l_readable, l_writeable, l_deleteable,
              l_permissionable);
        }

        context.commitContext();
        context = null;
      } catch (UnsupportedEncodingException e) {
        return false;
      } finally {
        if (context != null) {
          context.rollbackContext();
          context = null;
        }
      }
    } catch (XythosException e) {
      return false;
    }
    return true;
  }

}