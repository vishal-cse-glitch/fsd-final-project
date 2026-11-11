package com.studymate;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;

    public UserPrincipal(User user) {
        id = user.getId();
        name = user.getName();
        email = user.getEmail();
        passwordHash = user.getPasswordHash();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}