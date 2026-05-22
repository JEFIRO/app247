package com.jefiro.app247.domain.model.dto.auth;

import com.jefiro.app247.infra.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        UserDetails user = repository.findByCpf(cpf);
        if (user == null) {
            throw new UsernameNotFoundException("Usuário não encontrado com o CPF: " + cpf);
        }
        return user;
    }
}