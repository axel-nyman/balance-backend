package org.example.axelnyman.main.infrastructure.data.services;

import java.util.Optional;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.model.User;
import org.example.axelnyman.main.infrastructure.data.context.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DataService implements IDataService {

    private final UserRepository userRepository;

    public DataService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public boolean deleteUserById(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean userExistsByEmailIncludingDeleted(String email) {
        return userRepository.existsByEmailIncludingDeleted(email);
    }

    @Override
    public Optional<User> findActiveUserByEmail(String email) {
        return userRepository.findActiveByEmail(email);
    }
}
