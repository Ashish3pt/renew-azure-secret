package com.renewazuresecret.renew_azure_secret;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RenewController {
    private final RenewalService renewalService;

    public RenewController(RenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @GetMapping("/sign-in")
    public String login(@RegisteredOAuth2AuthorizedClient("azure") OAuth2AuthorizedClient client)
    {
        return client.getAccessToken().getTokenValue();
    }

    @GetMapping("/renew_secret")
    public String renew(HttpServletRequest request)
    {
        String accessToken = request.getHeader("Authorization");
        renewalService.checkAndRenewSecret(accessToken);
        return "200 OK";
    }

    @GetMapping("/renew")
    public String renew(@RegisteredOAuth2AuthorizedClient("azure") OAuth2AuthorizedClient client)
    {
        renewalService.checkAndRenewSecret(client);
        return "200 OK";
    }
}
