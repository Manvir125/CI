package com.chpc.backend.security;

import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.RoleRepository;
import com.chpc.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
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

    @Value("${ldap.domain}")
    private String ldapDomain;

    @Value("${ldap.required-group}")
    private String requiredGroup;

    @Transactional
    public Optional<User> authenticateAndSync(String username, String password) {
        try {
            log.info("=== AD: Intentando autenticar {}", username);

            // 1. Autenticar (Bind)
            // AD suele permitir bind con sAMAccountName si el LdapContextSource está bien
            // configurado
            // o con userPrincipalName (user@domain)
            boolean authenticated = ldapTemplate.authenticate(
                    "",
                    "(sAMAccountName=" + username + ")",
                    password);

            if (!authenticated) {
                log.warn("=== AD: Autenticación fallida para {}", username);
                return Optional.empty();
            }

            // 2. Buscar atributos y grupos
            List<Map<String, Object>> results = ldapTemplate.search(
                    "",
                    "(sAMAccountName=" + username + ")",
                    (Attributes attrs) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("cn", getAttr(attrs, "cn"));
                        map.put("mail", getAttr(attrs, "mail"));
                        map.put("uid", getAttr(attrs, "sAMAccountName"));
                        map.put("description", getAttr(attrs, "description"));

                        Set<String> memberOf = new HashSet<>();
                        var attr = attrs.get("memberOf");
                        if (attr != null) {
                            for (int i = 0; i < attr.size(); i++) {
                                memberOf.add(attr.get(i).toString());
                            }
                        }
                        map.put("groups", memberOf);
                        return map;
                    });

            if (results.isEmpty()) {
                log.warn("=== AD: Usuario {} autenticado pero no encontrado en búsqueda posterior", username);
                return Optional.empty();
            }

            Map<String, Object> userData = results.get(0);
            Set<String> groups = (Set<String>) userData.get("groups");

            // 3. Verificar grupo obligatorio
            boolean hasAccess = groups.stream().anyMatch(g -> g.contains(requiredGroup));
            if (!hasAccess) {
                log.warn("=== AD: Usuario {} no pertenece al grupo requerido {}", username, requiredGroup);
                throw new RuntimeException("Acceso denegado: No pertenece al grupo " + requiredGroup);
            }

            // 4. Sincronizar
            User user = syncUser(username, userData, groups, password);
            return Optional.of(user);

        } catch (Exception e) {
            log.error("=== AD: Error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    protected User syncUser(String username, Map<String, Object> attrs,
            Set<String> groups, String password) {

        // Mapeo básico de roles. AD memberOf son DNs completos.
        Set<Role> roles = groups.stream()
                .map(groupDn -> {
                    // Extraer CN del DN del grupo
                    String cn = groupDn.split(",")[0].replace("CN=", "").toUpperCase();
                    try {
                        return roleRepository.findByType(Role.RoleType.valueOf(cn))
                                .orElse(null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Si no tiene roles específicos, le asignamos PROFESSIONAL por defecto si está
        // en el grupo de acceso
        if (roles.isEmpty()) {
            roleRepository.findByType(Role.RoleType.PROFESSIONAL)
                    .ifPresent(roles::add);
        }

        String mail = (String) attrs.getOrDefault("mail", username + "@" + ldapDomain);
        String cn = (String) attrs.getOrDefault("cn", username);
        String serviceCode = (String) attrs.getOrDefault("description", "");

        return userRepository.findByUsername(username).map(existing -> {
            existing.setFullName(cn);
            existing.setEmail(mail);
            existing.setRoles(roles);
            existing.setIsActive(true);
            existing.setServiceCode(serviceCode);
            return userRepository.save(existing);
        }).orElseGet(() -> {
            User newUser = User.builder()
                    .username(username)
                    .fullName(cn)
                    .email(mail)
                    .passwordHash(passwordEncoder.encode(password))
                    .isActive(true)
                    .roles(roles)
                    .serviceCode(serviceCode)
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