package org.apache.zeppelin.service.bde.hera;

import org.apache.zeppelin.server.view.UserSession;
import org.eclipse.jetty.util.annotation.ManagedObject;

@ManagedObject
public class UserContext {
    private static final ThreadLocal<UserSession> userSessionHolder = new ThreadLocal<>();

    public static void setUserSession(UserSession userSession) {
        userSessionHolder.set(userSession);
    }

    public static UserSession getUserSession() {
        return userSessionHolder.get();
    }

    public static String getUsername() {
        UserSession session = userSessionHolder.get();
        return session != null ? session.getUsername() : null;
    }

    public static String getToken() {
        UserSession session = userSessionHolder.get();
        return session != null ? session.getToken() : null;
    }

    public static void clear() {
        userSessionHolder.remove();
    }
}
