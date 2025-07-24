package org.apache.zeppelin.service.bde.hera;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gable.templar.heaven.service.rest.license.model.LicenseRegisterModuleNotebookRequest;
import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import org.apache.zeppelin.rest.bde.view.auth.LoginRequest;
import org.apache.zeppelin.rest.bde.view.auth.LoginResponse;
import org.apache.zeppelin.rest.bde.view.auth.LoginUser;
import org.apache.zeppelin.rest.bde.view.health.RunningJobHealthStatus;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.service.bde.constant.ApplicationConstant;
import org.apache.zeppelin.service.bde.error.HeraErrorResponse;
import org.apache.zeppelin.service.bde.hera.view.*;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.service.bde.spark.BDESparkServices;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;


public class BDEHeraServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(BDEHeraServices.class);
    private String encodedSalt = "vWt+cxfb6pulYAtKXJlrcw==";
    private String passwordToEncrypt = "password";
    ZeppelinConfiguration zeppelinConf = ZeppelinConfiguration.create();
    ObjectMapper mapper = new ObjectMapper();

    public TokenServiceResponse validateBDEToken(String token, String noteId,String sessionId, String publicToken) {
        String nbToken;
        if (publicToken != null) {
            nbToken = "pubNToken:"+publicToken+":"+noteId;
        }
        else {
            nbToken = "nbToken:"+token+":"+noteId+":"+sessionId;
        }
        String validateTokenUri = zeppelinConf.getString(ConfVars.HERA_SERVICE_VALIDATE_BDE_TOKEN_URL);
        String uri = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)+validateTokenUri;
        TokenServiceResponse resp = new TokenServiceResponse();
        resp.setNbToken(nbToken);
        try{
            Map<String,Integer> request = new HashMap<>();
            request.put("cpuCores",getMostCoresPossible());

            LOGGER.info("Send validate token request to BDE...");
            if (sessionId != null) {
                LOGGER.info("nbToken : {}, noteId : {}, sessionId : {}, cpuCores : {}",nbToken,noteId,sessionId,request.get("cpuCores"));
            }
            else {
                LOGGER.info("nbToken : {}, noteId : {}, cpuCores : {}",nbToken,noteId,request.get("cpuCores"));
            }
            ResponseEntity<ValidateBDETokenResponse> result = RestTemplateFactoryUtil.getRestTemplar(nbToken,true)
                    .postForEntity(uri, request, ValidateBDETokenResponse.class);
            resp.setStatus(0);
            resp.setMessage("Success");
            resp.setRedirectURL(ApplicationConstant.getListNotePageUrl());
            resp.setUsername(result.getBody().getSessionOwner());
            resp.setPermission(result.getBody().getRoleLevel());
            LOGGER.debug("BDE return code : {} , result : {}",result.getStatusCodeValue(),mapper.writeValueAsString(result.getBody()));
        }catch (HttpStatusCodeException e){
            LOGGER.error("Validate Token Error : {}", e.getResponseBodyAsString());
            HeraErrorResponse errorResult = (HeraErrorResponse) parseJson(e.getResponseBodyAsString(),HeraErrorResponse.class);
            if(errorResult.getMessage().equalsIgnoreCase("Notebook Session Expired"))
                resp.setStatus(1);
            else if(errorResult.getMessage().equalsIgnoreCase("Invalid Session"))
                resp.setStatus(2);
            resp.setMessage(errorResult.getMessage());
            resp.setRedirectURL(ApplicationConstant.getListNotePageUrl());

        } catch (Exception e) {
            LOGGER.error("Error while calling hera to validate notebook token.");
            LOGGER.error("",e);
        }

        resp.setModuleNotebookName(zeppelinConf.getString(ConfVars.ZEPPELIN_APP_NAME));

        return resp;
    }

    public void updateNoteRename(String token, String noteId,String sessionId,String noteName) throws Exception{

        String nbToken = "nbToken:"+token+":"+noteId+":"+sessionId;
        String renameNoteUrl = zeppelinConf.getString(ConfVars.HERA_SERVICE_RENAME_NOTE_URL);
        String tmpUri = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)+renameNoteUrl;
        String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(noteId).toUriString();

        try{
            Map<String, String> data = new HashMap<>();
            data.put("name",noteName);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(data);

            ResponseEntity<ValidateBDETokenResponse> result = RestTemplateFactoryUtil.getRestTemplar(nbToken,true)
                    .exchange(restUri, HttpMethod.PUT, request, ValidateBDETokenResponse.class);

            LOGGER.debug("BDE return code : {} ",result.getStatusCodeValue());
            if (result.getStatusCodeValue() != 200) {
                throw new BadRequestException("Rename notebook error");
            }
        }catch (HttpStatusCodeException e){
            LOGGER.error("Validate Token Error : {}", e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error while calling hera to validate notebook token.\n"+e.getMessage(), e);
            throw e;
        }
    }

    public boolean registerZeppelinModule() throws Exception{
        try{
            LicenseRegisterModuleNotebookRequest req = createLicenseRegisterRequestBody();
            LOGGER.info("Zeppelin module request : {}",mapper.writeValueAsString(req));
            String registerZeppelinModule = zeppelinConf.getString(ConfVars.HERA_SERVICE_REGISTER_ZEPPELIN_MODULE_URL);
            String uri = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)+registerZeppelinModule;

            ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(null,true)
                    .postForEntity(uri,req,String.class);
            LOGGER.info("BDE return http status code : {}",response.getStatusCodeValue());

            return true;
        }catch (HttpStatusCodeException e){
            if(e.getStatusCode() == HttpStatus.BAD_REQUEST){
                LOGGER.warn("Registration Error : {}", e.getResponseBodyAsString());
                return true;
            }else{
                LOGGER.error("Registration Error : {}", e.getResponseBodyAsString());
                return false;
            }
        }catch (Exception e){
            LOGGER.error(e.getMessage());
            throw e;
        }
    }

    public boolean stopParallelNotebook() throws Exception{
        try{
            String url = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                    + zeppelinConf.getString(ConfVars.HERA_SERVICE_STOP_ALL_PARALLEL_URL);
            String moduleRefKey = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_MODULE_REF_KEY);

            LOGGER.debug("Request with module reference key : {}",moduleRefKey);
            ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(moduleRefKey,true)
                    .postForEntity(url, null, String.class);
            LOGGER.info("BDE return http status code : {}",response.getStatusCodeValue());

            return true;
        }catch (HttpStatusCodeException e){
            if(e.getStatusCode() == HttpStatus.BAD_REQUEST){
                LOGGER.warn("Stop Parallel Error : {}", e.getResponseBodyAsString());
                return true;
            }else{
                LOGGER.error("Stop Parallel Error : {}", e.getResponseBodyAsString());
                return false;
            }
        }catch (Exception e){
            LOGGER.error(e.getMessage());
            throw e;
        }
    }

    public boolean licenseCheckModule() throws Exception{
        LOGGER.info("Check module license moduleReferenceKey : {}, cpuCores : {}",ApplicationConstant.getModuleReferenceKey(),getMostCoresPossible());
        try{
            String tmpUri = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                    +zeppelinConf.getString(ConfVars.HERA_SERVICE_LICENSE_CHECK_MODULE_URL);
            String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(ApplicationConstant.getModuleReferenceKey(),getMostCoresPossible()).toUriString();

            ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(null,true)
                    .getForEntity(restUri,null,String.class);
            LOGGER.info("BDE return http status code : {}",response.getStatusCode());
            return (response.getStatusCodeValue() == 200);
        }catch (Exception e){
            throw new BadRequestException("Check license module failed.");
        }
    }

    public void forwardCronHealthStatusToBDE(RunningJobHealthStatus job) throws Exception{
        //create health status request
        job.setModuleId(zeppelinConf.getLong(ConfVars.ZEPPELIN_APP_MODULE_ID));
        job.setModuleReferenceKey(ApplicationConstant.getModuleReferenceKey());
        job.setCpuCores(getMostCoresPossible());
        job.setModuleName(zeppelinConf.getString(ConfVars.ZEPPELIN_APP_NAME));

        String url = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                + zeppelinConf.getString(ConfVars.HERA_SERVICE_SEND_HEALTH_STATUS_URL);

        LOGGER.debug("Forward health status to BDE jobId : {}, moduleReferenceKey : {}, request : {}",job.getJobId(),job.getModuleReferenceKey(),job);

        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(ApplicationConstant.getModuleReferenceKey(),true)
                .postForEntity(url, job, String.class);
        LOGGER.debug("BDE return code : {}",response.getStatusCodeValue());
    }

    public void forwardHealthStatusToBDE(RunningJobHealthStatus job) throws Exception{
        //create health status request
        job.setModuleId(zeppelinConf.getLong(ConfVars.ZEPPELIN_APP_MODULE_ID));
        job.setModuleReferenceKey(ApplicationConstant.getModuleReferenceKey());
        job.setModuleName(zeppelinConf.getString(ConfVars.ZEPPELIN_APP_NAME));

        String url = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                + zeppelinConf.getString(ConfVars.HERA_ZEPPELIN_SERVICE_SEND_HEALTH_STATUS_URL);

        LOGGER.debug("Forward health status to BDE jobId : {}, moduleReferenceKey : {}, request : {}",job.getJobId(),job.getModuleReferenceKey(),job);

        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(ApplicationConstant.getModuleReferenceKey(),true)
                .postForEntity(url, job, String.class);
        LOGGER.debug("BDE return code : {}",response.getStatusCodeValue());
    }

    public void forwardLicenseValidateAndHoldCpuRequestToBDE(String jobId) throws Exception{
        try{
            Map<String,Object> request = new HashMap();
            request.put("cpuCores",getMostCoresPossible());
            request.put("jobId",jobId);
            String url = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                    + zeppelinConf.getString(ConfVars.HERA_SERVICE_CRON_HOLD_LICENSE_CPU_URL);

            LOGGER.debug("Forward validate license and hold cpu, jobId : {}, moduleReferenceKey : {}, cpuCores : {}",jobId,ApplicationConstant.getModuleReferenceKey(),request.get("cpuCores"));
            ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(ApplicationConstant.getModuleReferenceKey(),true)
                    .postForEntity(url, request, String.class);
            LOGGER.debug("BDE return code : {}",response.getStatusCodeValue());
        }catch (Exception e){
            throw new BadRequestException("Validate license failed.");
        }

    }

    public void forwardReleaseCpuRequestToBDE(String jobId) throws Exception{

        Map<String,Object> request = new HashMap();
        request.put("cpuCores",getMostCoresPossible());
        request.put("jobId",jobId);

        String url = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                + zeppelinConf.getString(ConfVars.HERA_SERVICE_CRON_UNHOLD_LICENSE_CPU_URL);

        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(ApplicationConstant.getModuleReferenceKey(),true)
                .postForEntity(url, request, String.class);

        LOGGER.debug("Forward release cpu request, jobId : {}, moduleReferenceKey : {}, cpuCores : {}",jobId,ApplicationConstant.getModuleReferenceKey(),request.get("cpuCores"));
        LOGGER.debug("BDE return code : {}",response.getStatusCodeValue());
    }

    private LicenseRegisterModuleNotebookRequest createLicenseRegisterRequestBody() throws Exception{

        LicenseRegisterModuleNotebookRequest licenseRegisterModuleRequest = new LicenseRegisterModuleNotebookRequest();

        licenseRegisterModuleRequest.setModuleId(zeppelinConf.getLong(ConfVars.ZEPPELIN_APP_MODULE_ID));
        licenseRegisterModuleRequest.setModuleReferenceKey(ApplicationConstant.getModuleReferenceKey());
        licenseRegisterModuleRequest.setCpuCores(getMostCoresPossible());
        licenseRegisterModuleRequest.setApplicationInformation(ApplicationConstant.getINFO());
        String hostIp = InetAddress.getLocalHost().getHostAddress();
        String hostName = InetAddress.getLocalHost().getHostName();

        if(!zeppelinConf.getString(ConfVars.ZEPPELIN_APP_REGISTER_HOST_IP).isEmpty() && !zeppelinConf.getString(ConfVars.ZEPPELIN_APP_REGISTER_HOST_NAME).isEmpty()){
            hostIp = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_REGISTER_HOST_IP);
            hostName = zeppelinConf.getString(ConfVars.ZEPPELIN_APP_REGISTER_HOST_NAME);
        }else{
            if(hostIp==null || hostIp.length()==0){
                hostIp = InetAddress.getLocalHost().getHostAddress();
            }
            if(hostName==null || hostName.length()==0){
                hostName = InetAddress.getLocalHost().getHostName();
            }
        }
        licenseRegisterModuleRequest.setHostName(hostName);
        licenseRegisterModuleRequest.setHostIp(hostIp);
        licenseRegisterModuleRequest.setPort(zeppelinConf.getInt(ConfVars.ZEPPELIN_PORT));
        licenseRegisterModuleRequest.setRootPath("");
        licenseRegisterModuleRequest.setMetastoreConf(ApplicationConstant.getHiveSiteConfig().getUrl());
