package org.example.axelnyman.main.domain.services;

import java.util.Optional;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.UserDtos.*;
import org.example.axelnyman.main.domain.extensions.UserExtensions;
import org.springframework.stereotype.Service;

@Service
public class DomainService implements IDomainService {

    private final IDataService dataService;

    public DomainService(IDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public Optional<UserResponse> getUserProfile(Long userId) {
        return dataService.getUserById(userId)
                .map(UserExtensions::toResponse);
    }
}
