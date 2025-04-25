package com.renewazuresecret.renew_azure_secret;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RenewalService {
    private static final Logger log = LogManager.getLogger(RenewalService.class);
    @Value("${azure.tenant-id}")
    private String tenantId;

    @Value("${azure.client-id}")
    private String clientId;

    @Value("${azure.client-secret}")
    private String clientSecret;

    @Value("${azure.graph-url}")
    private String graphUrl;

    @Autowired
    private EmailService emailService;

    @Autowired
    OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    private final WebClient webClient = WebClient.builder().build();

    private String getAccessToken(OAuth2AuthorizedClient authorizedClient) {
        //ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder().clientId(clientId)
        //.clientSecret(clientSecret).tenantId(tenantId).build();
        //return Objects.requireNonNull(clientSecretCredential.getToken(new TokenRequestContext().addScopes("https://graph.microsoft.com/.default")).block()).getToken();
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private boolean isSecretExpiringSoon(String expirationDate) {
        Instant expiry = Instant.parse(expirationDate);
        Instant now = Instant.now();
        return expiry.isBefore(now.plus(60, ChronoUnit.DAYS));
    }

    public void checkAndRenewSecret(OAuth2AuthorizedClient client) {
        String token = getAccessToken(client);

        String url = graphUrl + "/applications";
        long startTime = System.currentTimeMillis();
        String response = webClient.get().uri(url).headers(httpHeaders -> httpHeaders.setBearerAuth(token))
                .retrieve().bodyToMono(String.class).block();
        JSONObject applicationJsonResponse = new JSONObject(response);
        long endTime = System.currentTimeMillis();
        //log.info("First API : {}", endTime-startTime);
        String appIdForException="";
        for (Object application : applicationJsonResponse.getJSONArray("value")) {
            try {
                JSONObject app = (JSONObject) application;
                appIdForException = app.getString("appId");
                if (isApplicationEnabledForUserLogin(app.getString("appId"), token)) {
                    url = graphUrl + "/applications/" + app.getString("id");
                    startTime = System.currentTimeMillis();
                    response = webClient.get().uri(url).headers(httpHeaders -> httpHeaders.setBearerAuth(token))
                            .retrieve().bodyToMono(String.class).block();

                    JSONObject jsonResponse = new JSONObject(response);
                    List<String> emailIds = new ArrayList<>();
                    if (!ObjectUtils.isEmpty(jsonResponse.optString("notes", null)))
                        emailIds = extractEmailIds(jsonResponse.getString("notes"));
                    String trouxId = jsonResponse.optString("serviceManagementReference", "");


                    List<JSONObject> secrets = new ArrayList<>();
                    for (Object obj : jsonResponse.getJSONArray("passwordCredentials")) {
                        secrets.add((JSONObject) obj);
                    }

                    endTime = System.currentTimeMillis();
                    //log.info("Third API : {}", endTime - startTime);

                    for (JSONObject secret : secrets) {
                        startTime = System.currentTimeMillis();
                        String existingSecretExpiry = secret.getString("endDateTime");
                        if (!ObjectUtils.isEmpty(existingSecretExpiry) && isSecretExpiringSoon(existingSecretExpiry)) {
                            ZonedDateTime zonedDateTime = ZonedDateTime.parse(existingSecretExpiry);
                            ZonedDateTime newDate = zonedDateTime.plusYears(1);
                            String formattedDate = newDate.format(DateTimeFormatter.ISO_INSTANT);
                            boolean alreadyRenewed = secrets.stream().anyMatch(s -> s.has("endDateTime") && s.getString("endDateTime").equals(formattedDate));
                            if (!alreadyRenewed) {
                                String secretText = renewClientSecret(token, existingSecretExpiry, app.getString("id"));
//                                if (!CollectionUtils.isEmpty(emailIds)) {
//                                    String displayName = app.getString("displayName");
//                                    String applicationEnvironment;
//                                    if (displayName.toLowerCase().contains("qa"))
//                                        applicationEnvironment = "QA";
//                                    else
//                                        applicationEnvironment = "PROD";
//
//                                    //Existing Secret Expiry
//                                    ZonedDateTime utcDateTime = ZonedDateTime.parse(existingSecretExpiry);
//                                    ZonedDateTime istDateTime = utcDateTime.withZoneSameInstant(ZoneId.systemDefault());
//                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//
//                                    //New Secret Expiry
//                                    ZonedDateTime newIstDateTime = newDate.withZoneSameInstant(ZoneId.systemDefault());
//
//                                    emailService.sendEmail(emailIds, "Azure App Client Secret Renewal for " + displayName, applicationEnvironment, trouxId, istDateTime.format(formatter),
//                                            displayName, app.getString("appId"), secretText, newIstDateTime.format(formatter));
//                                }
                            } else
                                log.info("Secret with expiration {} is already renewed", existingSecretExpiry);
                        }
                        endTime = System.currentTimeMillis();
                        //log.info("Forth API with Mail : {}", endTime-startTime);
                    }
                } else {
                    log.info("{} is not accepting user logins", app.getString("appId"));
                }
            }
            catch (Exception ex)
            {
                log.info("User does not own the application {}", appIdForException);
                ex.printStackTrace();
            }
        }
    }

    private List<String> extractEmailIds(String notes) {
        List<String> emails = new ArrayList<>();
        Pattern pattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        Matcher matcher = pattern.matcher(notes);
        while(matcher.find())
        {
            emails.add(matcher.group());
        }
        return emails;
    }

    private boolean isApplicationEnabledForUserLogin(String appClientId, String token) {
        String url = graphUrl + "/servicePrincipals(appId='" + appClientId + "')";
        long startTime = System.currentTimeMillis();
        String response = webClient.get().uri(url).headers(httpHeaders -> httpHeaders.setBearerAuth(token))
                .retrieve().bodyToMono(String.class).block();
        JSONObject jsonResponse = new JSONObject(response);
        long endTime = System.currentTimeMillis();
        //log.info("Second API: {} ",endTime - startTime);
        return jsonResponse.getBoolean("accountEnabled");
    }

    private String renewClientSecret(String token, String existingSecretExpiry, String appObjectId) {
        String url = graphUrl + "/applications/" + appObjectId + "/addPassword";

        JSONObject requestBody = getJsonObject(existingSecretExpiry);
        String response = webClient.post().uri(url).headers(httpHeaders -> {
                    httpHeaders.setBearerAuth(token);
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                }).
                bodyValue(requestBody.toString()).retrieve().bodyToMono(String.class).block();
        JSONObject jsonResponse = new JSONObject(response);
        String newSecret = jsonResponse.getString("secretText");
        log.info("New Client Secret: {}", newSecret);
        return newSecret;
    }

    private static JSONObject getJsonObject(String existingSecretExpiry) {
        JSONObject requestBody = new JSONObject();
        JSONObject passwordCredential = new JSONObject();
        passwordCredential.put("displayName", "Auto Renewed Secret " + LocalDate.now());
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(existingSecretExpiry);
        ZonedDateTime newDate = zonedDateTime.plusYears(1);
        passwordCredential.put("endDateTime", newDate.toString());
        passwordCredential.put("startDateTime", Instant.now().toString());
        requestBody.put("passwordCredential", passwordCredential);
        return requestBody;
    }
}
