package org.apache.zeppelin.resource;

/** Listener for resource pool updates. */
public interface ResourcePoolListener {
  void onResourceUpdated(String noteId, String paragraphId, String name, Object value);
}
