package org.apache.zeppelin.server;

import com.gable.templar.heaven.service.rest.cerberus.OAuthJwtAccessTokenConverter;
import com.gable.templar.heaven.util.HTTPServletRequestUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.apache.zeppelin.server.view.LongLiveToken;
import org.apache.zeppelin.server.view.UserSession;
import org.apache.zeppelin.service.bde.hera.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class RestAuthFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAuthFilter.class);

    public static final String LONGLIVE_TOKEN_PREFIX = "LL-";

    public static final String SECRET = "G@TemplarPwd";


    private OAuthJwtAccessTokenConverter jwtAccessTokenConverter;
//
//    public RestAuthFilter(HeraUser heraUser) {
//        this.heraUser = heraUser;
//    }

//
//    @Inject
//    public RestAuthFilter(OAuthJwtAccessTokenConverter jwtAccessTokenConverter) {
//
//    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.jwtAccessTokenConverter = new OAuthJwtAccessTokenConverter();
        LOGGER.info("CustomAuthFilter is start");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String username;
        String token = HTTPServletRequestUtil.getToken(req);

        if (req.getRequestURI().contains("/api/notebook/name") || req.getRequestURI().contains("/api/notebook/public/day")) {
            chain.doFilter(request, response);
            return;
        }

        if (token != null) {
            if (token.startsWith("Bearer")) {
                token = token.split(" ")[1];
            }
            if(token.startsWith(LONGLIVE_TOKEN_PREFIX)) {
                LongLiveToken longLiveToken = extractLongLiveToken(token);
                Map<String,Object> mapObject = validateValidationJwtToken(longLiveToken.getValidationJwtToken());
                username = (String) mapObject.get("user_name");
//                token = removeLongLiveValidationJwt(token);
//                if (isLongLiveTokenGenerateFromUserName(token)) {
//                validateValidationJwtToken(longLiveToken.getValidationJwtToken());
//
//                }
            }
            else {
                Map<String,Object> mapObject = jwtAccessTokenConverter.decodeToken(token);
                username = (String) mapObject.get("user_name");
//                tenantId = Long.parseLong((String) mapObject.get("tenant_id"));
            }
            UserSession userSession = new UserSession(username, token);
            UserContext.setUserSession(userSession);
            try {
                chain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
        } else {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write("Unauthorized");
        }
    }

    @Override
    public void destroy() {
    }

    private LongLiveToken extractLongLiveToken(String token) {
        LongLiveToken longLiveToken = new LongLiveToken();

        try {
            String[] _token = token.split(":");
            longLiveToken.setTenantId(_token[0].split("-")[1]);
            longLiveToken.setUserName(_token[1]);
            if (_token.length > 2) {
                longLiveToken.setValidationJwtToken(_token[2]);
            }

            return longLiveToken;
        } catch (Exception var4) {
            return longLiveToken;
        }
    }

    public boolean isLongLiveTokenGenerateFromUserName(String token) {
        LongLiveToken longLiveToken = extractLongLiveToken(token);
        try {
            Base64.getDecoder().decode(longLiveToken.getUserName());
            return true;
        } catch (Exception e) {
            LOGGER.debug(e.getMessage());
        }
        return false;
    }

    public String removeLongLiveValidationJwt(String token) {
        LongLiveToken longLiveToken = extractLongLiveToken(token);
        return LONGLIVE_TOKEN_PREFIX+longLiveToken.getTenantId()+":"+longLiveToken.getUserName();
    }

    public Map<String,Object> validateValidationJwtToken(String token) {
        Map<String,Object> tokenData = new HashMap<>();
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token)
                    .getBody();
            claims.forEach(tokenData::put);
        } catch (ExpiredJwtException e) {
            // Token has expired, but we can still access the claims
            Claims claims = e.getClaims();
            LOGGER.warn("token is expire: " + e.getMessage());
            // Attempt to retrieve specific claims like user_name
            tokenData.put("user_name", claims.get("user_name", String.class));
        }
        return tokenData;
    }
}
