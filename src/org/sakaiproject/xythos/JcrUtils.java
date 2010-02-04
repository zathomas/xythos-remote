package org.sakaiproject.xythos;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

public class JcrUtils {
  
  /**
   * Deep creates a path.
   * 
   * @param session
   * @param path
   * @param nodeType
   * @return
   * @throws RepositoryException
   */
  public static Node deepGetOrCreateNode(Session session, String path, String nodeType)
      throws RepositoryException {
    if (path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("path must be an absolute path.");
    }
    if (!"/".equals(path) && path.endsWith("/")) { // strip trailing slash
      path = path.substring(0, path.lastIndexOf("/"));
    }
    // get the starting node
    String startingNodePath = path;
    Node startingNode = null;
    while (startingNode == null) {
      if (startingNodePath.equals("/")) {
        startingNode = session.getRootNode();
      } else if (session.itemExists(startingNodePath)) {
        startingNode = (Node) session.getItem(startingNodePath);
      } else {
        int pos = startingNodePath.lastIndexOf('/');
        if (pos > 0) {
          startingNodePath = startingNodePath.substring(0, pos);
        } else {
          startingNodePath = "/";
        }
      }
    }
    // is the searched node already existing?
    if (startingNodePath.length() == path.length()) {
      return startingNode;
    }
    // create nodes
    int from = (startingNodePath.length() == 1 ? 1 : startingNodePath.length() + 1);
    Node node = startingNode;
    while (from > 0) {
      final int to = path.indexOf('/', from);
      final String name = to < 0 ? path.substring(from) : path.substring(from, to);
      boolean leafNode = to < 0;
      // although the node should not exist (according to the first test
      // above)
      // we do a sanety check.
      if (node.hasNode(name)) {
        node = node.getNode(name);
      } else {
        if (leafNode && nodeType != null) {
          node = node.addNode(name, nodeType);
        } else {
          node = node.addNode(name);
        }
      }
      from = to + 1;
    }
    return node;
  }

}
