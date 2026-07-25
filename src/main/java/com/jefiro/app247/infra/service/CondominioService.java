package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Condominio;
import com.jefiro.app247.domain.model.Empresa;
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
    EmpresaService empresaService;
    @Autowired
    UserService userService;

    @Transactional
    public boolean newCondominio(CadastroCompletoRequest request) {
        try {
            Empresa empresa = empresaService.newEmpresa(new Empresa(request.empresa()));

            User user = new User(request.user());
            user.setEmpresa(empresa);

            userService.cadastrar(user, RoleUser.ADMIN);

            Endereco endereco = new Endereco(request.condominio().endereco());
            endereco.setEmpresa(empresa);

            Condominio condominio = new Condominio(request.condominio(), endereco);
            condominio.setEmpresa(empresa);

            Terminal terminal = new Terminal(request.terminal());
            terminal.setEmpresa(empresa);

            condominio.addTerminal(terminal);
            condominio.addUser(user);

            repository.save(condominio);

            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Page<CondominioResponse> getCondominio(Pageable pageable) {
        return repository.findAll(pageable).map(c -> new CondominioResponse(c.getIdCondominio(), c.getNome(), new EnderecoResponse(c.getEndereco())));
    }
}
