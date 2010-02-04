package org.sakaiproject.xythos;

import com.xythos.common.api.VirtualServer;
import com.xythos.jcr.api.AdminCredentials;
import com.xythos.jcr.api.RepositoryFactory;
import com.xythos.storageServer.api.classification.ClassificationManager;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class XythosEndpointServlet extends HttpServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 8770513269344805539L;

  /**
   * Constructor of the object.
   */
  public XythosEndpointServlet() {
    super();
  }

  /**
   * Destruction of the servlet. <br>
   */
  public void destroy() {
    super.destroy(); // Just puts "destroy" string in log
    // Put your code here
  }

  /**
   * The doGet method of the servlet. <br>
   *
   * This method is called when a form has its tag value method equals to get.
   * 
   * @param request the request send by the client to the server
   * @param response the response send by the server to the client
   * @throws ServletException if an error occurred
   * @throws IOException if an error occurred
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("application/json");
    PrintWriter out = response.getWriter();
    out.println("{\"files\":[{\"filename\":\"IMG_7172.JPG\",\"path\":\"/_user/files/AkPiNjM-\",\"id\":\"AkPiNjM-\"}]}");
    out.flush();
    out.close();
  }

  /**
   * The doPost method of the servlet. <br>
   *
   * This method is called when a form has its tag value method equals to post.
   * 
   * @param request the request send by the client to the server
   * @param response the response send by the server to the client
   * @throws ServletException if an error occurred
   * @throws IOException if an error occurred
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    
    // Create a factory for disk-based file items
    FileItemFactory factory = new DiskFileItemFactory();

    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload(factory);

    // Parse the request
    List<?> /* FileItem */items = new ArrayList<FileItem>();
    try {
      items = upload.parseRequest(request);
    } catch (FileUploadException e) {
      throw new RuntimeException(e);
    }
    if (items.size() < 1) {
      throw new RuntimeException("Unable to pull a file from the request.");
    }
    FileItem firstItem = (FileItem) items.get(0);
    String parentFolderPath = "/" + request.getParameter("path");
    String newContentPath = "";
    try {
      newContentPath = addFile(parentFolderPath, firstItem.get(), firstItem.getName(), firstItem.getContentType());
    } catch (RepositoryException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    response.setContentType("application/json");
    PrintWriter out = response.getWriter();
    out.println("{\"files\":[{\"filename\":\"" + firstItem.getName() + "\",\"path\":\""+ newContentPath +"\",\"id\":\"xxxxxxxxx\"}]}");
    out.flush();
    out.close();
  }

  /**
   * Initialization of the servlet. <br>
   *
   * @throws ServletException if an error occurs
   */
  public void init() throws ServletException {
    // Put your code here
  }
  
  private String addFile(String parentFolderPath, byte[] fileContent, String fileName, String mimeType) throws RepositoryException {
    VirtualServer vServer = VirtualServer.getDefaultVirtualServer();
    String virtualserver = vServer.getName();
    // Obtain the JCR repository instance
    Repository repository = RepositoryFactory.newRepository(virtualserver);
    Session session = null;
    try { 
      // Obtain the JCR session 
      session = repository.login(new AdminCredentials("test"));
      // Compute the node type name (plain WFS file) 
      String nodetypenamespace = "http://www.jcp.org/jcr/nt/1.0";
      String nodetypename = "file"; 
      // ...for creating a file with a specific document class, 
      // we need both the document class property namespace name, 
      // and the document class name. 
      nodetypenamespace = ClassificationManager.getDocumentClassPropertyNamespace(vServer);
      //for the node type's namespace 
      String prefix = session.getNamespacePrefix(nodetypenamespace);
      // get the parent node
      Node parent = (Node)session.getItem(parentFolderPath);
      // add file node
      Node file = parent.addNode(fileName, prefix + ":" + nodetypename); 
      // add jcr:content child node
      Node content = file.addNode("jcr:content", "nt:resource"); 
      // set content properties, including jcr:data which takes the binary data
      content.setProperty("jcr:data", new ByteArrayInputStream(fileContent));
      content.setProperty("jcr:encoding", "UTF-8");
      content.setProperty("jcr:mimeType", mimeType);
      
      // save parent node 
      parent.save();
      return content.getPath();
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        if (session != null) { 
          session.logout(); 
        } 
      } 
  }

}
