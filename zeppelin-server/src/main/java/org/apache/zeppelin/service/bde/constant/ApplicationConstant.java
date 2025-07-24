package org.apache.zeppelin.service.bde.constant;

import com.gable.templar.heaven.constant.ApplicationInformation;
import com.gable.templar.heaven.util.SystemUtil;
import org.apache.zeppelin.service.bde.config.HiveSiteConfig;
import org.glassfish.jersey.server.monitoring.ApplicationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.net.UnknownHostException;

public class ApplicationConstant {
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConstant.class);
    private static ApplicationInformation INFO = new ApplicationInformation();
    private static HiveSiteConfig HIVE_SITE_CONFIG = new HiveSiteConfig();
    private static String MODULE_REFERENCE_KEY = "";
    private static String LIST_NOTE_PAGE_URL;

    public static String getListNotePageUrl() {
        return LIST_NOTE_PAGE_URL;
    }

    public static void setListNotePageUrl(String listNotePageUrl) {
        LIST_NOTE_PAGE_URL = listNotePageUrl;
    }

    public static HiveSiteConfig getHiveSiteConfig() {
        return HIVE_SITE_CONFIG;
    }

    public static void setHiveSiteConfig(HiveSiteConfig hiveSiteConfig) {
        HIVE_SITE_CONFIG = hiveSiteConfig;
    }

    public static void setModuleReferenceKey(String moduleReferenceKey) {
        MODULE_REFERENCE_KEY = moduleReferenceKey;
    }

    public static ApplicationInformation getINFO() {
        return INFO;
    }

    public static void setINFO(ApplicationInformation info){
        INFO = info;
    }

    public static String getModuleReferenceKey(){
        return MODULE_REFERENCE_KEY;
    }

    public static void setModuleReferenceKey(String appName, Integer port){
        try {
            MODULE_REFERENCE_KEY = SystemUtil.getModuleReferenceKey(appName, port);
        } catch (UnknownHostException e) {
            LOG.error(e.getMessage(),e);
        } catch (SocketException e) {
            LOG.error(e.getMessage(),e);
        }
    }

    public static void setModuleReferenceKey(String appName, Integer port, String hardwareId) throws Exception{
        if(appName != null && port != null)
            MODULE_REFERENCE_KEY = appName + "-" + hardwareId + "-" + port;
        else
            throw new NullPointerException("Module name or port is null.");
    }

}
