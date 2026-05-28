package juby.invest.global.security.dto;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GoogleResponse implements OAuth2Response{

    private final Map<String, Object> attribute;

    @Override
    public String getProvider() {
        return "google";
    }

    @Override
    public String getProviderId() {
        return attribute.get("sub").toString();
    }

    @Override
    public String getEmail() {
        return attribute.get("email").toString();
    }

    @Override
    public String getName() {
        return attribute.get("name").toString();
    }

    @Override
    public String getProfileUrl() {
        return attribute.get("picture").toString();
    }

    @Override
    public String getBirthday() {
        return "null";
    }
}
