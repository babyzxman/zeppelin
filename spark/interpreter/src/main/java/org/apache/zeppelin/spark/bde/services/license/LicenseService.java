package org.apache.zeppelin.spark.bde.services.license;

import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.spark.bde.services.license.view.HealthStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LicenseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthStatusRunnable.class);
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();

    public void sendSparkJobHealthStatus(String jobId, Date startTime, Date currentTime,Date previousTime, boolean isLastTime) throws Exception{

        HealthStatusRequest request = new HealthStatusRequest();
        request.setStartTime(startTime);
        request.setPreviousTime(previousTime);
        request.setCurrentTime(currentTime);
        request.setJobId(jobId);
        request.setLastTime(isLastTime);

        String addr = zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_ADDR) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR);
        String port = zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_PORT) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT);

        String url = zConf.getString(ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ConfVars.ZEPPELIN_SSL) ? zConf.getString(ConfVars.ZEPPELIN_SSL_PORT) : port)
                +zConf.getString(ConfVars.ZEPPELIN_SERVER_SEND_HEALTH_STATUS_URL);

        RestTemplateFactoryUtil.getRestTemplar().put(url,request);

        LOGGER.debug("Send health status to zeppelin-server jobId : {}",jobId);
    }

    public void deleteSparkJobHealthStatus(String jobId) throws Exception{

        Map<String,String> param = new HashMap<>();
        param.put("jobId",jobId);
        String addr = zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_ADDR) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR);
        String port = zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_PORT) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT);

        String url = zConf.getString(ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ConfVars.ZEPPELIN_SSL) ? zConf.getString(ConfVars.ZEPPELIN_SSL_PORT) : port)
                +zConf.getString(ConfVars.ZEPPELIN_SERVER_SEND_HEALTH_STATUS_URL)
                +"/{jobId}";

        RestTemplateFactoryUtil.getRestTemplar().delete(url,param);

    }

    public void callValidateLicenseAndHoldCpu(String jobId) throws Exception{

        String addr = zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_ADDR) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR);
        String port = zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_PORT) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT);

        String tmpUri = zConf.getString(ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ConfVars.ZEPPELIN_SSL) ? zConf.getString(ConfVars.ZEPPELIN_SSL_PORT) : port)
                +zConf.getString(ConfVars.ZEPPELIN_SERVER_LICENSE_VALIDATE_HOLD_CPU_URL);
        String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(jobId).toUriString();

        RestTemplateFactoryUtil.getRestTemplar().postForLocation(restUri,null);

        LOGGER.info("Send license validate and hold cpu to zeppelin-server jobId : {}",jobId);
    }

    public void callValidateLicenseCpu() throws Exception{
        LOGGER.info("Send license validate cpu to zeppelin-server");
        String addr = zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_ADDR) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR);
        String port = zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_PORT) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT);

        String url = zConf.getString(ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ConfVars.ZEPPELIN_SSL) ? zConf.getString(ConfVars.ZEPPELIN_SSL_PORT) : port)
                +zConf.getString(ConfVars.ZEPPELIN_SERVER_LICENSE_VALIDATE_CPU_URL);

        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(null,true)
                .getForEntity(url, String.class);
        if (response.getStatusCodeValue() != 200) {
            throw new IllegalAccessException("Validate cpu error");
        }
    }

    public void callReleaseCpu(String jobId) throws Exception{

        String addr = zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_ADDR) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_ADDR);
        String port = zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ConfVars.ZEPPELIN_PORT) : zConf.getString(ConfVars.ZEPPELIN_LOCAL_PORT);

        String tmpUri = zConf.getString(ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ConfVars.ZEPPELIN_SSL) ? zConf.getString(ConfVars.ZEPPELIN_SSL_PORT) : port)
                +zConf.getString(ConfVars.ZEPPELIN_SERVER_LICENSE_RELEASE_CPU_URL);
        String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(jobId).toUriString();

        RestTemplateFactoryUtil.getRestTemplar().postForLocation(restUri,null);

        LOGGER.info("Send license release cpu to zeppelin-server jobId : {}",jobId);
    }

}
