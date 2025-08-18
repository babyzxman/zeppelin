package org.apache.zeppelin.notebook;

import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.junit.jupiter.api.Test;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests broadcasting of resources into running interpreter groups.
 */
public class ResourceBroadcastServiceTest extends AbstractInterpreterTest {

  static class User implements Serializable {
    final String id;
    final String name;

    User(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  @Test
  void broadcastResourceToInterpreter() throws Exception {
    String noteId = notebook.createNote("/note_broadcast", null, AuthenticationInfo.ANONYMOUS);
    Note note = notebook.getNote(noteId);
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText("%mock1 echo 1");
    p.setAuthenticationInfo(AuthenticationInfo.ANONYMOUS);
    note.run(p.getId());
    while (!p.isTerminated()) {
      Thread.yield();
    }

    notebook.broadcastResource("userObj", new User("7", "Bob"));
    // allow background thread to push resource
    Thread.sleep(200);

    for (ManagedInterpreterGroup group : interpreterSettingManager.getAllInterpreterGroup()) {
      Resource res = group.getResourcePool().get("userObj");
      assertNotNull(res);
      User u = (User) res.get();
      assertEquals("Bob", u.name);
    }

    notebook.removeNote(noteId, AuthenticationInfo.ANONYMOUS);
  }
}
