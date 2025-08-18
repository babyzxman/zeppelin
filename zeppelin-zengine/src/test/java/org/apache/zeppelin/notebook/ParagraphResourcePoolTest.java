package org.apache.zeppelin.notebook;

import com.google.gson.Gson;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParagraphResourcePoolTest extends AbstractInterpreterTest {

  static class User implements Serializable {
    private final String id;
    private final String name;

    User(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  @Test
  void serverSideResourceInjection() throws Exception {
    String noteId = notebook.createNote("/note_1", null, AuthenticationInfo.ANONYMOUS);
    Note note = notebook.getNote(noteId);
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%mock_resource_pool get " + noteId + ":" + p.getId() + ":userObj");
    p.setAuthenticationInfo(AuthenticationInfo.ANONYMOUS);
    p.addResource("userObj", new User("42", "Alice"));

    note.run(p.getId());
    while (!p.isTerminated() || p.getReturn() == null) {
      Thread.yield();
    }

    String expected = new Gson().toJson(new User("42", "Alice"));
    assertEquals(expected, p.getReturn().message().get(0).getData());

    notebook.removeNote(noteId, AuthenticationInfo.ANONYMOUS);
  }
}
