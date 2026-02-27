package com.chpc.backend.security;

import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.RoleRepository;
import com.chpc.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.directory.Attributes;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LdapAuthService {

    private final LdapTemplate ldapTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Optional<User> authenticateAndSync(String username, String password) {
        try {

            List<Map<String, String>> results = ldapTemplate.search(
                    LdapQueryBuilder.query()
                            .base("ou=users")
                            .where("uid").is(username),
                    (Attributes attrs) -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("cn", getAttr(attrs, "cn"));
                        map.put("mail", getAttr(attrs, "mail"));
                        map.put("uid", getAttr(attrs, "uid"));
                        return map;
                    });

            if (results.isEmpty()) {
                return Optional.empty();
            }

            Map<String, String> attrs = results.get(0);
            String userDn = "uid=" + username + ",ou=users,dc=chpc,dc=es";

            boolean authenticated = verifyPassword(username, password);

            if (!authenticated)
                return Optional.empty();

            Set<String> groups = getGroups(userDn);

            User user = syncUser(username, attrs, groups, password);
            return Optional.of(user);

        } catch (Exception e) {
            log.error("=== LDAP: Error: ", e);
            return Optional.empty();
        }
    }

    private boolean verifyPassword(String username, String rawPassword) {
        try {
            List<String> storedPasswords = ldapTemplate.search(
                    LdapQueryBuilder.query()
                            .base("ou=users")
                            .where("uid").is(username),
                    (Attributes attrs) -> {
                        try {
                            var pwAttr = attrs.get("userPassword");
                            if (pwAttr == null)
                                return null;
                            Object val = pwAttr.get();
                            if (val instanceof byte[]) {
                                return new String((byte[]) val);
                            }
                            return val.toString();
                        } catch (Exception e) {
                            return null;
                        }
                    });

            if (storedPasswords.isEmpty() || storedPasswords.get(0) == null) {
                return false;
            }

            String stored = storedPasswords.get(0);

            if (stored.startsWith("{SHA}")) {
                return verifySha(rawPassword, stored);
            } else if (stored.startsWith("{SSHA}")) {
                return verifySsha(rawPassword, stored);
            } else {
                return stored.equals(rawPassword);
            }

        } catch (Exception e) {
            log.error("=== LDAP: Error verificando contraseña: {}", e.getMessage());
            return false;
        }
    }

    private boolean verifySha(String rawPassword, String storedHash) {
        try {
            String b64Hash = storedHash.substring(5);
            byte[] storedBytes = Base64.getDecoder().decode(b64Hash);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] rawBytes = md.digest(
                    rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            boolean match = java.util.Arrays.equals(rawBytes, storedBytes);
            return match;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifySsha(String rawPassword, String storedHash) {
        try {
            String b64Hash = storedHash.substring(6);
            byte[] storedBytes = Base64.getDecoder().decode(b64Hash);

            byte[] salt = java.util.Arrays.copyOfRange(
                    storedBytes, storedBytes.length - 4, storedBytes.length);
            byte[] hashPart = java.util.Arrays.copyOfRange(
                    storedBytes, 0, storedBytes.length - 4);

            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            md.update(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update(salt);
            byte[] computed = md.digest();

            boolean match = java.util.Arrays.equals(computed, hashPart);
            return match;

        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> getGroups(String userDn) {
        try {
            return new HashSet<>(ldapTemplate.search(
                    LdapQueryBuilder.query()
                            .base("ou=groups")
                            .where("member").is(userDn),
                    (Attributes attrs) -> getAttr(attrs, "cn")));
        } catch (Exception e) {
            return Set.of();
        }
    }

    @Transactional
    protected User syncUser(String username, Map<String, String> attrs,
            Set<String> groups, String password) {
        Set<Role> roles = groups.stream()
                .map(group -> {
                    try {
                        return roleRepository
                                .findByType(Role.RoleType.valueOf(group.toUpperCase()))
                                .orElse(null);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (roles.isEmpty()) {
            roleRepository.findByType(Role.RoleType.PROFESSIONAL)
                    .ifPresent(roles::add);
        }

        return userRepository.findByUsername(username).map(existing -> {
            existing.setFullName(attrs.getOrDefault("cn", existing.getFullName()));
            existing.setEmail(attrs.getOrDefault("mail", existing.getEmail()));
            existing.setRoles(roles);
            existing.setIsActive(true);
            return userRepository.save(existing);
        }).orElseGet(() -> {
            User newUser = User.builder()
                    .username(username)
                    .fullName(attrs.getOrDefault("cn", username))
                    .email(attrs.getOrDefault("mail", username + "@chpc.es"))
                    .passwordHash(passwordEncoder.encode(password))
                    .isActive(true)
                    .roles(roles)
                    .build();
            return userRepository.save(newUser);
        });
    }

    private String getAttr(Attributes attrs, String name) {
        try {
            var attr = attrs.get(name);
            return attr != null ? attr.get().toString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}