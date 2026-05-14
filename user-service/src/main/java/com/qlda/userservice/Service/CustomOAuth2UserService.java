package com.qlda.userservice.Service;

import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Enum.UserProvider;
import com.qlda.userservice.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepo userRepo;


    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String email = (String) attrs.get("email");
        String name = (String) attrs.get("name");
        String googleId = (String) attrs.get("sub");
        String avatar = (String) attrs.get("picture");

        if (email == null || email.isBlank())
            throw new OAuth2AuthenticationException("Không lấy được email từ Google");

        User user = userRepo.findByEmail(email)
                .map(existing -> {
                    existing.setFullName(name);
                    existing.setAvatarUrl(avatar);
                    return userRepo.save(existing);
                })
                .orElseGet(() -> userRepo.save(User.builder()
                        .email(email).fullName(name).avatarUrl(avatar)
                        .provider(UserProvider.GOOGLE).providerId(googleId)
                        .emailVerified(true)
                        .build()));

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().name())),
                attrs,
                "sub"
        );


    }
}
