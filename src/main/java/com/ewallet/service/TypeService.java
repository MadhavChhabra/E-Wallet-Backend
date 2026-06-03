package com.ewallet.service;

import com.ewallet.model.Type;
import com.ewallet.repository.TypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j(topic = "TypeService")
@Service
@RequiredArgsConstructor
public class TypeService {

    private final TypeRepository typeRepository;

    @Cacheable(value = "transactionTypes", key = "#id")
    public Type getReferenceById(long id) {
        return typeRepository.getReferenceById(id);
    }
}
