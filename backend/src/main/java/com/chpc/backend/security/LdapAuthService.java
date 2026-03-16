package com.chpc.backend.security;

import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.RoleRepository;
import com.chpc.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.directory.Attribute;
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

    private static final String REQUIRED_GROUP_DN = "CN=DEP02_1536_ACCESO_CIDIGITAL,OU=Hospital,OU=Usuarios,OU=CAS_PROVINCIAL,DC=chpcs,DC=local";

    @Transactional
    public Optional<User> authenticateAndSync(String username, String password) {
        try {
            AndFilter filter = new AndFilter();
            filter.and(new EqualsFilter("objectclass", "person"));
            filter.and(new EqualsFilter("sAMAccountName", username));

            log.info("=== LDAP: Intentando autenticar usuario '{}'", username);

            boolean authenticated = ldapTemplate.authenticate("", filter.encode(), password);

            if (!authenticated) {
                log.warn("=== LDAP: Fallo de autenticación para usuario '{}'", username);
                return Optional.empty();
            }

            List<Map<String, Object>> results = ldapTemplate.search(
                    "",
                    filter.encode(),
                    (Attributes attrs) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("cn", getAttr(attrs, "cn"));
                        map.put("mail", getAttr(attrs, "mail"));

                        // Debug: log all attribute IDs returned by AD
                        log.debug("=== LDAP: Atributos devueltos para el usuario:");
                        var attrIds = attrs.getIDs();
                        while (attrIds.hasMore()) {
                            String id = attrIds.next();
                            log.debug("=== LDAP:   atributo: {}", id);
                        }

                        List<String> memberOf = new ArrayList<>();
                        try {
                            Attribute memberOfAttr = attrs.get("memberOf");
                            log.info("=== LDAP: memberOf attr es null? {}", (memberOfAttr == null));
                            if (memberOfAttr != null) {
                                log.info("=== LDAP: memberOf tiene {} valores", memberOfAttr.size());
                                for (int i = 0; i < memberOfAttr.size(); i++) {
                                    String group = memberOfAttr.get(i).toString();
                                    log.info("=== LDAP:   grupo[{}]: {}", i, group);
                                    memberOf.add(group);
                                }
                            }
                        } catch (Exception e) {
                            log.error("=== LDAP: Error leyendo grupos: ", e);
                        }
                        map.put("memberOf", memberOf);
                        return map;
                    });

            if (results.isEmpty()) {
                log.warn("=== AD: Usuario {} autenticado pero no encontrado en búsqueda posterior", username);
                return Optional.empty();
            }

            Map<String, Object> attrs = results.get(0);

            @SuppressWarnings("unchecked")
            List<String> userGroups = (List<String>) attrs.getOrDefault("memberOf", new ArrayList<>());

            boolean hasRequiredGroup = userGroups.stream()
                    .anyMatch(g -> g.equalsIgnoreCase(REQUIRED_GROUP_DN));

            if (!hasRequiredGroup) {
                log.warn(
                        "=== LDAP: Usuario '{}' autenticado pero no pertenece al grupo requerido. Grupos del usuario: {}",
                        username, userGroups);
                return Optional.empty();
            }

            Set<String> safeGroups = userGroups.stream()
                    .map(this::extractCommonName)
                    .collect(Collectors.toSet());

            log.info("=== LDAP: Autenticación exitosa y con grupo válido para '{}'", username);

            Map<String, String> stringAttrs = new HashMap<>();
            stringAttrs.put("cn", attrs.getOrDefault("cn", "").toString());
            stringAttrs.put("mail", attrs.getOrDefault("mail", "").toString());

            User user = syncUser(username, stringAttrs, safeGroups, password);
            return Optional.of(user);

        } catch (Exception e) {
            log.error("=== AD: Error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractCommonName(String dn) {
        try {
            if (dn != null && dn.toUpperCase().startsWith("CN=")) {
                int start = 3;
                int end = dn.indexOf(',');
                if (end > start) {
                    return dn.substring(start, end);
                }
                return dn.substring(start);
            }
        } catch (Exception e) {
        }
        return dn;
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