//        licenseRegisterModuleRequest.setDatastoreConf(zeppelinConf.getString(ConfVars.ZEPPELIN_APP_DATA_STORE_DIR));
        licenseRegisterModuleRequest.setHttps(false);
        licenseRegisterModuleRequest.setPublicHostName(zeppelinConf.getString(ConfVars.ZEPPELIN_PUBlIC_HOST_NAME));
        licenseRegisterModuleRequest.setPublicHostIp(zeppelinConf.getString(ConfVars.ZEPPELIN_PUBlIC_HOST_IP));
        licenseRegisterModuleRequest.setPublicPort(zeppelinConf.getInt(ConfVars.ZEPPELIN_PUBlIC_PORT));
        licenseRegisterModuleRequest.setPublicRootPath(zeppelinConf.getString(ConfVars.ZEPPELIN_PUBlIC_DOMAIN_SUB_PATH));
        licenseRegisterModuleRequest.setPublicHttps(false);

        return licenseRegisterModuleRequest;
    }

    private Object parseJson(String jsonString,Class klazz){
        try{
            return new ObjectMapper().readValue(jsonString, klazz);
        }catch (Exception e){
            LOGGER.error("Error trying to parse json ",e);
            return null;
        }

    }

    private int getMostCoresPossible() throws Exception{
        BDESparkServices bdeSparkServices = new BDESparkServices();

        int mostCoresPossible = 0;
        int sparkCoresMax = Integer.parseInt(bdeSparkServices.getSparkConfigValue("spark.cores.max"));
        int executorCores = Integer.parseInt(bdeSparkServices.getSparkConfigValue("spark.executor.cores"));

        while(mostCoresPossible < sparkCoresMax) mostCoresPossible += executorCores;
        mostCoresPossible = (mostCoresPossible > sparkCoresMax) ? mostCoresPossible - executorCores : mostCoresPossible;

        return mostCoresPossible;
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
    
    public void forwardCheckCpuToHeraLicenseOverLimit(int cores) throws Exception {
        String tmpUrl = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)
                + zeppelinConf.getString(ConfVars.HERA_SERVICE_CHECK_CPU_LICENSE_OVER_LIMIT) ;
        String url = UriComponentsBuilder.fromUriString(tmpUrl).buildAndExpand(String.valueOf(cores)).toUriString();
        LOGGER.info("Send license validate cpu to hera " + cores + " cores" );
        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(ApplicationConstant.getModuleReferenceKey(),true)
                .getForEntity(url, String.class);
        try {
            if (response.getStatusCodeValue() != 200) {
                throw new IllegalAccessException("Error while calling hera to check cpu limit");
            }
        } catch (Exception e) {
            HeraErrorResponse errorResult = (HeraErrorResponse) parseJson(response.getBody(), HeraErrorResponse.class);
            throw new IllegalAccessException(errorResult.getMessage());
        }
    }

    public static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
        byte[] encryptedIVAndText = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, encryptedIVAndText, 0, iv.length);
        System.arraycopy(encrypted, 0, encryptedIVAndText, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(encryptedIVAndText);
    }

    public static String decrypt(String encryptedText, SecretKey key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        byte[] iv = new byte[16];
        byte[] encrypted = new byte[decoded.length - 16];
        System.arraycopy(decoded, 0, iv, 0, 16);
        System.arraycopy(decoded, 16, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, "UTF-8");
    }

    public String encryptUser(String userText) throws Exception {
        String password = passwordToEncrypt;
        byte[] salt = Base64.getDecoder().decode(encodedSalt);
        SecretKey key = deriveKey(password, salt);
        return encrypt(userText, key);
    }

    public String decryptUser() throws Exception {
        byte[] salt = Base64.getDecoder().decode(encodedSalt);
        SecretKey key = deriveKey(passwordToEncrypt, salt);
        String encryptUser = zeppelinConf.getString(ConfVars.HERA_USER);
        if (encryptUser.isEmpty() || encryptUser.equals("default")) {
            throw new BadRequestException("Please set encrypt hera user in config");
        }
        return decrypt(encryptUser, key);
    }

    public LoginResponse getAccessTokenFromHeraByUser(String userName, String password) throws Exception {
        String authenticationUrl = zeppelinConf.getString(ConfVars.HERA_SERVICE_AUTH_URL);
        Long tenantID = zeppelinConf.getLong(ConfVars.ZEPPELIN_APP_TENANT_ID);
        LoginRequest loginRequest = new LoginRequest();
        LoginUser loginUser = new LoginUser();
        loginUser.setUsername(userName);
        loginUser.setPassword(password);
        loginRequest.setUser(loginUser);
        loginRequest.setTenantId(tenantID);
        String uri = zeppelinConf.getString(ConfVars.HERA_ADDR)+":"+zeppelinConf.getString(ConfVars.HERA_PORT)+authenticationUrl;
        ResponseEntity<LoginResponse> response = RestTemplateFactoryUtil.getRestTemplar(null,true)
                .postForEntity(uri,loginRequest, LoginResponse.class);
        if (response.getStatusCodeValue() != 200) {
            throw new BadRequestException("Authentication Failed.");
        }
        return response.getBody();
    }

}
