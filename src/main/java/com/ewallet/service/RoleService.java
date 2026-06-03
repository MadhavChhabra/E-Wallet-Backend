package com.ewallet.service;

import com.ewallet.model.Role;
import com.ewallet.model.RoleType;
import com.ewallet.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j(topic = "RoleService")
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<Role> getReferenceByTypeIsIn(Set<RoleType> types) {
        return findAllCached().stream()
                .filter(role -> types.contains(role.getType()))
                .toList();
    }

    @Cacheable("roles")
    public List<Role> findAllCached() {
        return roleRepository.findAll();
    }

    public List<Role> findAll() {
        return findAllCached();
    }
}
