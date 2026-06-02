package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.auth.Endereco;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.CadastroCompletoRequest;
import com.jefiro.app247.domain.model.dto.CondominioResponse;
import com.jefiro.app247.domain.model.dto.EnderecoResponse;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.repository.CondominioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CondominioService {
    @Autowired
    CondominioRepository repository;

    @Autowired
    UserService userService;

    @Transactional
    public boolean newCondominio(CadastroCompletoRequest request) {
        try {


            User user = userService.cadastrar(request.user(), RoleUser.ADMIN);

            Endereco endereco = new Endereco(request.condominio().endereco());

            Condominio condominio = new Condominio(request.condominio(), endereco);


            Terminal terminal = new Terminal(request.terminal());

            condominio.addTerminal(terminal);
            condominio.addUser(user);

            repository.save(condominio);

            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Page<CondominioResponse> getCondominio(Pageable pageable) {
        return repository.findAll(pageable).map(c -> new CondominioResponse(c.getCondominioId(), c.getNome(), new EnderecoResponse(c.getEndereco())));
    }
}
