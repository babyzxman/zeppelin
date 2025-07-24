package org.apache.zeppelin.bde.services.hera;

import com.gable.templar.heaven.util.ObjectUtil;
import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BDEHeraServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(BDEHeraServices.class);
    ZeppelinConfiguration zeppelinConf = ZeppelinConfiguration.create();
    private static final Pattern REPL_PATTERN = Pattern.compile("^(\\s*)%([a-zA-Z0-9_\\-]+(?:\\.\\w+)*)");

    public void callLicenseValidate() throws Exception{
        LOGGER.info("Call BDE to validate license before running non-spark cron job or run note from Rest API...");

        String url = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                + zeppelinConf.getString(ConfVars.HERA_SERVICE_CRON_LICENSE_VALIDATE_URL);
        String moduleRefKey = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_MODULE_REF_KEY);

        LOGGER.debug("Request with module reference key : {}",moduleRefKey);
        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(moduleRefKey,true)
                .postForEntity(url,null, String.class);

        LOGGER.info("BDE return code : {}",response.getStatusCodeValue());
    }

    public String changeInterpreterGroupName(String text, Map<String, String> interpreterGroupNewName) {
        List<String> sparkInterpreters = Arrays.asList("sql", "pyspark", "ipyspark", "r", "ir", "shiny");
        if (ObjectUtil.isNullOrEmpty(interpreterGroupNewName)) {
            return text;
        }
        String firstLine = text.split("\n")[0];
        String interpreterGroupName;
        Matcher matcher = REPL_PATTERN.matcher(firstLine);
        if (matcher.find()) {
            String headingSpace = matcher.group(1);
            String intpText = matcher.group(2);
            String[] interpreter = intpText.split("\\.");
            int lastDotIndex = intpText.lastIndexOf('.');
            if (lastDotIndex == -1) {
                interpreterGroupName = intpText;
            }
            else {
                interpreterGroupName = intpText.substring(0, lastDotIndex);
            }
            if (interpreterGroupNewName.containsKey(interpreterGroupName)) {
                String newInterpreterGroup = interpreterGroupNewName.get(interpreterGroupName);
                String newLine = firstLine.replace(interpreterGroupName, newInterpreterGroup);
                String[] lines = text.split("\n", -1);
                lines[0] = newLine;
                return String.join("\n", lines);
            } else if (interpreterGroupNewName.containsKey("spark") && (sparkInterpreters.contains(interpreterGroupName))) {
                String newLine = firstLine.replace(interpreterGroupName, interpreterGroupNewName.get("spark") + "." + interpreterGroupName);
                String[] lines = text.split("\n", -1);
                lines[0] = newLine;
                return String.join("\n", lines);
            }

        }
        //replace default interpreter spark
        else if (interpreterGroupNewName.containsKey("spark")) {
            String newLine = "%" + interpreterGroupNewName.get("spark");
            return newLine + "\n" + text;
        }
        return text;
    }

    public String addTokenToHeraApiText(String text, String nbToken, String noteId, String user, String originalNoteId) {
        if(!text.contains("blendata_util") && !text.contains("hera_api")) {
            return text;
        }
        int verify = 0;
        if (zeppelinConf.getBoolean(ConfVars.HERA_SSL)) {
            verify = 1;
        }
        String uri = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT);
        String addr = zeppelinConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zeppelinConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zeppelinConf.getString(ConfVars.ZEPPELIN_ADDR) : zeppelinConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR);
        String port = zeppelinConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zeppelinConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zeppelinConf.getString(ConfVars.ZEPPELIN_PORT) : zeppelinConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT);

        String zeppelinUri = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zeppelinConf.getBoolean(ConfVars.ZEPPELIN_SSL) ? zeppelinConf.getString(ConfVars.ZEPPELIN_SSL_PORT) : port);
        //        String storePath = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_DATA_STORE_DIR);
        String moduleNotebookName = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_NAME);
        noteId = moduleNotebookName + ":" + noteId + ":" + originalNoteId;
        if (nbToken == null && text.contains("hera_api")) {
            try {
                String validateUri = zeppelinUri + zeppelinConf.getString(ConfVars.ZEPPELIN_BDE_VALIDATE);
                ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(null,true)
                        .postForEntity(validateUri,null, String.class);
                JSONObject jsonObject = new JSONObject(Objects.requireNonNull(response.getBody()));
                nbToken = jsonObject.getString("body");
            } catch (Exception e) {
                LOGGER.error("Authentication to hera error cause: " + e);
                nbToken = "noToken";
            }
        }
        if (user == null) {
            user = "unknown";
        }
        String replacedString = text.replaceAll("(blendata_util\\.hera_api_[a-zA-Z_]+)\\(([^)]*)\\)", "$1(\"" + uri + "\", \"" + nbToken + "\", " + verify + ", \"" + noteId +  "\", \""  + user +  "\",  $2)");
        replacedString= replacedString.replaceAll("(blendata_util_py\\.hera_api_[a-zA-Z_]+)\\(([^)]*)\\)", "$1(\"" + uri + "\", \"" + nbToken + "\", " + verify + ", \"" + noteId +  "\", \"" + user +  "\",  $2)");

        replacedString = replacedString.replaceAll(", \"" + nbToken + "\", " + verify + "\", " + noteId + "\", \"" + user +  "\", \\)", ", \"" + nbToken + "\", " + verify + ", \"" + noteId + "\", \"" + user + "\")");

        replacedString = replacedString.replaceAll("(blendata_util_py\\.encryptUser+)\\(([^)]*)\\)", "$1(\"" + zeppelinUri + "\",$2)");
        replacedString = replacedString.replaceAll("(blendata_util\\.encryptUser+)\\(([^)]*)\\)", "$1(\"" + zeppelinUri + "\",$2)");

        replacedString = replacedString.replaceAll("(blendata_util_py\\.importNoteName+)\\(([^)]*)\\)", "$1(\"" + zeppelinUri + "\", z, $2)");
        replacedString = replacedString.replaceAll("(blendata_util\\.importNoteName+)\\(([^)]*)\\)", "$1(\"" + zeppelinUri + "\", z, $2)");

        replacedString = replacedString.replaceAll("(blendata_util\\.getUser+)\\(([^)]*)\\)", "$1(\"" + user + "\")");
        replacedString = replacedString.replaceAll("(blendata_util_py\\.getUser+)\\(([^)]*)\\)", "$1(\"" + user + "\")");

        replacedString = replacedString.replaceAll("(blendata_util\\.getModuleNotebookName+)\\(([^)]*)\\)", "$1(\"" + moduleNotebookName + "\")");
        replacedString = replacedString.replaceAll("(blendata_util_py\\.getModuleNotebookName+)\\(([^)]*)\\)", "$1(\"" + moduleNotebookName + "\")");
        return replacedString;
    }
}
