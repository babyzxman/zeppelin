package org.apache.zeppelin.rest.bde;

import org.apache.zeppelin.rest.bde.view.auth.EncryptUserRequest;
import org.apache.zeppelin.rest.bde.view.auth.LoginResponse;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.bde.hera.BDEHeraServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/bde")
@Produces("application/json")
@Singleton
public class BDEAuthRestApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(BDEAuthRestApi.class);
    private BDEHeraServices bdeHeraServices = new BDEHeraServices();

    @POST
    @Path("validate/sent")
    public Response validateApiAndSentToHera() throws Exception {
        String decryptedUser = bdeHeraServices.decryptUser();
        String[] decryptedUserPass = decryptedUser.split("\\|");
        String username = decryptedUserPass[0];
        String password = decryptedUserPass[1];
        int retry = 0;
        while (retry<3) {
            try {
                LoginResponse response = bdeHeraServices.getAccessTokenFromHeraByUser(username,password);
                return new JsonResponse<>(Response.Status.OK, "", response.getOauth().getAccess_token()).build();
            } catch (Exception e) {
                LOGGER.warn("Retry Login...");
                retry++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread was interrupted", ie);
                }
            }
        }
        throw new BadRequestException("Authentication Failed.");
    }


    @POST
    @Path("encrypt/user")
    public Response encryptUser(String message) throws Exception {
        EncryptUserRequest encryptUserRequest = EncryptUserRequest.fromJson(message);
        String userText = encryptUserRequest.getUsername() + "|" + encryptUserRequest.getPassword();
        String encryptedUser = bdeHeraServices.encryptUser(userText);
        return new JsonResponse<>(Response.Status.OK, "", encryptedUser).build();
    }

//    @POST
//    @Path("decrypt/user")
//    public Response decryptUser(String message) throws Exception {
//        String decryptedUser = bdeHeraServices.decryptUser();
//        return new JsonResponse<>(Response.Status.OK, "", decryptedUser).build();
//    }


}
